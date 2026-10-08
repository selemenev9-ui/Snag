package app.snag.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.snag.R
import app.snag.core.Compressor
import app.snag.core.Downloader
import app.snag.core.FfmpegRunner
import app.snag.core.HistoryStore
import app.snag.core.Job
import app.snag.core.JobStatus
import app.snag.core.JobStore
import app.snag.core.SaveSink
import app.snag.core.SnagService
import app.snag.core.LinkParse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.snag.core.shortError
import kotlinx.coroutines.launch
import app.snag.core.CompletionAction
import app.snag.core.PreparationRules
import app.snag.core.PreparationStore
import app.snag.core.PreparationRequest
import app.snag.core.PreparationState
import app.snag.core.StudioOptsData
import java.io.File
import java.util.UUID

typealias StudioOp = app.snag.core.StudioOp
typealias StudioResult = app.snag.core.PreparationState

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val jobs = JobStore.jobs

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl

    private val _squashTarget = MutableStateFlow<Job?>(null)
    val squashTarget: StateFlow<Job?> = _squashTarget

    private val _studioJob = MutableStateFlow<Job?>(null)
    val studioJob: StateFlow<Job?> = _studioJob

    private var exportTask: kotlinx.coroutines.Job? = null
    val studioResult: StateFlow<StudioResult> = PreparationStore.state

    private val _undoJob = MutableStateFlow<Job?>(null)
    val undoJob: StateFlow<Job?> = _undoJob

    private val _playerJob = MutableStateFlow<Job?>(null)
    val playerJob: StateFlow<Job?> = _playerJob

    fun openPlayer(job: Job) {
        _playerJob.value = job
    }

    /** Save the selected card, independently of the currently open studio. */
    fun saveResultCopy(job: Job) {
        val current = JobStore.get(job.id) ?: job
        if (!current.ephemeral || current.savingCopy || current.publishedUri == null) return
        if (PreparationStore.state.value.resultJobId == job.id && PreparationStore.state.value.running) return
        JobStore.update(job.id) { it.copy(savingCopy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val staged = File(getApplication<Application>().cacheDir, "save-${UUID.randomUUID()}.tmp")
            try {
                getApplication<Application>().contentResolver.openInputStream(current.publishedUri).use { input ->
                    checkNotNull(input) { "Temporary file is no longer available" }
                    staged.outputStream().use { input.copyTo(it) }
                }
                val name = current.publishedName ?: "snag.mp4"
                val published = SaveSink.publish(getApplication(), staged, name)
                JobStore.update(job.id) { it.copy(ephemeral = false, savingCopy = false,
                    publishedUri = published.uri, publishedName = published.displayName, publishedBytes = published.bytes) }
                JobStore.get(job.id)?.let { HistoryStore.record(it) }
                PreparationStore.update { if (it.resultJobId == job.id) it.copy(saved = true) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                JobStore.update(job.id) { it.copy(error = shortError(e)) }
            } finally {
                staged.delete()
                JobStore.update(job.id) { it.copy(savingCopy = false) }
            }
        }
    }

    fun closePlayer() {
        _playerJob.value = null
    }

    fun onClipboardText(text: String?) {
        _clipboardUrl.value = LinkParse.firstUrl(text)
    }

    fun clearClipboardHint() {
        _clipboardUrl.value = null
    }

    /** Route shared/typed text: every URL becomes its own job. */
    fun submitText(raw: String) {
        val urls = LinkParse.allUrls(raw)
        if (urls.isNotEmpty()) {
            urls.forEach { submitLink(it) }
        } else if (raw.isNotBlank()) {
            search(raw.trim())
        }
    }

    private val _searchResults =
        MutableStateFlow<List<Downloader.SearchHit>?>(null)
    val searchResults: StateFlow<List<Downloader.SearchHit>?> = _searchResults
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    /** Text query → YouTube search results dialog. */
    fun search(query: String) {
        if (_searching.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _searching.value = true
            try {
                _searchResults.value = Downloader.search(getApplication(), query)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _searching.value = false
            }
        }
    }

    fun dismissSearch() {
        _searchResults.value = null
    }

    private val _playlistOffer =
        MutableStateFlow<List<Downloader.SearchHit>?>(null)
    val playlistOffer: StateFlow<List<Downloader.SearchHit>?> = _playlistOffer
    private val _playlistLoading = MutableStateFlow(false)
    val playlistLoading: StateFlow<Boolean> = _playlistLoading

    fun dismissPlaylist() {
        _playlistOffer.value = null
    }

    /** Create READY jobs for playlist entries and queue all downloads. */
    fun downloadPlaylist(hits: List<Downloader.SearchHit>) {
        _playlistOffer.value = null
        hits.forEach { hit ->
            val id = JobStore.nextId()
            JobStore.add(
                Job(
                    id = id, url = hit.url, status = JobStatus.READY,
                    title = hit.title, thumbnail = hit.thumb,
                    durationSec = hit.durationSec, site = "YouTube",
                ),
            )
            startDownload(id, Downloader.Format.AUTO)
        }
    }

    /** Resolve metadata and stage a READY job. Errors land on the job card. */
    fun submitLink(raw: String) {
        val url = LinkParse.firstUrl(raw) ?: return
        if (LinkParse.looksLikePlaylist(url)) {
            viewModelScope.launch(Dispatchers.IO) {
                _playlistLoading.value = true
                try {
                    val hits = Downloader.probePlaylist(getApplication(), url)
                    if (hits.size > 1) {
                        _playlistOffer.value = hits
                        return@launch
                    }
                } catch (_: Exception) {
                    // Not a playlist / fetch failed — fall through to normal.
                } finally {
                    _playlistLoading.value = false
                }
                submitLinkNormal(url)
            }
            return
        }
        submitLinkNormal(url)
    }

    private fun submitLinkNormal(url: String) {
        if (JobStore.jobs.value.any {
                it.url == url && it.status in setOf(
                    JobStatus.QUEUED, JobStatus.RESOLVING, JobStatus.READY,
                    JobStatus.DOWNLOADING, JobStatus.COMPRESSING,
                )
            }
        ) {
            return // same link already being worked on
        }
        val id = JobStore.nextId()
        JobStore.add(Job(id = id, url = url, status = JobStatus.RESOLVING))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = Downloader.probe(getApplication(), url)
                JobStore.update(id) {
                    it.copy(
                        status = JobStatus.READY,
                        title = info.title ?: "",
                        thumbnail = info.thumbnail,
                        durationSec = info.duration.toLong(),
                        site = info.extractor ?: "",
                        downloadQualities = Downloader.availableQualities(info),
                        audioAvailable = Downloader.audioAvailable(info),
                    )
                }
            } catch (e: Exception) {
                JobStore.update(id) {
                    it.copy(status = JobStatus.FAILED, error = shortError(e))
                }
            }
        }
    }

    fun startDownload(
        jobId: String, format: Downloader.Format, ephemeral: Boolean = false,
        edit: Boolean = false,
    ) {
        val job = JobStore.get(jobId) ?: return
        if (job.status != JobStatus.READY) return
        JobStore.update(jobId) {
            it.copy(
                status = JobStatus.QUEUED,
                completionAction = when {
                    edit -> CompletionAction.EDIT
                    ephemeral -> CompletionAction.SHARE
                    else -> CompletionAction.NONE
                },
            )
        }
        try {
            SnagService.startDownload(getApplication(), jobId, job.url, format, ephemeral || edit)
        } catch (e: Exception) {
            JobStore.update(jobId) {
                it.copy(
                    status = JobStatus.FAILED,
                    error = shortError(e),
                    completionAction = CompletionAction.NONE,
                )
            }
        }
    }

    fun consumeCompletion(job: Job): CompletionAction {
        val current = JobStore.get(job.id) ?: return CompletionAction.NONE
        if (current.status != JobStatus.DONE) return CompletionAction.NONE
        JobStore.update(job.id) { it.copy(completionAction = CompletionAction.NONE) }
        return current.completionAction
    }

    fun cancelJob(jobId: String) {
        Downloader.cancel(jobId)
        JobStore.update(jobId) { it.copy(status = JobStatus.CANCELLED) }
    }

    fun removeJob(jobId: String) {
        val job = JobStore.get(jobId)
        // Hiding a card must not revoke a file already handed to another app.
        // Temporary artifacts are cleaned by ShareCache after their retention period.
        FfmpegRunner.cleanup(getApplication(), jobId)
        if (job?.status == JobStatus.DOWNLOADING ||
            job?.status == JobStatus.RESOLVING ||
            job?.status == JobStatus.QUEUED
        ) {
            JobStore.update(jobId) { it.copy(status = JobStatus.CANCELLED) }
        } else {
            JobStore.remove(jobId)
            _undoJob.value = job
        }
        HistoryStore.dropByJobId(jobId)
    }

    fun undoRemove() {
        val job = _undoJob.value ?: return
        _undoJob.value = null
        JobStore.add(job)
        if (job.status == JobStatus.DONE && !job.ephemeral) {
            viewModelScope.launch(Dispatchers.IO) { HistoryStore.record(job) }
        }
    }

    fun consumeUndo() {
        _undoJob.value = null
    }

    /** Import a local video (gallery pick or ACTION_SEND video stream). */
    fun addLocalVideo(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) { /* ACTION_SEND grants aren't persistable */ }
            var name = ""
            var bytes = 0L
            var durationMs = 0L
            runCatching {
                app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val nameIdx =
                        c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx =
                        c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (nameIdx >= 0) name = c.getString(nameIdx) ?: ""
                        if (sizeIdx >= 0) bytes = c.getLong(sizeIdx)
                    }
                }
            }
            runCatching {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(app, uri)
                durationMs = mmr.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull() ?: 0L
                mmr.release()
            }
            val imported = Job(
                id = JobStore.nextId(),
                url = uri.toString(),
                title = name.substringBeforeLast('.', name),
                durationSec = durationMs / 1000,
                site = app.getString(R.string.source_device),
                status = JobStatus.DONE,
                progress = 100f,
                publishedUri = uri,
                publishedName = name.ifBlank { "video" },
                publishedBytes = bytes,
            )
            JobStore.add(imported)
            if (app.contentResolver.getType(uri)?.startsWith("video/") != false) openStudio(imported)
        }
    }

    init {
        val cur = PreparationStore.state.value
        if (cur.running || cur.cancelling) {
            ensureStudioOpenedForActivePrep()
        }
    }

    fun ensureStudioOpenedForActivePrep() {
        val req = PreparationStore.currentRequest()
        val current = PreparationStore.state.value
        if (req != null && (current.running || current.cancelling || current.uri != null)) {
            val job = JobStore.get(req.sourceJobId) ?: Job(
                id = req.sourceJobId,
                url = req.sourceUrl,
                title = req.title,
                site = req.site,
                thumbnail = req.thumbnail,
                publishedUri = req.sourceUri,
                status = JobStatus.DONE,
            )
            _studioJob.value = job
        }
    }

    fun openStudio(job: Job) {
        val current = PreparationStore.state.value
        if (current.resultJobId == job.id && current.uri != null) {
            ensureStudioOpenedForActivePrep()
            if (!current.running && !current.cancelling) showReadyResult()
            return
        }
        if ((current.running || current.cancelling) && current.sourceJobId != null && current.sourceJobId != job.id) {
            // Another preparation task is currently running in background
            return
        }
        _studioJob.value = job
        if (!current.running && !current.cancelling && current.sourceJobId != job.id) {
            PreparationStore.reset()
        }
    }

    fun resetStudioResult() {
        PreparationStore.reset()
    }

    fun adjustStudioResult() {
        PreparationStore.update {
            it.copy(showReady = false)
        }
    }

    fun showReadyResult() {
        PreparationStore.update {
            if (it.uri != null && !it.running && !it.cancelling) it.copy(showReady = true) else it
        }
    }

    fun closeStudio() {
        if (!PreparationStore.state.value.running && !PreparationStore.state.value.cancelling) {
            _studioJob.value = null
            PreparationStore.reset()
        } else {
            // Dismiss studio screen while preparation continues in background
            _studioJob.value = null
        }
    }

    fun cancelStudioExport() {
        val current = PreparationStore.state.value
        if (current.phase == 2) return
        exportTask?.cancel()
        if (!current.running && !current.cancelling) return
        PreparationStore.update {
            it.copy(cancelling = true)
        }
        SnagService.cancelPreparation(getApplication(), current.workId.takeIf { it.isNotEmpty() })
    }

    /** Render once into private cache; saving to shared storage is a separate action. */
    fun studioExport(
        job: Job, op: StudioOp, startMs: Long, endMs: Long,
        opts: FfmpegRunner.StudioOpts = FfmpegRunner.StudioOpts(),
        targetBytes: Long? = null,
        framePositionMs: Long? = null,
    ) {
        val src = job.publishedUri ?: return
        val current = PreparationStore.state.value
        if (current.running || current.cancelling) return

        val effectiveStartMs = if (op == StudioOp.STICKER_STATIC && framePositionMs != null) {
            PreparationRules.resolveFramePosition(framePositionMs, startMs, endMs)
        } else {
            startMs
        }

        val invalid = when {
            effectiveStartMs < 0 -> true
            op in listOf(StudioOp.FRAME, StudioOp.STICKER_STATIC) -> false
            endMs - effectiveStartMs < 500 -> true
            targetBytes != null && (op != StudioOp.CLIP || targetBytes < 104857) -> true
            else -> false
        }

        if (invalid) {
            PreparationStore.update {
                it.copy(running = false, cancelling = false, error = getApplication<Application>().getString(R.string.prepare_invalid))
            }
            return
        }
        val workId = "prep-${UUID.randomUUID()}"
        val request = PreparationRequest(
            workId = workId,
            sourceJobId = job.id,
            sourceUri = src,
            title = job.title.ifBlank { "video" },
            site = job.site,
            thumbnail = job.thumbnail,
            sourceUrl = job.url,
            op = op,
            startMs = effectiveStartMs,
            endMs = endMs,
            opts = StudioOptsData.from(opts),
            targetBytes = targetBytes,
            framePositionMs = if (op == StudioOp.STICKER_STATIC) framePositionMs else null,
            rangeStartMs = startMs,
            rangeEndMs = endMs,
        )
        PreparationStore.setRequest(request)
        PreparationStore.update {
            it.copy(
                workId = workId,
                running = true,
                cancelling = false,
                pct = 0,
                op = if (it.uri != null) it.op else op,
                sourceJobId = job.id,
                error = null,
                showReady = false,
                phase = 0,
            )
        }
        try {
            SnagService.startPreparation(getApplication(), workId)
        } catch (e: Exception) {
            PreparationStore.update {
                it.copy(running = false, cancelling = false, error = shortError(e))
            }
        }
    }

    fun saveStudioResult() {
        val result = PreparationStore.state.value
        if (exportTask?.isActive == true || result.running || result.saved || result.uri == null || result.name == null || result.resultJobId?.let { JobStore.get(it)?.savingCopy } == true) return
        PreparationStore.update { it.copy(running = true, cancelling = false, phase = 2, pct = 0, error = null) }
        exportTask = viewModelScope.launch(Dispatchers.IO) {
            var savedSuccessfully = false
            var saveError: String? = null
            try {
                val file = File(getApplication<Application>().cacheDir, "share/${result.name}")
                check(file.isFile) { "Temporary file is no longer available" }
                saveDelayHook?.invoke()
                val published = if (result.op == StudioOp.RINGTONE) {
                    SaveSink.publishRingtone(getApplication(), file, result.name)
                } else SaveSink.publish(getApplication(), file, result.name)
                result.resultJobId?.let { id ->
                    JobStore.update(id) { it.copy(ephemeral = false, publishedUri = published.uri,
                        publishedName = published.displayName, publishedBytes = published.bytes) }
                    JobStore.get(id)?.let { HistoryStore.record(it) }
                }
                savedSuccessfully = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = shortError(e)
            } finally {
                PreparationStore.update {
                    it.copy(
                        running = false,
                        cancelling = false,
                        saved = if (savedSuccessfully) true else it.saved,
                        error = saveError ?: if (savedSuccessfully) null else it.error,
                    )
                }
            }
        }
    }

    fun openSquash(job: Job?) {
        _squashTarget.value = job
    }

    /** Squash the job's published artifact (read back from MediaStore). */
    fun squash(job: Job, targetBytes: Long?) {
        val src = job.publishedUri ?: run {
            JobStore.update(job.id) {
                it.copy(status = JobStatus.FAILED, error = "nothing to squash")
            }
            return
        }
        _squashTarget.value = null
        JobStore.update(job.id) { it.copy(status = JobStatus.COMPRESSING, progress = 0f) }
        viewModelScope.launch {
            try {
                val out = if (targetBytes != null) {
                    Compressor.compress(getApplication(), job.id, src, targetBytes) { a, p ->
                        JobStore.update(job.id) { it.copy(progress = (a * 30 + p / 3).toFloat()) }
                    }
                } else {
                    Compressor.shrink(getApplication(), job.id, src) { a, p ->
                        JobStore.update(job.id) { it.copy(progress = p.toFloat()) }
                    }
                }
                val published = SaveSink.publish(
                    getApplication(), out,
                    SaveSink.displayName("${job.title.ifBlank { "video" }}-squashed"),
                )
                out.delete()
                JobStore.update(job.id) {
                    it.copy(
                        status = JobStatus.DONE,
                        publishedUri = published.uri,
                        publishedName = published.displayName,
                        publishedBytes = published.bytes,
                        progress = 100f,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                // The original artifact is still valid — keep the card DONE
                // and show the squash error alongside it, don't eat the file.
                JobStore.update(job.id) {
                    it.copy(status = JobStatus.DONE, error = shortError(e))
                }
            }
        }
    }

    /** Re-submit the same link (used by the Retry chip on FAILED cards). */
    fun retry(job: Job) {
        if (job.url.startsWith("http")) {
            JobStore.remove(job.id)
            submitLink(job.url)
        }
    }

    companion object {
        @Volatile
        var saveDelayHook: (suspend () -> Unit)? = null
    }
}
