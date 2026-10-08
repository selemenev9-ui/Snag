package app.snag.core

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class JobStatus { QUEUED, RESOLVING, READY, DOWNLOADING, COMPRESSING, DONE, FAILED, CANCELLED }
enum class CompletionAction { NONE, SHARE, EDIT }

data class Job(
    val id: String,
    val url: String,
    val title: String = "",
    val thumbnail: String? = null,
    val durationSec: Long = 0,
    val site: String = "",
    val status: JobStatus = JobStatus.RESOLVING,
    val progress: Float = -1f,
    val etaSec: Long = -1,
    val localFile: String? = null,
    val publishedUri: Uri? = null,
    val publishedName: String? = null,
    val publishedBytes: Long = 0,
    val error: String? = null,
    val compressOf: String? = null,
    /** Quick-share artifact: lives in cache via FileProvider, never MediaStore. */
    val ephemeral: Boolean = false,
    val savingCopy: Boolean = false,
    val downloadQualities: List<DownloadQuality> = emptyList(),
    val selectedQuality: DownloadQuality? = null,
    val audioAvailable: Boolean = false,
    val completionAction: CompletionAction = CompletionAction.NONE,
)

data class DownloadQuality(val selector: String, val width: Int, val height: Int) {
    val label: String get() = "${minOf(width, height)}p · ${width}×${height}"
}

/** In-process job registry — shared between UI and the download service. */
object JobStore {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs

    fun nextId(): String = "j${UUID.randomUUID()}"

    @Synchronized fun add(job: Job) {
        _jobs.value = listOf(job) + _jobs.value
    }

    /** Seed completed jobs (history) without touching id counters. */
    @Synchronized fun seed(list: List<Job>) {
        _jobs.value = _jobs.value + list
    }

    /** Called whenever a job transitions to DONE (set by the app). */
    var onDone: ((Job) -> Unit)? = null

    @Synchronized fun update(id: String, f: (Job) -> Job) {
        _jobs.value = _jobs.value.map {
            if (it.id == id) {
                val next = f(it)
                if (next.status == JobStatus.DONE && it.status != JobStatus.DONE) {
                    runCatching { onDone?.invoke(next) }
                }
                next
            } else {
                it
            }
        }
    }

    @Synchronized fun remove(id: String) {
        _jobs.value = _jobs.value.filterNot { it.id == id }
    }

    fun get(id: String): Job? = _jobs.value.firstOrNull { it.id == id }
}
