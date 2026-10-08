package app.snag.core

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Every prepared artifact has its own lifetime, independent of cards and dialogs. */
object ShareCache {
    const val RETENTION_MS = 24L * 60 * 60 * 1000

    fun store(context: Context, source: File, title: String): SaveSink.Published {
        check(source.isFile && source.length() > 0) { "Empty result" }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val name = SaveSink.displayName("${title.take(60)}-${UUID.randomUUID().toString().take(8)}", source.extension)
        val dest = File(dir, name)
        if (!source.renameTo(dest)) {
            try { source.copyTo(dest) } catch (e: Exception) { dest.delete(); throw e }
            source.delete()
        }
        dest.setLastModified(System.currentTimeMillis())
        return SaveSink.Published(
            FileProvider.getUriForFile(context, "${context.packageName}.sharefiles", dest),
            dest.name, dest.length(),
        )
    }

    fun prune(context: Context) {
        val now = System.currentTimeMillis()
        File(context.cacheDir, "share").listFiles()?.filter {
            it.isFile && now - it.lastModified() > RETENTION_MS
        }?.forEach { it.delete() }
    }
}
