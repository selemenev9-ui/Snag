package app.snag.core

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import java.util.concurrent.atomic.AtomicBoolean

/** A known working engine is available even if GitHub cannot be reached. */
internal object DownloadEngine {
    const val BUNDLED_VERSION = "2026.08.19"
    private const val SHA256 = "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"
    private val engineLock = ReentrantReadWriteLock(true)
    private val updateLock = Any()
    private val updateScheduled = AtomicBoolean(false)

    fun <T> use(block: () -> T): T = engineLock.read(block)

    fun scheduleUpdate(context: Context) {
        val elapsed = System.currentTimeMillis() - context.getSharedPreferences("download_engine", 0).getLong("last_attempt", 0)
        if (elapsed in 0 until 24 * 60 * 60_000L || !updateScheduled.compareAndSet(false, true)) return
        Thread({
            try { refresh(context.applicationContext) } finally { updateScheduled.set(false) }
        }, "Snag-engine-update").start()
    }

    private fun engineFile(context: Context) = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")

    fun version(context: Context): String? = version(engineFile(context))

    /** yt-dlp is a zipapp with a shebang prefix, rejected by Android's ZipFile. */
    fun version(file: File): String? = runCatching {
        file.inputStream().buffered().use { input ->
            input.mark(4096)
            val header = ByteArray(4096)
            val count = input.read(header)
            val offset = (0 until count - 3).firstOrNull { i ->
                header[i] == 0x50.toByte() && header[i + 1] == 0x4b.toByte() &&
                    header[i + 2] == 3.toByte() && header[i + 3] == 4.toByte()
            } ?: return@use null
            input.reset()
            check(input.skip(offset.toLong()) == offset.toLong())
            ZipInputStream(input).use zipUse@ { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "yt_dlp/version.py") {
                        val text = zip.bufferedReader().readText()
                        return@zipUse Regex("__version__ = ['\"]([^'\"]+)['\"]").find(text)?.groupValues?.get(1)
                    }
                }
                null
            }
        }
    }.getOrNull()

    /** Network work stays outside the write lock; running downloads keep their engine. */
    fun refresh(context: Context, force: Boolean = false): Boolean = synchronized(updateLock) {
        val prefs = context.getSharedPreferences("download_engine", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = now - prefs.getLong("last_attempt", 0)
        if (since >= 0 && since < if (force) 60_000L else 24 * 60 * 60_000L) return@synchronized false
        prefs.edit().putLong("last_attempt", now).apply()
        var staging: File? = null
        try {
            val release = JSONObject(fetch("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest", 1_000_000).toString(Charsets.UTF_8))
            val tag = release.getString("tag_name")
            require(Regex("\\d{4}\\.\\d{2}\\.\\d{2}").matches(tag))
            val installed = version(context)
            if (installed != null && installed >= tag) {
                prefs.edit().putString("status", "current").putLong("last_success", now).apply()
                Log.i("SnagDownloader", "Download engine current: $installed")
                return@synchronized false
            }
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name") == "yt-dlp" }
            val url = asset.getString("browser_download_url")
            require(url == "https://github.com/yt-dlp/yt-dlp/releases/download/$tag/yt-dlp")
            val expected = asset.getString("digest").removePrefix("sha256:")
            require(Regex("[a-f0-9]{64}").matches(expected))
            val bytes = fetch(url, 10_000_000)
            check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == expected)
            val downloaded = File(engineFile(context).parentFile, "yt-dlp.update")
            staging = downloaded
            downloaded.writeBytes(bytes)
            check(version(downloaded) == tag) { "Unexpected engine version" }
            engineLock.write {
                Files.move(downloaded.toPath(), engineFile(context).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            prefs.edit().putString("status", "updated").putLong("last_success", now).apply()
            Log.i("SnagDownloader", "Download engine updated to $tag")
            true
        } catch (e: Exception) {
            prefs.edit().putString("status", "failed").apply()
            Log.w("SnagDownloader", "Engine update unavailable; keeping ${version(context)}", e)
            false
        } finally {
            staging?.delete()
        }
    }

    private fun fetch(url: String, limit: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "Snag-Android")
        try {
            check(connection.responseCode == 200) { "Engine update HTTP ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= limit) { "Engine update too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    fun installBundledIfOlder(context: Context) {
        val directory = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp").apply { mkdirs() }
        val engine = File(directory, "yt-dlp")
        val installed = version(engine)
        if (installed != null && installed >= BUNDLED_VERSION) return
        val staging = File(directory, "yt-dlp.new")
        try {
            context.assets.open("yt-dlp").use { input -> staging.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256")
            staging.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == SHA256) { "Invalid bundled download engine" }
            Files.move(staging.toPath(), engine.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            staging.delete()
        }
    }
}
