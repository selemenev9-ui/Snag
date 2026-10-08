package app.snag.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Publishes a produced file into MediaStore Downloads and returns its URI. */
object SaveSink {

    data class Published(val uri: Uri, val displayName: String, val bytes: Long)

    fun publish(
        context: Context, file: File, displayName: String,
        subdir: String = "Snag", mimeOverride: String? = null,
    ): Published {
        val resolver = context.contentResolver
        val collection =
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOverride ?: mimeFor(displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subdir")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw SnagException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri, "w")!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Published(uri, displayName, file.length())
    }

    /** Publish into Ringtones/ — appears in the system ringtone picker. */
    fun publishRingtone(context: Context, file: File, displayName: String): Published {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media
            .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_RINGTONES}/Snag")
            put(MediaStore.Audio.AudioColumns.IS_RINGTONE, 1)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw SnagException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri, "w")!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Published(uri, displayName, file.length())
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "")) {
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    fun ensureExt(base: String, ext: String): String =
        if (base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"

    /** Display name for a downloaded video — safe chars, mp4 by default. */
    fun displayName(title: String, ext: String = "mp4"): String {
        val clean = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), " ").trim()
            .replace(Regex("\\s+"), " ").take(80).ifBlank { "snag" }
        return ensureExt("$clean-snag", ext)
    }
}
