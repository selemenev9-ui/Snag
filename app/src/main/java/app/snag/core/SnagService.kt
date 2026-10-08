package app.snag.core

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.snag.MainActivity
import app.snag.R
import app.snag.SnagServiceChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Foreground service that runs yt-dlp downloads and studio preparation jobs.
 * Registered as `dataSync`; `startForeground` uses manifest-declared type.
 */
class SnagService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prepMutex = Mutex()
    @Volatile private var currentPrepWorkId: String? = null
    private var prepJob: CoroutineJob? = null
    private var foregroundDownloadStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_JOB_ID)?.let { id ->
                    JobStore.update(id) { it.copy(status = JobStatus.CANCELLED) }
                    Downloader.cancel(id)
                }
                return START_NOT_STICKY
            }
            ACTION_DOWNLOAD -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return START_NOT_STICKY
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fmt = intent.getStringExtra(EXTRA_FORMAT)
                    ?.let { runCatching { Downloader.Format.valueOf(it) }.getOrNull() }
                    ?: Downloader.Format.AUTO
                val ephemeral = intent.getBooleanExtra(EXTRA_EPHEMERAL, false)
                startForegroundDownload(runningNotification(jobId, 0))
                scope.launch { runDownload(jobId, url, fmt, ephemeral) }
                return START_STICKY
            }
            ACTION_PREPARE -> {
                val workId = intent.getStringExtra(EXTRA_WORK_ID) ?: return START_NOT_STICKY
                if (currentPrepWorkId == workId && prepJob?.isActive == true) {
                    val p = PreparationStore.state.value.pct
                    startForegroundPrep(prepNotification(workId, p, getString(R.string.prepare_rendering, p)))
                    return START_STICKY
                }
                startForegroundPrep(prepNotification(workId, 0, getString(R.string.prepare_rendering, 0)))
                scope.launch {
                    prepMutex.withLock {
                        if (currentPrepWorkId == workId && prepJob?.isActive == true) return@withLock
                        val oldJob = prepJob
                        if (oldJob != null && oldJob.isActive) {
                            oldJob.cancelAndJoin()
                        }
                        currentPrepWorkId = workId
                        prepJob = launch { runPreparation(workId) }
                    }
                }
                return START_STICKY
            }
            ACTION_CANCEL_PREPARE -> {
                val workId = intent.getStringExtra(EXTRA_WORK_ID)
                scope.launch {
                    try {
                        prepMutex.withLock {
                            if (workId == null || currentPrepWorkId == workId || currentPrepWorkId == null) {
                                val active = prepJob
                                if (active != null && active.isActive && (workId == null || currentPrepWorkId == workId)) {
                                    active.cancelAndJoin()
                                }
                                if (workId != null) {
                                    withContext(NonCancellable + Dispatchers.IO) {
                                        FfmpegRunner.cleanup(this@SnagService, workId)
                                        File(filesDir, "squash").listFiles()
                                            ?.filter { it.name.startsWith("$workId-") }?.forEach { it.delete() }
                                    }
                                }
                                PreparationStore.update {
                                    if ((workId == null || it.workId == workId) && it.phase != 2 && (it.running || it.cancelling)) {
                                        it.copy(running = false, cancelling = false, error = getString(R.string.state_cancelled), showReady = false)
                                    } else it
                                }
                                if (workId == null || currentPrepWorkId == workId) {
                                    currentPrepWorkId = null
                                    prepJob = null
                                }
                                checkStopService()
                            }
                        }
                    } finally {
                        cancelProcessedHook?.invoke(workId)
                    }
                }
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundDownload(n: Notification) {
        startForeground(NOTIF_DOWNLOAD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        foregroundDownloadStarted = true
    }

    private fun startForegroundPrep(n: Notification) {
        if (!foregroundDownloadStarted) {
            startForeground(NOTIF_PREPARE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_PREPARE_ID, n)
        }
    }

    private fun runningNotification(jobId: String, percent: Int): Notification {
        val cancel = PendingIntent.getService(
            this, jobId.hashCode(),
            Intent(this, SnagService::class.java)
                .setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SnagServiceChannels.DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("$percent%")
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.action_cancel), cancel)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun prepNotification(workId: String, percent: Int, text: String): Notification {
        val cancel = PendingIntent.getService(
            this, workId.hashCode(),
            Intent(this, SnagService::class.java)
                .setAction(ACTION_CANCEL_PREPARE).putExtra(EXTRA_WORK_ID, workId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_STUDIO
            putExtra(EXTRA_WORK_ID, workId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SnagServiceChannels.DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_prepare_title))
            .setContentText(text)
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.action_cancel), cancel)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun doneNotification(title: String, contentText: String = getString(R.string.notif_done)): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SnagServiceChannels.DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title.ifBlank { contentText })
            .setContentText(contentText)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private suspend fun runDownload(
        jobId: String, url: String, fmt: Downloader.Format, ephemeral: Boolean,
    ) {
        val job = JobStore.get(jobId) ?: run { stopSelf(); return }
        if (job.status == JobStatus.CANCELLED) {
            checkStopService()
            return
        }
        JobStore.update(jobId) {
            it.copy(
                status = if (downloadGate.isLocked) JobStatus.QUEUED
                else JobStatus.DOWNLOADING,
                progress = 0f,
            )
        }
        var lastNotif = 0L
        try {
            val file = downloadGate.withLock {
                val cur = JobStore.get(jobId)?.status
                if (cur != JobStatus.QUEUED && cur != JobStatus.DOWNLOADING) {
                    return@withLock null
                }
                JobStore.update(jobId) {
                    if (it.status == JobStatus.QUEUED)
                        it.copy(status = JobStatus.DOWNLOADING) else it
                }
                Downloader.download(this, jobId, url, fmt, selection = job.selectedQuality.takeIf { fmt == Downloader.Format.AUTO }) { p, eta, _ ->
                    if (JobStore.get(jobId)?.status == JobStatus.CANCELLED) {
                        Downloader.cancel(jobId)
                        return@download
                    }
                    JobStore.update(jobId) { it.copy(progress = p, etaSec = eta) }
                    val now = System.currentTimeMillis()
                    if (now - lastNotif > 800) {
                        lastNotif = now
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(
                            NOTIF_DOWNLOAD_ID,
                            runningNotification(jobId, p.toInt().coerceIn(0, 100)),
                        )
                    }
                }
            } ?: return
            if (JobStore.get(jobId)?.status == JobStatus.CANCELLED) {
                file.delete()
                return
            }
            val title = job.title.ifBlank { file.nameWithoutExtension }
            if (ephemeral) {
                val cached = ShareCache.store(this, file, title)
                JobStore.update(jobId) {
                    it.copy(status = JobStatus.DONE, ephemeral = true,
                        publishedUri = cached.uri, publishedName = cached.displayName,
                        publishedBytes = cached.bytes, progress = 100f)
                }
            } else {
                val published = SaveSink.publish(
                    this, file,
                    SaveSink.displayName(title, file.extension.ifBlank { "mp4" }),
                )
                file.delete()
                JobStore.update(jobId) {
                    it.copy(
                        status = JobStatus.DONE,
                        publishedUri = published.uri,
                        publishedName = published.displayName,
                        publishedBytes = published.bytes,
                        progress = 100f,
                    )
                }
                getSystemService(NotificationManager::class.java)
                    .notify(jobId.hashCode(), doneNotification(published.displayName))
            }
        } catch (e: Exception) {
            val cancelled = JobStore.get(jobId)?.status == JobStatus.CANCELLED ||
                e.message?.contains("destroy", ignoreCase = true) == true
            JobStore.update(jobId) {
                it.copy(
                    status = if (cancelled) JobStatus.CANCELLED else JobStatus.FAILED,
                    error = if (cancelled) null else shortError(e),
                )
            }
        } finally {
            checkStopService()
        }
    }

    private suspend fun runPreparation(workId: String) {
        val nm = getSystemService(NotificationManager::class.java)
        var lastNotif = 0L

        fun updateProgress(phase: Int, p: Int) {
            val pct = p.coerceIn(0, 99)
            PreparationStore.update {
                if (it.workId == workId && it.running) it.copy(phase = phase, pct = pct) else it
            }
            val now = System.currentTimeMillis()
            if (now - lastNotif > 500) {
                lastNotif = now
                val text = if (phase == 1) getString(R.string.prepare_compressing, pct)
                else getString(R.string.prepare_rendering, pct)
                nm.notify(NOTIF_PREPARE_ID, prepNotification(workId, pct, text))
            }
        }

        var failure: Throwable? = null
        var prepSuccess: PrepSuccess? = null
        try {
            val req = PreparationStore.currentRequest()
            val curState = PreparationStore.state.value
            if (req == null || req.workId != workId || !curState.running) {
                return
            }
            if (curState.cancelling) {
                throw CancellationException("Cancelled before start")
            }

            val actualDuration = Compressor.probe(this, req.sourceUri).durationUs / 1000
            val isValid = if (req.op in listOf(StudioOp.STICKER_STATIC, StudioOp.FRAME)) {
                PreparationRules.validFramePosition(req.startMs, actualDuration)
            } else {
                PreparationRules.validRange(req.startMs, req.endMs, actualDuration)
            }
            require(isValid) {
                getString(R.string.prepare_invalid)
            }
            val staged = FfmpegRunner.stage(this, req.sourceUri, workId)
            currentCoroutineContext().ensureActive()

            val progress: (Int) -> Unit = { p -> updateProgress(0, p) }
            val opts = req.opts.toStudioOpts()
            val directCompression = req.op == StudioOp.CLIP && req.targetBytes != null &&
                req.startMs == 0L && req.endMs >= actualDuration - 50 &&
                opts.speed == 1f && !opts.mute && opts.rotate % 360 == 0 &&
                opts.crop == FfmpegRunner.Crop.ORIG && opts.skip.isEmpty()
            var file = when (req.op) {
                StudioOp.CLIP -> if (directCompression && staged.length() > req.targetBytes!!) staged
                    else if (directCompression) {
                        try { FfmpegRunner.copyFullClip(this, staged) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { FfmpegRunner.cutClip(this, staged, req.startMs, req.endMs, opts, progress) }
                    } else FfmpegRunner.cutClip(this, staged, req.startMs, req.endMs, opts, progress)
                StudioOp.GIF -> FfmpegRunner.makeGif(this, staged, req.startMs, req.endMs, opts, progress)
                StudioOp.STICKER -> FfmpegRunner.makeSticker(this, staged, req.startMs, req.endMs, opts, progress)
                StudioOp.STICKER_STATIC -> FfmpegRunner.makeStaticSticker(this, staged, req.startMs)
                StudioOp.MP3, StudioOp.RINGTONE -> FfmpegRunner.extractAudio(
                    this, staged, req.startMs, req.endMs, opts.speed, progress
                )
                StudioOp.FRAME -> FfmpegRunner.frameJpg(this, staged, req.startMs)
            }
            currentCoroutineContext().ensureActive()

            if (req.targetBytes != null && file.length() > req.targetBytes) {
                updateProgress(1, 0)
                file = Compressor.compress(this, "$workId-fit", Uri.fromFile(file), req.targetBytes) { _, p ->
                    updateProgress(1, p)
                }
                check(file.length() in 1..req.targetBytes) { "Size target was not met" }
            }
            currentCoroutineContext().ensureActive()

            val cached = ShareCache.store(this, file, req.title.ifBlank { "video" })
            val resultId = JobStore.nextId()
            val resultJob = Job(
                id = resultId,
                url = req.sourceUrl,
                title = req.title,
                thumbnail = req.thumbnail,
                site = req.site,
                durationSec = if (req.op in listOf(StudioOp.FRAME, StudioOp.STICKER_STATIC)) 0L
                    else ((req.endMs - req.startMs) / opts.speed / 1000).toLong(),
                status = JobStatus.DONE,
                progress = 100f,
                ephemeral = true,
                publishedUri = cached.uri,
                publishedName = cached.displayName,
                publishedBytes = cached.bytes,
            )
            JobStore.add(resultJob)
            val snap = PreparationSnapshot(
                op = req.op,
                startMs = req.rangeStartMs ?: req.startMs,
                endMs = req.rangeEndMs ?: req.endMs,
                opts = req.opts,
                targetBytes = req.targetBytes,
                framePositionMs = if (req.op == StudioOp.STICKER_STATIC || req.op == StudioOp.FRAME) req.startMs else null,
            )
            prepSuccess = PrepSuccess(cached, resultId, req.op, snap)
        } catch (e: CancellationException) {
            failure = e
        } catch (e: Exception) {
            failure = e
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                cleanupDelayHook?.invoke()
                FfmpegRunner.cleanup(this@SnagService, workId)
                File(filesDir, "squash").listFiles()
                    ?.filter { it.name.startsWith("$workId-") }?.forEach { it.delete() }
            }
            if (currentPrepWorkId == workId) {
                currentPrepWorkId = null
                prepJob = null
            }
            if (failure != null) {
                val err = if (failure is CancellationException) {
                    getString(R.string.state_cancelled)
                } else {
                    shortError(failure as Exception)
                }
                PreparationStore.update {
                    if (it.workId == workId && it.phase != 2) {
                        it.copy(running = false, cancelling = false, error = err, showReady = false)
                    } else it
                }
            } else if (prepSuccess != null) {
                val succ = prepSuccess
                JobStore.get(succ.resultJobId)?.let { HistoryStore.record(it) }
                PreparationStore.update {
                    if (it.workId == workId && it.phase != 2) {
                        it.copy(
                            running = false,
                            cancelling = false,
                            pct = 100,
                            uri = succ.cached.uri,
                            name = succ.cached.displayName,
                            bytes = succ.cached.bytes,
                            ephemeral = true,
                            resultJobId = succ.resultJobId,
                            op = succ.op,
                            snapshot = succ.snapshot,
                            showReady = true,
                            saved = false,
                            error = null,
                        )
                    } else it
                }
                nm.notify(workId.hashCode(), doneNotification(succ.cached.displayName, getString(R.string.notif_prepare_ready)))
            } else {
                PreparationStore.update {
                    if (it.workId == workId && it.phase != 2 && (it.running || it.cancelling)) {
                        it.copy(running = false, cancelling = false, showReady = false)
                    } else it
                }
            }
            checkStopService()
        }
        if (failure is CancellationException) {
            throw failure
        }
    }

    private data class PrepSuccess(
        val cached: SaveSink.Published,
        val resultJobId: String,
        val op: StudioOp,
        val snapshot: PreparationSnapshot,
    )

    private fun checkStopService() {
        val hasActiveDownloads = JobStore.jobs.value.any {
            it.status == JobStatus.DOWNLOADING || it.status == JobStatus.QUEUED
        }
        val isPrepRunning = prepJob?.isActive == true ||
            (PreparationStore.state.value.running && PreparationStore.state.value.phase != 2) ||
            PreparationStore.state.value.cancelling
        val nm = getSystemService(NotificationManager::class.java)
        if (!hasActiveDownloads && !isPrepRunning) {
            nm.cancel(NOTIF_DOWNLOAD_ID)
            nm.cancel(NOTIF_PREPARE_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundDownloadStarted = false
            stopSelf()
        } else if (!hasActiveDownloads) {
            foregroundDownloadStarted = false
            nm.cancel(NOTIF_DOWNLOAD_ID)
            val cur = PreparationStore.state.value
            if (cur.running && cur.workId.isNotEmpty() && cur.phase != 2) {
                val text = if (cur.phase == 1) getString(R.string.prepare_compressing, cur.pct)
                else getString(R.string.prepare_rendering, cur.pct)
                startForeground(NOTIF_PREPARE_ID, prepNotification(cur.workId, cur.pct, text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
            }
        } else if (!isPrepRunning) {
            nm.cancel(NOTIF_PREPARE_ID)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val downloadGate = Mutex()

        @Volatile
        var cleanupDelayHook: (suspend () -> Unit)? = null

        @Volatile
        var cancelProcessedHook: ((String?) -> Unit)? = null

        const val ACTION_DOWNLOAD = "app.snag.DOWNLOAD"
        const val ACTION_CANCEL = "app.snag.CANCEL"
        const val ACTION_PREPARE = "app.snag.PREPARE"
        const val ACTION_CANCEL_PREPARE = "app.snag.CANCEL_PREPARE"
        const val ACTION_OPEN_STUDIO = "app.snag.OPEN_STUDIO"

        const val EXTRA_JOB_ID = "jobId"
        const val EXTRA_URL = "url"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_EPHEMERAL = "ephemeral"
        const val EXTRA_WORK_ID = "workId"

        const val NOTIF_DOWNLOAD_ID = 42
        const val NOTIF_PREPARE_ID = 43
        const val NOTIF_ID = NOTIF_DOWNLOAD_ID

        fun startDownload(
            context: Context, jobId: String, url: String, fmt: Downloader.Format,
            ephemeral: Boolean = false,
        ) {
            context.startForegroundService(
                Intent(context, SnagService::class.java)
                    .setAction(ACTION_DOWNLOAD)
                    .putExtra(EXTRA_JOB_ID, jobId)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_FORMAT, fmt.name)
                    .putExtra(EXTRA_EPHEMERAL, ephemeral),
            )
        }

        fun startPreparation(context: Context, workId: String) {
            context.startForegroundService(
                Intent(context, SnagService::class.java)
                    .setAction(ACTION_PREPARE)
                    .putExtra(EXTRA_WORK_ID, workId),
            )
        }

        fun cancelPreparation(context: Context, workId: String? = null) {
            val intent = Intent(context, SnagService::class.java)
                .setAction(ACTION_CANCEL_PREPARE)
            if (workId != null) intent.putExtra(EXTRA_WORK_ID, workId)
            context.startService(intent)
        }
    }
}
