package app.snag.core

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.ffmpeg.FFmpeg
import java.io.File

/**
 * yt-dlp engine wrapper. The bundled Python runtime is extracted once per
 * process (slow on first call) — `warm` should be called early, off the main
 * thread.
 */
object Downloader {
    @Volatile private var ready = false
    private val initLock = Any()

    fun warm(context: Context) = ensureInit(context)

    fun checkForUpdates(context: Context) {
        if (ready) DownloadEngine.scheduleUpdate(context.applicationContext)
    }

    fun ensureInit(context: Context) {
        if (ready) return
        synchronized(initLock) {
            if (ready) return
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            // Library 0.18.1 includes an old engine. Never rely on a silent,
            // network-dependent update for the application's primary path.
            DownloadEngine.installBundledIfOlder(context.applicationContext)
            ready = true
            DownloadEngine.scheduleUpdate(context.applicationContext)
        }
    }

    /** Fast metadata probe — title/thumb/duration without downloading. */
    fun probe(context: Context, url: String): VideoInfo {
        ensureInit(context)
        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        return DownloadEngine.use { YoutubeDL.getInstance().getInfo(req) }
    }

    data class SearchHit(
        val url: String,
        val title: String,
        val durationSec: Long,
        val uploader: String,
        val thumb: String?,
    )

    fun availableQualities(info: VideoInfo): List<DownloadQuality> {
        val formats = info.formats.orEmpty()
        return formats.filter { it.width > 0 && it.height > 0 && it.vcodec != "none" &&
            it.ext !in listOf("mhtml", "jpg", "png", "webp") && !it.formatId.isNullOrBlank() }
            .groupBy { it.width to it.height }
            .values.map { group ->
                val best = group.maxWith(compareBy({ it.tbr }, { it.fps }))
                val selector = if (best.acodec == "none" && formats.any { it.vcodec == "none" && it.acodec != "none" })
                    "${best.formatId}+ba" else best.formatId!!
                DownloadQuality(selector, best.width, best.height)
            }.sortedByDescending { it.width.toLong() * it.height }
    }

    fun audioAvailable(info: VideoInfo): Boolean = info.formats.orEmpty().any {
        !it.acodec.isNullOrBlank() && it.acodec != "none"
    }

