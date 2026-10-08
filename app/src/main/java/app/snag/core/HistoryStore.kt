package app.snag.core

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists finished jobs across restarts — a tiny JSON file, no Room needed.
 * Temporary results retain their URI and storage status across app restarts.
 */
object HistoryStore {

    private lateinit var file: File
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        file = File(context.filesDir, "history.json")
        JobStore.onDone = { job ->
            record(job)
        }
        // Seed completed jobs back into the store (newest first preserved).
        JobStore.seed(load())
    }

    fun record(job: Job) {
        val o = JSONObject().apply {
            put("id", job.id)
            put("url", job.url)
            put("title", job.title)
            put("thumbnail", job.thumbnail)
            put("durationSec", job.durationSec)
            put("site", job.site)
            put("uri", job.publishedUri?.toString())
            put("name", job.publishedName)
            put("bytes", job.publishedBytes)
            put("ephemeral", job.ephemeral)
            put("ts", System.currentTimeMillis())
        }
        synchronized(this) {
            val arr = read()
            // Replace an existing record for this id, append otherwise.
            var replaced = false
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i)?.optString("id") == job.id) {
                    arr.put(i, o); replaced = true; break
                }
            }
            // JSONArray.put(0, ...) replaces the first item; it does not prepend.
            val updated = if (replaced) arr else JSONArray().apply {
                put(o)
                for (i in 0 until arr.length()) put(arr.get(i))
            }
            file.writeText(updated.toString())
        }
    }

    fun drop(id: String) {
        synchronized(this) {
            val arr = read()
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("id") != id) out.put(o)
            }
            file.writeText(out.toString())
        }
    }

    fun load(): List<Job> {
        val arr = read()
        val jobs = mutableListOf<Job>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val uri = o.optString("uri").takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) } ?: continue
            if (o.optBoolean("ephemeral", false) && runCatching {
                appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false).not()) continue
            jobs += Job(
                id = o.optString("id").ifBlank { JobStore.nextId() },
                url = o.optString("url"),
                title = o.optString("title"),
                thumbnail = o.optString("thumbnail").ifBlank { null },
                durationSec = o.optLong("durationSec"),
                site = o.optString("site"),
                status = JobStatus.DONE,
                progress = 100f,
                publishedUri = uri,
                publishedName = o.optString("name").ifBlank { null },
                publishedBytes = o.optLong("bytes"),
                ephemeral = o.optBoolean("ephemeral", false),
            )
        }
        return jobs
    }

    fun dropByJobId(id: String) {
        // Job ids in the store get an "h<N>-" prefix when reloaded; strip it.
        var persistentId = id
        while (persistentId.matches(Regex("^h\\d+-.*"))) persistentId = persistentId.substringAfter('-')
        drop(persistentId)
    }

    private fun read(): JSONArray =
        runCatching { JSONArray(file.readText()) }.getOrElse { JSONArray() }
}
