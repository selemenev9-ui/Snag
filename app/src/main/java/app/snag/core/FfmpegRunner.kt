package app.snag.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Runs the bundled ffmpeg executable (`libffmpeg.so`, a Termux-style ELF with a
 * main entry point) with the shared-library path pointed at the unpacked
 * youtubedl-android packages. Covers clip cut, GIF, Telegram webm sticker and
 * MP3 extraction.
 */
object FfmpegRunner {

    /** Modifiers applied to studio exports. */
    data class StudioOpts(
        val speed: Float = 1f,
        val mute: Boolean = false,
        val crop: Crop = Crop.ORIG,
        val rotate: Int = 0, // 0/90/180/270 degrees clockwise
        /** Sponsor segments to cut out of the exported range. */
        val skip: List<SponsorBlock.Segment> = emptyList(),
    )

    enum class Crop { ORIG, V916, S11 }

    private val TIME_RX = Pattern.compile("out_time_us=(\\d+)")

    private fun bin(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    private fun libPath(context: Context): String {
        val pkg = File(context.noBackupFilesDir, "youtubedl-android/packages")
        return listOf(
            File(pkg, "ffmpeg/usr/lib"),
            File(pkg, "python/usr/lib"),
        ).joinToString(":") { it.absolutePath }
    }

    /**
     * Executes ffmpeg with [args]. [totalUs] enables progress (0-100) parsed
     * from `-progress pipe:1`. Returns process exit code.
     */
    suspend fun run(
        context: Context,
        args: List<String>,
        totalUs: Long = 0,
        onProgress: (Int) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        Downloader.ensureInit(context)
        currentCoroutineContext().ensureActive()
        val full = mutableListOf(
            bin(context).absolutePath, "-hide_banner", "-nostdin", "-y",
            "-progress", "pipe:1", "-nostats",
        )
        full += args
        val pb = ProcessBuilder(full)
        pb.environment()["LD_LIBRARY_PATH"] = libPath(context) + ":" +
            (pb.environment()["LD_LIBRARY_PATH"] ?: "")
        pb.environment()["HOME"] = context.filesDir.absolutePath
        val err = StringBuilder()
        val proc = pb.start()
        // Gobble stderr on a plain thread — keeps run() simple and cancellable.
        val gobbler = Thread {
            // Process cancellation closes both pipes; that is expected, not an
            // uncaught exception on this thread (which would terminate the app).
            runCatching {
                proc.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        if (err.length < 4096) err.appendLine(line)
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        coroutineScope {
            val watchdog = launch(Dispatchers.IO) {
                try { awaitCancellation() } finally {
                    if (proc.isAlive) proc.destroyForcibly()
                }
            }
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    if (totalUs > 0) {
                        val m = TIME_RX.matcher(line)
                        if (m.find()) onProgress((m.group(1)!!.toLong() * 100 / totalUs).toInt().coerceIn(0, 99))
                    }
                }
                proc.waitFor()
                gobbler.join(2000)
            } finally {
                watchdog.cancel()
                if (proc.isAlive) proc.destroyForcibly()
                currentCoroutineContext().ensureActive()
            }
        }
        if (proc.exitValue() != 0) {
            val lastErrors = err.lines().filter { it.isNotBlank() }.takeLast(5).joinToString(" | ")
            throw SnagException("ffmpeg failed (${proc.exitValue()}): $lastErrors")
        }
        proc.exitValue()
    }

    /** Copies a content:// source into a local staging file ffmpeg can open. */
    suspend fun stage(context: Context, src: Uri, jobId: String): File {
        val dir = File(context.filesDir, "studio").apply { mkdirs() }
        val ext = src.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 } ?: "bin"
        val out = File(dir, "$jobId-src.$ext").apply { if (exists()) delete() }
        context.contentResolver.openInputStream(src)?.use { input ->
            out.outputStream().use { target ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    target.write(buffer, 0, count)
                }
            }
        } ?: throw SnagException("cannot read source")
        return out
    }

    fun cleanup(context: Context, jobId: String) {
        File(context.filesDir, "studio").listFiles { f -> f.name.startsWith("$jobId-") || f.name == "$jobId.bin" }
            ?.forEach { it.delete() }
    }

    /** ffmpeg -vf chain for crop/rotate/speed, plus [extra] filters. */
    private fun videoFilters(o: StudioOpts, extra: List<String> = emptyList()): String {
        val f = mutableListOf<String>()
        when (o.crop) {
            Crop.V916 -> f += "crop='min(iw,ih*9/16)':'min(ih,iw*16/9)'"
            Crop.S11 -> f += "crop='min(iw,ih)':'min(ih,iw)'"
            Crop.ORIG -> Unit
        }
        when (o.rotate % 360) {
            90 -> f += "transpose=1"
            180 -> f += "transpose=1,transpose=1"
            270 -> f += "transpose=2"
        }
        if (o.speed != 1f) f += "setpts=PTS/${o.speed}"
        f += extra
        return f.joinToString(",")
    }

    private fun speedArgs(o: StudioOpts, hasAudio: Boolean): List<String> = when {
        o.mute || !hasAudio -> listOf("-an")
        o.speed != 1f -> listOf("-af", "atempo=${o.speed}", "-c:a", "aac", "-b:a", "128k")
        else -> listOf("-c:a", "aac", "-b:a", "128k")
    }

    /**
     * Precise cut + social-ready re-encode (H.264/AAC, faststart).
     * Re-encoding keeps cut points exact regardless of keyframe alignment.
     */
    suspend fun cutClip(
        context: Context, src: File, startMs: Long, endMs: Long,
        opts: StudioOpts, onProgress: (Int) -> Unit,
    ): File {
        val out = File(src.parentFile, "${src.nameWithoutExtension}-clip.mp4")
            .apply { if (exists()) delete() }
        // Input -ss resets pts to ~0, so sponsor ranges shift by -startMs.
        val dur = endMs - startMs
        val shifted = opts.skip.map {
            SponsorBlock.Segment(it.startMs - startMs, it.endMs - startMs, "")
        }
        val keep = SponsorBlock.keepExpr(0, dur, shifted)
        val vf = listOfNotNull(
            keep?.let { "select='$it',setpts=N/FRAME_RATE/TB" },
            videoFilters(opts).takeIf { it.isNotEmpty() },
        ).joinToString(",")
        val args = mutableListOf(
            "-ss", ms(startMs), "-to", ms(endMs), "-i", src.absolutePath,
        )
        if (vf.isNotEmpty()) args += listOf("-vf", vf)
        args += listOf(
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
            "-pix_fmt", "yuv420p",
        )
        args += when {
            opts.mute -> listOf("-an")
            keep != null -> listOf(
                "-af", "aselect='$keep',asetpts=N/SR/TB" +
                    if (opts.speed != 1f) ",atempo=${opts.speed}" else "",
                "-c:a", "aac", "-b:a", "128k",
            )
            else -> speedArgs(opts, hasAudio = true)
        }
        args += listOf("-movflags", "+faststart", out.absolutePath)
        run(
            context, args,
            totalUs = ((endMs - startMs) / opts.speed * 1000).toLong(),
            onProgress = onProgress,
        )
        return out
    }

    /** Single frame → JPEG at the selected moment. */
    suspend fun copyFullClip(context: Context, src: File): File {
        val out = File(src.parentFile, "${src.nameWithoutExtension}-copy.mp4")
        run(context, listOf("-i", src.absolutePath, "-map", "0:v:0", "-map", "0:a:0?",
            "-c", "copy", "-movflags", "+faststart", out.absolutePath))
        return out
    }

    suspend fun frameJpg(
        context: Context, src: File, atMs: Long,
    ): File {
        val out = File(src.parentFile, "${src.nameWithoutExtension}-frame.jpg")
            .apply { if (exists()) delete() }
        run(
            context, listOf(
                "-ss", ms(atMs), "-i", src.absolutePath,
                "-frames:v", "1", "-q:v", "2", out.absolutePath,
            ),
        )
        return out
    }

    /** Two-pass palette GIF — much better colors than single-pass dither. */
    suspend fun makeGif(
        context: Context, src: File, startMs: Long, endMs: Long,
        opts: StudioOpts, onProgress: (Int) -> Unit,
    ): File {
        val palette = File(src.parentFile, "${src.nameWithoutExtension}-pal.png")
        val shifted = opts.skip.map {
            SponsorBlock.Segment(it.startMs - startMs, it.endMs - startMs, "")
        }
        val keep = SponsorBlock.keepExpr(0, endMs - startMs, shifted)
        val vf = listOfNotNull(
            keep?.let { "select='$it',setpts=N/FRAME_RATE/TB" },
            videoFilters(opts, listOf("fps=12,scale=480:-1:flags=lanczos")),
        ).joinToString(",")
        run(
            context, listOf(
                "-ss", ms(startMs), "-to", ms(endMs), "-i", src.absolutePath,
                "-vf", "$vf,palettegen=max_colors=128", palette.absolutePath,
            ),
            totalUs = (endMs - startMs) * 1000,
        ) { onProgress(it / 2) }
        val out = File(src.parentFile, "${src.nameWithoutExtension}.gif")
            .apply { if (exists()) delete() }
        run(
            context, listOf(
                "-ss", ms(startMs), "-to", ms(endMs), "-i", src.absolutePath,
                "-i", palette.absolutePath,
                "-lavfi", "$vf [x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4",
                out.absolutePath,
            ),
            totalUs = (endMs - startMs) * 1000,
        ) { onProgress(50 + it / 2) }
        palette.delete()
        return out
    }

    /**
     * Telegram video sticker: webm VP9, square ≤512px, muted, ≤3 s.
     * Retries with a harsher CRF until it fits the 256 KiB sticker budget.
     */
    suspend fun makeSticker(
        context: Context, src: File, startMs: Long, endMs: Long, opts: StudioOpts,
        onProgress: (Int) -> Unit,
    ): File {
        val out = File(src.parentFile, "${src.nameWithoutExtension}-sticker.webm")
        val durationMs = ((endMs - startMs) / opts.speed).toLong().coerceAtMost(3000)
        val shifted = opts.skip.map {
            SponsorBlock.Segment(it.startMs - startMs, it.endMs - startMs, "")
        }
        val dur = endMs - startMs
        val keep = SponsorBlock.keepExpr(0, dur, shifted)
        val vf = listOfNotNull(
            keep?.let { "select='$it',setpts=N/FRAME_RATE/TB" },
            videoFilters(opts, listOf("scale=w=512:h=512:force_original_aspect_ratio=decrease:force_divisible_by=2,fps=30")),
        ).joinToString(",")
        var crf = 36
        repeat(3) { attempt ->
            if (out.exists()) out.delete()
            run(
                context, listOf(
                    "-ss", ms(startMs), "-to", ms(endMs), "-i", src.absolutePath, "-t", ms(durationMs),
                    "-vf", vf,
                    "-c:v", "libvpx-vp9", "-an", "-crf", crf.toString(), "-b:v", "0",
                    "-row-mt", "1", "-deadline", "good", "-cpu-used", "4",
                    out.absolutePath,
                ),
                totalUs = durationMs * 1000,
            ) { onProgress((attempt * 33 + it / 3).coerceAtMost(99)) }
            if (out.length() in 1..256L * 1024) return out
            crf += 6
        }
        out.delete()
        throw SnagException("Sticker cannot fit the size limit; choose a shorter moment")
    }

    /**
     * Telegram static sticker: 512px WebP, one side exactly 512px, other side <= 512px.
     * Extracts frame at [atMs] using MediaMetadataRetriever (with FFmpeg fallback)
     * and compresses to lossless WebP.
     */
    suspend fun makeStaticSticker(
        context: Context, src: File, atMs: Long,
    ): File = withContext(Dispatchers.IO) {
        val out = File(src.parentFile, "${src.nameWithoutExtension}-$atMs-sticker.webp")
            .apply { if (exists()) delete() }
        var bmp: Bitmap? = null
        val mmr = MediaMetadataRetriever()
        var fis: java.io.FileInputStream? = null
        try {
            fis = java.io.FileInputStream(src)
            mmr.setDataSource(fis.fd)
            val timeUs = atMs * 1000L
            bmp = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            try {
                mmr.setDataSource(src.absolutePath)
                val timeUs = atMs * 1000L
                bmp = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
            }
        } finally {
            runCatching { fis?.close() }
            runCatching { mmr.release() }
        }

        if (bmp == null) {
            val fallback = runCatching { frameJpg(context, src, atMs) }.getOrNull()
            if (fallback != null && fallback.exists()) {
                try {
                    bmp = android.graphics.BitmapFactory.decodeFile(fallback.absolutePath)
                } finally {
                    fallback.delete()
                }
            }
        }

        if (bmp != null) {
            val maxSide = maxOf(bmp.width, bmp.height).toFloat()
            val scale = if (maxSide > 0) 512f / maxSide else 1f
            val targetW = kotlin.math.round((bmp.width * scale)).toInt().coerceIn(1, 512)
            val targetH = kotlin.math.round((bmp.height * scale)).toInt().coerceIn(1, 512)
            val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            java.io.FileOutputStream(out).use { stream ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 100, stream)
                }
            }
            return@withContext out
        }
        throw SnagException("Could not extract frame for static sticker")
    }

    /**
     * Aggressive size-constrained squash — hardware encoders have a bitrate
     * floor, so tiny targets go through libx264 which honors sub-100 kbps.
     * Steps down a resolution ladder with corrected bitrate until it fits.
     */
    suspend fun squash(
        context: Context, src: File, targetBytes: Long, durSec: Double,
        hasAudio: Boolean, plan: CompressionPlan, onProgress: (Int) -> Unit,
    ): File {
        var vBps = plan.videoBps
        val out = File(src.parentFile, "${src.nameWithoutExtension}-squash.mp4")
        val passLog = File(src.parentFile, "${src.nameWithoutExtension}-pass")
        try {
        repeat(2) { attempt ->
            if (out.exists()) out.delete()
            val args = mutableListOf(
                "-i", src.absolutePath,
                "-map", "0:v:0",
                "-vf", "scale=${plan.width}:${plan.height}:flags=lanczos,setsar=1",
                "-c:v", "libx264", "-preset", "fast",
                "-b:v", "$vBps", "-pix_fmt", "yuv420p",
                "-passlogfile", passLog.absolutePath,
            )
            run(context, args + listOf("-pass", "1", "-an", "-f", "null", "/dev/null"),
                totalUs = (durSec * 1_000_000).toLong()) { onProgress(it / 2) }
            args += listOf("-pass", "2")
            if (hasAudio) {
                args += listOf("-map", "0:a:0", "-c:a", "aac", "-b:a", "${plan.audioBps}")
                if (plan.audioBps < 64000) args += listOf("-ac", "1")
            } else {
                args += "-an"
            }
            args += listOf("-movflags", "+faststart", out.absolutePath)
            run(
                context, args, totalUs = (durSec * 1_000_000).toLong(),
            ) { onProgress(50 + it / 2) }
            if (out.exists() && out.length() in 1..targetBytes) return out
            val actual = if (out.exists()) out.length() else targetBytes * 2
            vBps = (vBps / (actual.toDouble() / targetBytes) * 0.9)
                .toLong().coerceAtLeast(8000)
        }
        out.delete()
        throw SnagException("could not fit ${targetBytes}B")
        } finally {
            src.parentFile?.listFiles { f -> f.name.startsWith(passLog.name + "-") }?.forEach { it.delete() }
        }
    }

    /** MP3 for the selected range (or the whole file when range covers it). */
    suspend fun extractAudio(
        context: Context, src: File, startMs: Long, endMs: Long,
        speed: Float = 1f, onProgress: (Int) -> Unit,
    ): File {
        val out = File(src.parentFile, "${src.nameWithoutExtension}.mp3")
            .apply { if (exists()) delete() }
        val args = mutableListOf(
            "-ss", ms(startMs), "-to", ms(endMs), "-i", src.absolutePath,
        )
        if (speed != 1f) args += listOf("-af", "atempo=$speed")
        args += listOf("-vn", "-c:a", "libmp3lame", "-q:a", "3", out.absolutePath)
        run(
            context, args,
            totalUs = ((endMs - startMs) / speed * 1000).toLong(),
            onProgress = onProgress,
        )
        return out
    }

    /** ~8 scaled frames spread over the range for the timeline filmstrip. */
    suspend fun filmstrip(context: Context, src: Uri, durUs: Long, count: Int = 8):
        List<ImageBitmap> = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, src)
            val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 16
            val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 9
            val thumbH = 96
            val thumbW = (thumbH.toFloat() * vw / vh).toInt().coerceIn(24, 384)
            (0 until count).mapNotNull { i ->
                val t = if (count == 1) 0L else durUs * i / (count - 1)
                runCatching {
                    r.getScaledFrameAtTime(
                        t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, thumbW, thumbH,
                    )?.copy(Bitmap.Config.ARGB_8888, false)?.asImageBitmap()
                }.getOrNull()
            }
        } finally {
            r.release()
        }
    }

    private fun ms(v: Long): String =
        String.format(java.util.Locale.US, "%.3f", v / 1000.0)
}