    /**
     * YouTube text search ("ytsearch"). Flat playlist dump — no downloads,
     * one metadata round-trip per query.
     */
    fun search(context: Context, query: String, count: Int = 6): List<SearchHit> {
        ensureInit(context)
        val req = YoutubeDLRequest("ytsearch$count:$query").apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--socket-timeout", "25")
        }
        val res = DownloadEngine.use { YoutubeDL.getInstance().execute(req) }
        return parseFlat(res.out)
    }

    /**
     * Playlist → flat entry list (title/url/duration per video, no downloads).
     * Capped at [limit] to bound the metadata round-trip.
     */
    fun probePlaylist(
        context: Context, url: String, limit: Int = 50,
    ): List<SearchHit> {
        ensureInit(context)
        val req = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("-I", "1:$limit")
            addOption("--socket-timeout", "25")
        }
        return parseFlat(DownloadEngine.use { YoutubeDL.getInstance().execute(req).out })
    }

    private fun parseFlat(json: String): List<SearchHit> {
        val entries = org.json.JSONObject(json).optJSONArray("entries")
            ?: return emptyList()
        return (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val raw = e.optString("webpage_url").ifBlank { e.optString("url") }
            val url = when {
                raw.startsWith("http") -> raw
                raw.isNotBlank() -> "https://www.youtube.com/watch?v=$raw"
                else -> e.optString("id").takeIf { it.isNotBlank() }
                    ?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: return@mapNotNull null
            }
            SearchHit(
                url = url,
                title = e.optString("title").ifBlank { url },
                durationSec = e.optLong("duration"),
                uploader = e.optString("channel")
                    .ifBlank { e.optString("uploader") },
                thumb = e.optString("id").takeIf { it.isNotBlank() }
                    ?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
            )
        }
    }

    /**
     * Downloads [url] with the given yt-dlp format selector into the app's
     * private download dir. Returns the produced file. [onProgress] receives
     * (percent 0-100 or -1 when unknown, etaSec or -1, raw line).
     */
    fun download(
        context: Context,
        jobId: String,
        url: String,
        format: Format,
        selection: DownloadQuality? = null,
        onProgress: (Float, Long, String) -> Unit,
    ): File {
        ensureInit(context)
        val dir = File(context.filesDir, "dl").apply { mkdirs() }
        // Fallback chain: extractor-specific format tables (TikTok & friends)
        // often don't match bv*/ba selectors — degrade gracefully.
        val selectors: List<String?> = if (selection != null && format != Format.AUDIO) listOf(selection.selector) else when (format) {
            Format.AUDIO -> listOf(format.selector, "b", null)
            Format.AUTO -> listOf(format.selector, "b", null)
            Format.P1080 -> listOf(format.selector, "bv[width<=1080][height<=1920]+ba/b[width<=1080][height<=1920]", "b[height<=1080]")
            Format.P720 -> listOf(format.selector, "bv[width<=720][height<=1280]+ba/b[width<=720][height<=1280]", "b[height<=720]")
            Format.SMALL -> listOf(format.selector, "bv[width<=480][height<=854]+ba/b[width<=480][height<=854]", "b[height<=480]")
        }
        var lastError: Exception? = null
        for ((attempt, sel) in selectors.withIndex()) {
            if (attempt > 0) onProgress(-1f, -1, "retry:$attempt")
            val req = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--newline")
                addOption("--no-mtime")
                addOption("--socket-timeout", "25")
                addOption("--retries", "3")
                addOption("--no-windows-filenames")
                addOption("--restrict-filenames")
                sel?.let { addOption("-f", it) }
                // Point yt-dlp at the bundled ffmpeg executable (libffmpeg.so)
                // — merges and audio extraction need it explicitly.
                addOption(
                    "--ffmpeg-location",
                    File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
                        .absolutePath,
                )
                if (format == Format.AUDIO) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "2")
                } else {
                    addOption("--merge-output-format", "mp4")
                }
                addOption("-o", File(dir, "$jobId.%(ext)s").absolutePath)
            }
            try {
                DownloadEngine.use {
                    YoutubeDL.getInstance().execute(req, jobId) { p, eta, line ->
                        onProgress(p, eta, line)
                        null
                    }
                }
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                val formatIssue = msg.contains("format", ignoreCase = true) &&
                    msg.contains("available", ignoreCase = true)
                val youtubeDenied = SponsorBlock.videoIdOf(url) != null &&
                    msg.contains("HTTP Error 403", ignoreCase = true)
                if (youtubeDenied && attempt == 0) DownloadEngine.refresh(context.applicationContext, force = true)
                if ((!formatIssue && !youtubeDenied) || attempt == selectors.lastIndex) throw e
                // Only this job's failed streams are removed before selecting a fallback.
                dir.listFiles { f -> f.name.startsWith("$jobId.") }?.forEach { it.delete() }
                continue
            }
            break
        }
        val produced = dir.listFiles { f ->
            f.name.startsWith("$jobId.") && !f.name.endsWith(".part")
        }?.maxByOrNull { it.length() }
            ?: throw (lastError ?: SnagException("no output file"))
        return produced
    }

    fun cancel(jobId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }
    }

    enum class Format(val selector: String) {
        AUTO("bv*+ba/b"),
        P1080("bv*[height<=1080]+ba/b[height<=1080]/bv*[width<=1080][height<=1920]+ba/b[width<=1080][height<=1920]"),
        P720("bv*[height<=720]+ba/b[height<=720]/bv*[width<=720][height<=1280]+ba/b[width<=720][height<=1280]"),
        SMALL("bv*[height<=480]+ba/b[height<=480]/bv*[width<=480][height<=854]+ba/b[width<=480][height<=854]"),
        AUDIO("ba/b"),
    }
}

class SnagException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Last meaningful line of a python traceback / engine error. */
fun shortError(e: Throwable): String {
    val msg = e.message ?: return e.javaClass.simpleName
    val lines = msg.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    return lines.lastOrNull { it.startsWith("ERROR") || !it.startsWith("File") }
        ?: lines.lastOrNull() ?: msg.take(200)
}
