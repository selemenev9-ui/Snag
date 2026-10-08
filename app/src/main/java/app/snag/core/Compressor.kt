package app.snag.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.AudioEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Re-encodes a downloaded/local video to fit a byte budget using Media3
 * Transformer. Bounded retries with a corrected factor + resolution ladder —
 * nothing oversized is produced (same contract as VantaFetch).
 */
@OptIn(UnstableApi::class)
object Compressor {

    data class Probe(val durationUs: Long, val width: Int, val height: Int, val srcBytes: Long, val hasAudio: Boolean)

    fun probe(context: Context, src: Uri): Probe {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, src)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            var bytes = 0L
            runCatching {
                context.contentResolver.openFileDescriptor(src, "r")?.use { bytes = it.statSize }
            }
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            return if (rotation % 180 != 0) Probe(durMs * 1000, h, w, bytes, hasAudio)
                else Probe(durMs * 1000, w, h, bytes, hasAudio)
        } finally {
            r.release()
        }
    }

    private fun ladder(maxDim: Int): List<Int> = when {
        maxDim > 1080 -> listOf(1080, 720, 540, 360, 288)
        maxDim > 720 -> listOf(720, 540, 360, 288)
        else -> listOf(540, 360, 288, 180)
    }

    /** Compress [src] so output ≤ [targetBytes]; returns the produced file. */
    suspend fun compress(
        context: Context,
        jobId: String,
        src: Uri,
        targetBytes: Long,
        onAttempt: (attempt: Int, percent: Int) -> Unit,
    ): File {
        val p = probe(context, src)
        if (p.srcBytes in 1..targetBytes) return copyOut(context, src, jobId, "fits")
        val durSec = max(1.0, p.durationUs / 1_000_000.0)
        val plan = CompressionPlan.calculate(p.width, p.height, durSec, p.srcBytes, targetBytes, p.hasAudio)
        require(plan.feasible) { context.getString(app.snag.R.string.compression_impossible) }
        val heights = listOf(plan.height)
        var heightIdx = 0
        var audioBps = plan.audioBps
        var videoBps = plan.videoBps

        // Tight budgets use two-pass software encoding for more predictable
        // size and detail. Larger budgets can use the platform encoder.
        var hwFailed = false
        if (videoBps >= 1_500_000) {
            // Two passes per height before stepping down the ladder.
            val maxAttempts = heights.size * 2
            var attempt = 0
            var lastFile: File? = null
            while (attempt < maxAttempts) {
                attempt++
                val heightCap = heights.getOrNull(heightIdx)
                val out = File(context.filesDir, "squash/$jobId-a$attempt.mp4").apply {
                    parentFile?.mkdirs()
                    if (exists()) delete()
                }
                try {
                    encode(context, src, out, videoBps.toInt(), heightCap, p.hasAudio, audioBps) { pc ->
                        onAttempt(attempt, ((attempt - 1) * 100 + pc) / maxAttempts)
                    }
                } catch (e: CancellationException) {
                    out.delete(); throw e
                } catch (e: Exception) {
                    out.delete(); break
                }
                if (out.exists() && out.length() in 1..targetBytes) return out
                val actual = if (out.exists()) out.length() else targetBytes * 2
                val factor = actual.toDouble() / targetBytes.toDouble()
                out.delete()
                videoBps = (videoBps / factor * 0.9).toLong().coerceAtLeast(64_000)
                if (videoBps < 250_000) audioBps = 64_000
                if (attempt % 2 == 0) heightIdx++
                lastFile = out
            }
            lastFile?.delete()
            hwFailed = true
        }

        // Aggressive target (or hardware path exhausted): libx264 squash.
        val staged = FfmpegRunner.stage(context, src, jobId)
        try {
            return FfmpegRunner.squash(
                context, staged, targetBytes, durSec, p.hasAudio, plan,
            ) { onAttempt(1, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!hwFailed) throw e
            throw SnagException("could not fit ${targetBytes}B")
        } finally {
            staged.delete()
        }
    }

    /** "Visually same" re-encode: same dims, ~55% of source bitrate. */
    suspend fun shrink(
        context: Context,
        jobId: String,
        src: Uri,
        onAttempt: (attempt: Int, percent: Int) -> Unit,
    ): File {
        val p = probe(context, src)
        val srcBps = if (p.durationUs > 0) (p.srcBytes * 8.0 / (p.durationUs / 1e6)) else 6e6
        val videoBps = (srcBps * 0.5).toLong().coerceIn(200_000, 10_000_000).toInt()
        val out = File(context.filesDir, "squash/$jobId-lite.mp4").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        encode(context, src, out, videoBps, null, p.hasAudio, 128_000) { onAttempt(1, it) }
        // Never return a file bigger than the source — fall back to a copy.
        if (p.srcBytes > 0 && out.length() >= p.srcBytes) {
            out.delete()
            return copyOut(context, src, jobId, "lite")
        }
        return out
    }

    /** Byte-copy [src] into a staging file (used when re-encoding can't win). */
    private suspend fun copyOut(context: Context, src: Uri, jobId: String, tag: String): File =
        withContext(Dispatchers.IO) {
            val out = File(context.filesDir, "squash/$jobId-$tag.mp4").apply {
                parentFile?.mkdirs()
                if (exists()) delete()
            }
            context.contentResolver.openInputStream(src)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: throw SnagException("cannot read source")
            out
        }

    private suspend fun encode(
        context: Context,
        src: Uri,
        out: File,
        videoBps: Int,
        heightCap: Int?,
        hasAudio: Boolean,
        audioBps: Int,
        onProgress: (Int) -> Unit,
    ): Unit = withContext(Dispatchers.Main) {
        val holder = ProgressHolder()
        var transformer: Transformer? = null
        val poller = launch {
            while (isActive) {
                if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                delay(250)
            }
        }
        var cancelledByUs = false
        try {
            suspendCancellableCoroutine { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                if (cancelledByUs) {
                                    kotlinx.coroutines.CancellationException("squash cancelled")
                                } else {
                                    exportException
                                },
                            )
                        }
                    }
                }

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(videoBps).build(),
                    )
                    .apply {
                        if (hasAudio) {
                            setRequestedAudioEncoderSettings(
                                AudioEncoderSettings.Builder().setBitrate(audioBps).build(),
                            )
                        }
                    }
                    .build()

                val item = EditedMediaItem.Builder(MediaItem.fromUri(src))
                    .apply {
                        heightCap?.let { h ->
                            setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(h))))
                        }
                    }
                    .build()
                val sequence = if (hasAudio) {
                    EditedMediaItemSequence.withAudioAndVideoFrom(
                        com.google.common.collect.ImmutableList.of(item),
                    )
                } else {
                    EditedMediaItemSequence.withVideoFrom(
                        com.google.common.collect.ImmutableList.of(item),
                    )
                }
                val composition = Composition.Builder(sequence).build()

                val t = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()
                transformer = t

                cont.invokeOnCancellation {
                    cancelledByUs = true
                    android.os.Handler(android.os.Looper.getMainLooper()).post { t.cancel() }
                }
                t.start(composition, out.absolutePath)
            }
        } finally {
            poller.cancel()
        }
    }
}
