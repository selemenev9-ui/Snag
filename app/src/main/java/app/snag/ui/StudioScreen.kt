package app.snag.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.VideoSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import app.snag.core.PreparationRules
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import app.snag.core.PreparationSnapshot
import app.snag.core.StudioOptsData
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import app.snag.R
import app.snag.core.FfmpegRunner
import app.snag.core.Job
import kotlinx.coroutines.delay

/** A single preparation workspace: moment, format, size, then share or save. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class)
@Composable
fun StudioDialog(
    vm: MainViewModel,
    job: Job,
    onShare: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit,
    onEnsureNotifications: () -> Unit = {},
) {
    val result by vm.studioResult.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val studioFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val studioKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    var sourceInfo by remember(job.publishedUri) { mutableStateOf<app.snag.core.Compressor.Probe?>(null) }
    LaunchedEffect(job.publishedUri) {
        sourceInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { job.publishedUri?.let { app.snag.core.Compressor.probe(context, it) } }.getOrNull()
        }
    }
    val isReady = result.showReady && result.uri != null
    val activeReq = app.snag.core.PreparationStore.currentRequest()?.takeIf { it.sourceJobId == job.id }
    val initialSnap = result.snapshot?.takeIf { result.sourceJobId == job.id }
    val initialTargetBytes = activeReq?.targetBytes ?: initialSnap?.targetBytes
    val initialTargetSel = when (initialTargetBytes) {
        null -> 0
        8L * 1024 * 1024 -> 8
        25L * 1024 * 1024 -> 25
        50L * 1024 * 1024 -> 50
        else -> -1
    }
    var mediaDurMs by rememberSaveable(job.id) { mutableLongStateOf((job.durationSec * 1000).coerceAtLeast(1000)) }
    var durationResolved by remember(job.publishedUri) { mutableStateOf(false) }
    val editable = !result.running && !result.cancelling && !isReady && durationResolved
    var startMs by rememberSaveable(job.id) {
        mutableStateOf(activeReq?.startMs?.toFloat() ?: initialSnap?.startMs?.toFloat() ?: 0f)
    }
    var endMs by rememberSaveable(job.id) {
        mutableStateOf(activeReq?.endMs?.toFloat() ?: initialSnap?.endMs?.toFloat() ?: mediaDurMs.toFloat())
    }
    var touchedRange by rememberSaveable(job.id) { mutableStateOf(activeReq != null || initialSnap != null) }
    LaunchedEffect(sourceInfo?.durationUs) {
        val actual = (sourceInfo?.durationUs ?: 0) / 1000
        if (actual > 0) {
            mediaDurMs = actual
            durationResolved = true
            if (!touchedRange) endMs = actual.toFloat()
            else endMs = endMs.coerceAtMost(actual.toFloat())
        }
    }
    var formatName by rememberSaveable(job.id) {
        mutableStateOf(activeReq?.op?.name ?: initialSnap?.op?.name ?: StudioOp.CLIP.name)
    }
    val format = StudioOp.valueOf(formatName)
    var targetSelection by rememberSaveable(job.id) { mutableStateOf(initialTargetSel) }
    var customSize by rememberSaveable(job.id) {
        mutableStateOf(if (initialTargetSel == -1 && initialTargetBytes != null) PreparationRules.formatTargetMb(initialTargetBytes) else "")
    }
    var customTouched by rememberSaveable(job.id) { mutableStateOf(initialTargetSel == -1) }
    val initialOpts = activeReq?.opts ?: initialSnap?.opts
    val initialOp = activeReq?.op ?: initialSnap?.op
    var more by rememberSaveable(job.id) {
        mutableStateOf(
            initialOp in listOf(StudioOp.FRAME, StudioOp.RINGTONE) ||
            (initialOpts != null && (initialOpts.speed != 1f || initialOpts.mute || initialOpts.crop != FfmpegRunner.Crop.ORIG || initialOpts.rotate != 0 || initialOpts.skip.isNotEmpty()))
        )
    }
    var speed by rememberSaveable(job.id) { mutableStateOf(initialOpts?.speed ?: 1f) }
    var mute by rememberSaveable(job.id) { mutableStateOf(initialOpts?.mute ?: false) }
    var cropName by rememberSaveable(job.id) { mutableStateOf(initialOpts?.crop?.name ?: FfmpegRunner.Crop.ORIG.name) }
    var rotate by rememberSaveable(job.id) { mutableStateOf(initialOpts?.rotate ?: 0) }
    var exactTime by remember { mutableStateOf(false) }
    var frames by remember(job.id) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var playing by remember { mutableStateOf(false) }
    var sourceAspect by remember { mutableStateOf(16f / 9f) }
    var previewError by remember { mutableStateOf(false) }
    var sponsors by remember(job.id) {
        mutableStateOf<List<app.snag.core.SponsorBlock.Segment>>(
            activeReq?.opts?.skip ?: emptyList()
        )
    }
    var cutSponsors by rememberSaveable(job.id) {
        mutableStateOf(activeReq?.opts?.skip?.isNotEmpty() == true)
    }
    val opts = FfmpegRunner.StudioOpts(speed, mute, FfmpegRunner.Crop.valueOf(cropName), rotate,
        if (cutSponsors && format in listOf(StudioOp.CLIP, StudioOp.GIF, StudioOp.STICKER, StudioOp.STICKER_STATIC)) sponsors else emptyList())
    val target = if (format != StudioOp.CLIP || targetSelection == 0) null
        else if (targetSelection == -1) {
            if (activeReq?.targetBytes != null && customSize == PreparationRules.formatTargetMb(activeReq.targetBytes)) {
                activeReq.targetBytes
            } else {
                PreparationRules.targetBytes(customSize)
            }
        }
        else targetSelection.toLong() * 1024 * 1024
    val validTarget = format != StudioOp.CLIP || targetSelection != -1 || target != null
    val validRange = PreparationRules.validRange(startMs.toLong(), endMs.toLong(), mediaDurMs)
    val rawSelectedMs = (endMs - startMs).coerceAtLeast(0f)
    val skippedMs = if (cutSponsors && sponsors.isNotEmpty() && format in listOf(StudioOp.CLIP, StudioOp.GIF, StudioOp.STICKER, StudioOp.STICKER_STATIC)) {
        sponsors.sumOf { seg ->
            val s = maxOf(startMs.toLong(), seg.startMs)
            val e = minOf(endMs.toLong(), seg.endMs)
            maxOf(0L, e - s)
        }
    } else 0L
    val effectiveStickerSec = maxOf(0f, (rawSelectedMs - skippedMs) / speed / 1000f)
    val compressionPlan = if (target != null && sourceInfo != null) {
        val info = sourceInfo!!
        var w = info.width
        var h = info.height
        when (opts.crop) {
            FfmpegRunner.Crop.S11 -> { w = minOf(w, h); h = w }
            FfmpegRunner.Crop.V916 -> { w = minOf(w, h * 9 / 16); h = minOf(h, info.width * 16 / 9) }
            else -> Unit
        }
        if (rotate % 180 != 0) { val swap = w; w = h; h = swap }
        app.snag.core.CompressionPlan.calculate(w, h, effectiveStickerSec.toDouble(), info.srcBytes, target,
            info.hasAudio && !mute)
    } else null
    val isStickerTooLong = format == StudioOp.STICKER && effectiveStickerSec > 3.00f
    val player = remember(job.id, job.publishedUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(job.publishedUri!!))
            prepare()
        }
    }
    var previewPositionMs by remember(job.id) { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) {
                    durationResolved = true
                    mediaDurMs = sourceInfo?.durationUs?.takeIf { it > 0 }?.div(1000) ?: player.duration
                    if (!touchedRange) endMs = mediaDurMs.toFloat()
                    else endMs = endMs.coerceAtMost(mediaDurMs.toFloat())
                }
                if (state == Player.STATE_ENDED) { player.seekTo(startMs.toLong()); player.play() }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) sourceAspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { previewError = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(job.id, durationResolved, mediaDurMs) {
        if (!durationResolved) return@LaunchedEffect
        frames = runCatching { FfmpegRunner.filmstrip(context, job.publishedUri!!, mediaDurMs * 1000) }
            .getOrDefault(emptyList())
    }
    LaunchedEffect(job.id) {
        val id = app.snag.core.SponsorBlock.videoIdOf(job.url) ?: return@LaunchedEffect
        val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.snag.core.SponsorBlock.segments(id).orEmpty()
        }
        if (fetched.isNotEmpty() || sponsors.isEmpty()) {
            sponsors = fetched
        }
    }
    LaunchedEffect(speed, mute, format) {
        player.setPlaybackSpeed(speed)
        player.volume = if ((mute && format == StudioOp.CLIP) || format in listOf(StudioOp.GIF, StudioOp.STICKER, StudioOp.STICKER_STATIC, StudioOp.FRAME)) 0f else 1f
    }
    LaunchedEffect(cropName, rotate, sourceAspect, format) {
        val effects = mutableListOf<Effect>()
        // Still-frame extraction currently preserves the original picture.
        if (format != StudioOp.FRAME && format != StudioOp.STICKER_STATIC && format != StudioOp.MP3 && format != StudioOp.RINGTONE) {
            val aspect = when (opts.crop) { FfmpegRunner.Crop.V916 -> 9f / 16f
                FfmpegRunner.Crop.S11 -> 1f; else -> sourceAspect }
            if (sourceAspect > aspect) effects += Crop(-aspect / sourceAspect, aspect / sourceAspect, -1f, 1f)
            else if (sourceAspect < aspect) effects += Crop(-1f, 1f, -sourceAspect / aspect, sourceAspect / aspect)
            if (rotate != 0) effects += ScaleAndRotateTransformation.Builder().setRotationDegrees(-rotate.toFloat()).build()
        }
        player.setVideoEffects(effects)
    }
    LaunchedEffect(format, speed, mediaDurMs, durationResolved) {
        if (format == StudioOp.STICKER && durationResolved && !result.running) {
            val window = app.snag.core.StickerWindowRules.resolve(startMs,endMs,mediaDurMs.toFloat(),speed)
            startMs = window.start; endMs = window.end
            touchedRange = true
            player.seekTo(startMs.toLong())
        }
    }
    LaunchedEffect(startMs, endMs, format, speed, cutSponsors, sponsors) {
        while (true) {
            val end = if (format == StudioOp.STICKER) minOf(endMs, startMs + 3000 * speed) else endMs
            if (player.isPlaying && (player.currentPosition < startMs || player.currentPosition >= end)) player.seekTo(startMs.toLong())
            if (player.isPlaying && cutSponsors && format in listOf(StudioOp.CLIP, StudioOp.GIF)) {
                sponsors.firstOrNull { player.currentPosition in it.startMs until it.endMs }?.let {
                    player.seekTo(if (it.endMs < end) it.endMs else startMs.toLong())
                }
            }
            previewPositionMs = player.currentPosition
            delay(100)
        }
    }
    LaunchedEffect(result.running) { if (result.running) player.pause() }
    val visualFormat = format in listOf(StudioOp.CLIP, StudioOp.GIF, StudioOp.STICKER)
    val croppedAspect = if (!visualFormat) sourceAspect else when (opts.crop) {
        FfmpegRunner.Crop.V916 -> 9f / 16f; FfmpegRunner.Crop.S11 -> 1f; else -> sourceAspect }
    val previewAspect = if (visualFormat && rotate % 180 != 0) 1f / croppedAspect else croppedAspect

    val density = androidx.compose.ui.platform.LocalDensity.current
    val largeText = density.fontScale > 1.15f
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    var customFocused by remember { mutableStateOf(false) }
    val customBring = remember { BringIntoViewRequester() }

    val restoreSnapshot: (PreparationSnapshot) -> Unit = { snap ->
        formatName = snap.op.name
        startMs = snap.startMs.toFloat()
        endMs = snap.endMs.toFloat()
        touchedRange = true
        speed = snap.opts.speed
        mute = snap.opts.mute
        cropName = snap.opts.crop.name
        rotate = snap.opts.rotate
        cutSponsors = snap.opts.skip.isNotEmpty()
        targetSelection = when (snap.targetBytes) {
            null -> 0
            8L * 1024 * 1024 -> 8
            25L * 1024 * 1024 -> 25
            50L * 1024 * 1024 -> 50
            else -> -1
        }
        customSize = if (targetSelection == -1 && snap.targetBytes != null) {
            PreparationRules.formatTargetMb(snap.targetBytes)
        } else ""
        customTouched = (targetSelection == -1)
        more = snap.op in listOf(StudioOp.FRAME, StudioOp.RINGTONE) ||
            snap.opts.speed != 1f || snap.opts.mute || snap.opts.crop != FfmpegRunner.Crop.ORIG ||
            snap.opts.rotate != 0 || snap.opts.skip.isNotEmpty()
        val seekPos = snap.framePositionMs ?: snap.startMs
        player.seekTo(seekPos)
    }

    val hasPreviousResult = !isReady && result.uri != null && (result.sourceJobId == null || result.sourceJobId == job.id)
    val currentResolvedFramePos = if (format == StudioOp.STICKER_STATIC) {
        PreparationRules.resolveFramePosition(previewPositionMs, startMs.toLong(), endMs.toLong())
    } else null

    val parametersMatch = hasPreviousResult && (result.snapshot?.matches(
        currentOp = format,
        currentStartMs = startMs.toLong(),
        currentEndMs = endMs.toLong(),
        currentOpts = StudioOptsData.from(opts),
        currentTargetBytes = target,
        currentFramePosMs = currentResolvedFramePos,
    ) ?: false)

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        StudyDialogBars()
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            // Preview shares the screen with settings: shrink it as text or keyboard take room.
            val previewMax = (maxHeight * (if (largeText) 0.2f else if (DesignStudy.cinema) 0.34f else 0.26f))
                .coerceIn(112.dp, if (DesignStudy.cinema && !largeText) 280.dp else 208.dp)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (isReady) R.string.stage_ready else if (result.running || result.cancelling) R.string.stage_processing else R.string.stage_editing),
                            style = MaterialTheme.typography.labelSmall, color = cs.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Text(stringResource(if (isReady) R.string.studio_ready_title else R.string.prepare_title),
                            style = if (largeText) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(job.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.studio_close))
                    }
                }
                if (isReady) {
                    StudioReadyContent(
                        job = job,
                        result = result,
                        largeText = largeText,
                        previewMax = previewMax,
                        onShare = onShare,
                        onSaveCopy = { vm.saveStudioResult() },
                        sourceBytes = sourceInfo?.srcBytes ?: job.publishedBytes,
                        onAdjust = {
                            result.snapshot?.let { restoreSnapshot(it) }
                            vm.adjustStudioResult()
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else if (result.running || result.cancelling) {
                    StudioProgressContent(
                        state = result,
                        onCancel = { vm.cancelStudioExport() },
                        onBackground = onDismiss,
                        onSharePrevious = { result.uri?.let(onShare) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "source_info") {
                        val info = sourceInfo
                        val bytes = info?.srcBytes?.takeIf { it > 0 } ?: job.publishedBytes
                        Text(stringResource(R.string.compression_original, fmtBytes(bytes),
                            "${info?.width ?: 0}×${info?.height ?: 0}", (mediaDurMs / 1000).toString()),
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    if (hasPreviousResult) {
                        item(key = "previous_result") {
                            PreviousResultCard(
                                result = result,
                                parametersMatch = parametersMatch,
                                largeText = largeText,
                                onShare = { onShare(result.uri!!) },
                                onOpenReady = { vm.showReadyResult() },
                                onRestoreSettings = {
                                    result.snapshot?.let { restoreSnapshot(it) }
                                },
                            )
                        }
                    }
                    item {
                        BoxWithConstraints(Modifier.fillMaxWidth().height(previewMax).clip(studyShape(20))
                            .background(Color.Black), contentAlignment = Alignment.Center) {
                            val width = minOf(maxWidth, maxHeight * previewAspect)
                            PlayerSurface(player = player, modifier = Modifier.width(width).height(width / previewAspect))
                            if (previewError) Text(stringResource(R.string.preview_failed), color = Color.White)
                            else IconButton(onClick = { if (playing) player.pause() else player.play() },
                                enabled = !result.running,
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                    .background(Color.Black.copy(alpha = .6f), CircleShape)) {
                                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    stringResource(if (playing) R.string.studio_pause else R.string.studio_play), tint = Color.White)
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.prepare_moment), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { exactTime = true }, enabled = editable) {
                                Text("${fmtMs(startMs.toLong())} - ${fmtMs(endMs.toLong())}")
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(64.dp).clip(studyShape(12)).background(cs.surfaceContainer)) {
                            Row(Modifier.fillMaxSize()) {
                                frames.forEach { frame -> Image(frame, null, Modifier.weight(1f).height(64.dp), contentScale = ContentScale.Crop) }
                            }
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val left = size.width * startMs / mediaDurMs
                                val right = size.width * endMs / mediaDurMs
                                drawRect(Color.Black.copy(alpha = .55f), size = androidx.compose.ui.geometry.Size(left, size.height))
                                drawRect(Color.Black.copy(alpha = .55f),
                                    topLeft = androidx.compose.ui.geometry.Offset(right, 0f),
                                    size = androidx.compose.ui.geometry.Size(size.width - right, size.height))
                                drawRect(cs.primary, topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                                    size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(0f), size.height),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            }
                            if (format == StudioOp.STICKER) {
                                val windowLength = endMs-startMs
                                val maxStart = (mediaDurMs-windowLength).coerceAtLeast(0f)
                                androidx.compose.material3.Slider(value=startMs.coerceAtMost(maxStart),
                                    onValueChange={ position ->
                                        val window = app.snag.core.StickerWindowRules.move(position,windowLength,mediaDurMs.toFloat(),speed)
                                        touchedRange = true; startMs=window.start; endMs=window.end
                                        player.pause(); player.seekTo(startMs.toLong())
                                    },valueRange=0f..maxOf(1f,maxStart), enabled=editable && maxStart>0f,
                                    colors=androidx.compose.material3.SliderDefaults.colors(
                                        activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent),
                                    modifier=Modifier.fillMaxSize().semantics {
                                        contentDescription = context.getString(R.string.sticker_window_move)
                                    })
                            } else RangeSlider(value = startMs..endMs,
                                onValueChange = { r ->
                                    if (r.endInclusive - r.start >= 500) {
                                        val changed = if (r.start != startMs) r.start else r.endInclusive
                                        touchedRange = true; startMs = r.start; endMs = r.endInclusive
                                        player.pause(); player.seekTo(changed.toLong())
                                    }
                                }, valueRange = 0f..mediaDurMs.toFloat(), enabled = editable,
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                                    disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent),
                                modifier = Modifier.fillMaxSize())
                        }
                        Text(stringResource(R.string.studio_selected, fmtMs(((endMs - startMs) / speed).toLong())),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        if (format == StudioOp.STICKER) {
                            Text(stringResource(R.string.sticker_window_hint),style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)
                            val maxLength=minOf(mediaDurMs.toFloat(),3000f*speed)
                            val minLength=minOf(500f,maxLength)
                            androidx.compose.material3.Slider(value=(endMs-startMs).coerceIn(minLength,maxLength),
                                onValueChange={ length ->
                                    val window=app.snag.core.StickerWindowRules.move(startMs,length,mediaDurMs.toFloat(),speed)
                                    startMs=window.start; endMs=window.end; touchedRange=true
                                    player.pause(); player.seekTo(startMs.toLong())
                                },valueRange=minLength..maxOf(minLength+.01f,maxLength),
                                enabled=editable && maxLength>minLength,
                                modifier=Modifier.fillMaxWidth().semantics {
                                    contentDescription=context.getString(R.string.sticker_window_length)
                                })
                        }
                    }
                    item {
                        Text(stringResource(R.string.prepare_format), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(StudioOp.CLIP, StudioOp.GIF, StudioOp.STICKER, StudioOp.MP3).forEach { op ->
                                val isSelected = if (op == StudioOp.STICKER) {
                                    format == StudioOp.STICKER || format == StudioOp.STICKER_STATIC
                                } else {
                                    format == op
                                }
                                FilterChip(
                                    selected = isSelected,
                                    enabled = editable,
                                    onClick = {
                                        if (op == StudioOp.STICKER) {
                                            if (format != StudioOp.STICKER && format != StudioOp.STICKER_STATIC) {
                                                formatName = StudioOp.STICKER_STATIC.name
                                            }
                                        } else {
                                            formatName = op.name
                                        }
                                    },
                                    label = { Text(opLabel(op)) }
                                )
                            }
                            FilterChip(selected = more, enabled = editable,
                                onClick = { more = !more }, label = { Text(stringResource(R.string.prepare_more)) })
                        }
                        if (format == StudioOp.GIF) Text(stringResource(R.string.gif_hint),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        if (format == StudioOp.STICKER || format == StudioOp.STICKER_STATIC) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                FilterChip(
                                    selected = format == StudioOp.STICKER_STATIC,
                                    enabled = editable,
                                    onClick = { formatName = StudioOp.STICKER_STATIC.name },
                                    label = { Text(stringResource(R.string.sticker_type_static)) }
                                )
                                FilterChip(
                                    selected = format == StudioOp.STICKER,
                                    enabled = editable,
                                    onClick = { formatName = StudioOp.STICKER.name },
                                    label = { Text(stringResource(R.string.sticker_type_video)) }
                                )
                            }
                            if (format == StudioOp.STICKER_STATIC) {
                                Text(
                                    stringResource(R.string.sticker_static_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                Text(
                                    stringResource(R.string.sticker_video_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                if (isStickerTooLong) {
                                    Surface(
                                        shape = studyShape(12),
                                        color = cs.errorContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.sticker_duration_warning, effectiveStickerSec),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = cs.onErrorContainer,
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    var remainingTarget = (3000f * speed).toLong()
                                                    var current = startMs.toLong()
                                                    if (cutSponsors && sponsors.isNotEmpty()) {
                                                        while (remainingTarget > 0 && current < mediaDurMs) {
                                                            val nextSeg = sponsors.firstOrNull { current in it.startMs until it.endMs }
                                                            if (nextSeg != null) {
                                                                current = nextSeg.endMs
                                                            } else {
                                                                val upcoming = sponsors.filter { it.startMs > current }.minByOrNull { it.startMs }
                                                                val step = if (upcoming != null) minOf(remainingTarget, upcoming.startMs - current) else remainingTarget
                                                                current += step
                                                                remainingTarget -= step
                                                            }
                                                        }
                                                        endMs = current.toFloat().coerceAtMost(mediaDurMs.toFloat())
                                                    } else {
                                                        endMs = (startMs + 3000f * speed).coerceAtMost(mediaDurMs.toFloat())
                                                    }
                                                    touchedRange = true
                                                    player.pause()
                                                    player.seekTo(startMs.toLong())
                                                },
                                                shape = studyShape(8),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            ) {
                                                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.sticker_trim_action))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (format == StudioOp.CLIP) item {
                        if (DesignStudy.active) StudySizeReadout(
                            source = fmtBytes(sourceInfo?.srcBytes ?: job.publishedBytes),
                            target = if (target != null && target > 0) "≤ ${fmtBytes(target)}" else stringResource(R.string.size_unlimited),
                        )
                        Text(stringResource(R.string.prepare_size), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 8, 25, 50, -1).forEach { size ->
                                FilterChip(selected = targetSelection == size, enabled = editable,
                                    onClick = {
                                        if (size == -1 && targetSelection != -1 && !customTouched) {
                                            customSize = if (targetSelection > 0) targetSelection.toString() else "8"
                                        }
                                        targetSelection = size
                                    }, label = {
                                        Text(when (size) { 0 -> stringResource(R.string.size_unlimited)
                                            -1 -> stringResource(R.string.size_custom); else -> "$size ${stringResource(R.string.unit_mb)}" })
                                    })
                            }
                        }
                        if (targetSelection == -1) {
                            val invalidCustom = target == null
                            LaunchedEffect(Unit) { runCatching { customBring.bringIntoView() } }
                            LaunchedEffect(customFocused, imeOpen, invalidCustom, customSize) {
                                if (customFocused) { delay(150); runCatching { customBring.bringIntoView() } }
                            }
                            OutlinedTextField(value = customSize, onValueChange = { customSize = it; customTouched = true },
                                label = { Text(stringResource(R.string.size_custom)) }, suffix = { Text(stringResource(R.string.unit_mb)) },
                                singleLine = true, enabled = editable, isError = invalidCustom,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                supportingText = {
                                    Column {
                                        Text(stringResource(if (invalidCustom) R.string.size_invalid else R.string.size_range))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(customBring)
                                    .onFocusChanged { customFocused = it.isFocused })
                        }
                        Text(stringResource(R.string.size_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        compressionPlan?.let { plan ->
                            if (plan.alreadyFits && startMs == 0f && endMs >= mediaDurMs - 50 &&
                                opts.speed == 1f && !mute && opts.crop == FfmpegRunner.Crop.ORIG && rotate == 0 && opts.skip.isEmpty()) {
                                Text(stringResource(R.string.compression_already_fits), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (more) item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(StudioOp.FRAME, StudioOp.RINGTONE).forEach { op ->
                                FilterChip(selected = format == op, enabled = editable,
                                    onClick = { formatName = op.name }, label = { Text(opLabel(op)) })
                            }
                        }
                        if (format != StudioOp.FRAME) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(.5f, 1f, 1.5f, 2f).forEach { value ->
                                FilterChip(selected = speed == value, enabled = editable,
                                    onClick = { speed = value }, label = { Text("${value}x") })
                            }
                        }
                        if (format == StudioOp.CLIP) FilterChip(selected = mute, enabled = editable,
                            onClick = { mute = !mute }, label = { Text(stringResource(R.string.studio_mute)) })
                        if (visualFormat) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FfmpegRunner.Crop.entries.forEach { crop ->
                                FilterChip(selected = opts.crop == crop, enabled = editable,
                                    onClick = { cropName = crop.name }, label = { Text(when (crop) {
                                        FfmpegRunner.Crop.ORIG -> stringResource(R.string.crop_original)
                                        FfmpegRunner.Crop.V916 -> "9:16"; FfmpegRunner.Crop.S11 -> "1:1" }) })
                            }
                            FilterChip(selected = rotate != 0, enabled = editable,
                                onClick = { rotate = (rotate + 90) % 360 },
                                label = { Text(stringResource(R.string.rotate_degrees, rotate)) })
                        }
                        if (sponsors.isNotEmpty() && format in listOf(StudioOp.CLIP, StudioOp.GIF)) {
                            FilterChip(selected = cutSponsors, enabled = editable,
                                onClick = { cutSponsors = !cutSponsors }, label = {
                                    Text(stringResource(R.string.studio_nosponsors, sponsors.size)) })
                        }
                    }
                }
                // Primary action stays reachable while the controls scroll.
                Surface(color = cs.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                        if (result.running || result.cancelling) {
                            val text = if (result.cancelling) {
                                stringResource(R.string.state_cancelled)
                            } else {
                                when (result.phase) {
                                    1 -> stringResource(R.string.prepare_compressing, result.pct)
                                    2 -> stringResource(R.string.prepare_saving)
                                    else -> stringResource(R.string.prepare_rendering, result.pct)
                                }
                            }
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(progress = { result.pct / 100f }, modifier = Modifier.fillMaxWidth())
                            if (result.phase != 2) TextButton(
                                onClick = { vm.cancelStudioExport() },
                                enabled = !result.cancelling
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        } else {
                            if (isStickerTooLong) {
                                Text(
                                    text = stringResource(R.string.sticker_duration_warning, effectiveStickerSec),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.error,
                                )
                            }
                            compressionPlan?.let { plan -> CompressionForecast(plan, target!!) }
                            if (!imeOpen) Text(stringResource(R.string.prepare_private), style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant)
                            if (hasPreviousResult && parametersMatch) {
                                Button(
                                    onClick = { studioFocus.clearFocus(); studioKeyboard?.hide(); vm.showReadyResult() },
                                    enabled = true,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    shape = studyShape(16),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.studio_open_ready_action))
                                }
                            } else {
                                Button(onClick = {
                                    player.pause()
                                    studioFocus.clearFocus()
                                    studioKeyboard?.hide()
                                    onEnsureNotifications()
                                    val exportStartMs = if (format == StudioOp.STICKER_STATIC) {
                                        PreparationRules.resolveFramePosition(player.currentPosition, startMs.toLong(), endMs.toLong())
                                    } else {
                                        startMs.toLong()
                                    }
                                    vm.studioExport(
                                        job = job,
                                        op = format,
                                        startMs = exportStartMs,
                                        endMs = endMs.toLong(),
                                        opts = opts,
                                        targetBytes = target,
                                        framePositionMs = if (format == StudioOp.STICKER_STATIC) player.currentPosition else null,
                                    )
                                }, enabled = durationResolved && validTarget && compressionPlan?.feasible != false && (if (format == StudioOp.STICKER_STATIC) PreparationRules.validFramePosition(startMs.toLong(), mediaDurMs) else validRange) && !isStickerTooLong && !previewError,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = studyShape(16)) {
                                    Text(stringResource(R.string.prepare_action)) }
                            }
                        }
                    }
                }
                }
            }
            }
        }
    }
    if (exactTime) {
        var from by remember { mutableStateOf(fmtMs(startMs.toLong())) }
        var to by remember { mutableStateOf(fmtMs(endMs.toLong())) }
        val a = PreparationRules.timeMs(from)
        val b = PreparationRules.timeMs(to)
        val valid = a != null && b != null && PreparationRules.validRange(a, b, mediaDurMs)
        AlertDialog(onDismissRequest = { exactTime = false },
            title = { Text(stringResource(R.string.prepare_moment)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.time_hint))
                OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.time_from)) }, singleLine = true)
                OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.time_to)) }, singleLine = true)
                if (!valid) Text(stringResource(R.string.prepare_invalid), color = cs.error)
            } },
            confirmButton = { TextButton(enabled = valid, onClick = {
                if (format == StudioOp.STICKER) {
                    val window=app.snag.core.StickerWindowRules.resolve(a!!.toFloat(),b!!.toFloat(),mediaDurMs.toFloat(),speed)
                    startMs=window.start; endMs=window.end
                } else { startMs = a!!.toFloat(); endMs = b!!.toFloat() }
                touchedRange = true
                player.seekTo(a); exactTime = false
            }) { Text(stringResource(R.string.action_apply)) } },
            dismissButton = { TextButton(onClick = { exactTime = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
fun PreviousResultCard(
    result: app.snag.core.PreparationState,
    parametersMatch: Boolean,
    largeText: Boolean,
    onShare: () -> Unit,
    onOpenReady: () -> Unit,
    onRestoreSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val readyUri = result.uri ?: return

    Surface(
        shape = studyShape(16),
        color = cs.surfaceContainerHigh,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Icon + "Предыдущий результат" + format/size badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.previous_result_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                }

                Surface(
                    shape = studyShape(8),
                    color = cs.primaryContainer,
                ) {
                    Text(
                        text = when (result.op) {
                            StudioOp.STICKER -> stringResource(R.string.studio_sticker_video)
                            StudioOp.STICKER_STATIC -> stringResource(R.string.studio_sticker_static)
                            else -> opLabel(result.op)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // File details: name and formatted size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                result.name?.let { fileName ->
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val formattedBytes = fmtBytes(result.bytes)
                if (formattedBytes.isNotEmpty()) {
                    Text(
                        text = formattedBytes,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                }
            }

            // Status notice: match or diff
            Surface(
                shape = studyShape(8),
                color = if (parametersMatch) cs.surfaceContainerHighest else cs.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (parametersMatch) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (parametersMatch) cs.primary else cs.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(if (parametersMatch) R.string.previous_result_match else R.string.previous_result_diff),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (parametersMatch) cs.onSurface else cs.onSecondaryContainer,
                    )
                }
            }

            // Actions: Share + Open/Restore
            if (largeText) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = studyShape(12),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share))
                    }
                    if (parametersMatch) {
                        FilledTonalButton(
                            onClick = onOpenReady,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = studyShape(12),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open_ready))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRestoreSettings,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = studyShape(12),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.previous_result_restore))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = studyShape(12),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share))
                    }
                    if (parametersMatch) {
                        FilledTonalButton(
                            onClick = onOpenReady,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = studyShape(12),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open_ready))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRestoreSettings,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = studyShape(12),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.previous_result_restore))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun opLabel(op: StudioOp): String = when (op) {
    StudioOp.CLIP -> stringResource(R.string.format_video)
    StudioOp.GIF -> "GIF"
    StudioOp.MP3 -> stringResource(R.string.format_audio)
    StudioOp.STICKER, StudioOp.STICKER_STATIC -> stringResource(R.string.studio_sticker)
    StudioOp.FRAME -> stringResource(R.string.studio_frame)
    StudioOp.RINGTONE -> stringResource(R.string.studio_ringtone)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
/** In-app player: "Открыть" plays inside Snag — speed chips, no external app. */
@Composable
fun PlayerDialog(
    job: Job,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(job.id) {
        ExoPlayer.Builder(context).build().apply {
            job.publishedUri?.let { setMediaItem(MediaItem.fromUri(it)) }
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }
    var aspect by remember(job.publishedUri) { mutableStateOf(16f / 9f) }
    var frameReady by remember(job.publishedUri) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { frameReady = true }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val fittedWidth = minOf(maxWidth, maxHeight * aspect)
                PlayerSurface(
                    player = player,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(fittedWidth).height(fittedWidth / aspect)
                        .testTag("result_video_surface")
                        .semantics { stateDescription = if (frameReady) "ready" else "loading" }
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction
                                    .MutableInteractionSource()
                            },
                            indication = null,
                        ) { if (playing) player.pause() else player.play() },
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.7f)),
                            ),
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = {
                            if (playing) player.pause() else player.play()
                        }) {
                            Icon(
                                if (playing) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                null, tint = Color.White,
                            )
                        }
                        listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                            FilterChip(
                                selected = speed == s,
                                onClick = {
                                    speed = s; player.setPlaybackSpeed(s)
                                },
                                label = { Text("${s}×") },
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        stringResource(R.string.action_dismiss),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, shape = CircleShape) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun CompressionForecast(plan: app.snag.core.CompressionPlan, target: Long) {
    val cs = MaterialTheme.colorScheme
    Text(stringResource(R.string.compression_forecast, fmtBytes(target), "${plan.width}×${plan.height}"),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    if (!plan.feasible) Text(stringResource(R.string.compression_impossible), color = cs.error,
        style = MaterialTheme.typography.bodySmall)
    else if (!plan.alreadyFits && plan.videoBps < 400000) {
        Text(stringResource(R.string.compression_severe_warning), color = cs.error,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun fmtBytes(b: Long): String {
    val mb = stringResource(R.string.unit_mb)
    val kb = stringResource(R.string.unit_kb)
    return when {
        b <= 0 -> ""
        b >= 1 shl 20 -> "%.1f %s".format(b / 1048576.0, mb)
        else -> "%.0f %s".format(b / 1024.0, kb)
    }
}

/**
 * Clean, expressive, and adaptive ready result view ("Studio Deck" Result).
 * Hides editing controls and highlights ready state, measured file size,
 * prominent "Share" action, and distinct "Save a copy" / "Adjust" options.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun StudioReadyContent(
    job: Job,
    result: app.snag.core.PreparationState,
    largeText: Boolean,
    previewMax: androidx.compose.ui.unit.Dp,
    onShare: (android.net.Uri) -> Unit,
    onSaveCopy: () -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
    sourceBytes: Long = 0,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val readyUri = result.uri ?: return
    // Video/Sticker playable player (ExoPlayer only for video/audio)
    val isPlayableVideo = result.op == StudioOp.CLIP || (result.op == StudioOp.STICKER && result.name?.endsWith(".webm", ignoreCase = true) == true)
    val isPlayableAudio = result.op in listOf(StudioOp.MP3, StudioOp.RINGTONE)
    val isImageResult = result.op in listOf(StudioOp.GIF, StudioOp.FRAME, StudioOp.STICKER_STATIC) || (result.op == StudioOp.STICKER && result.name?.endsWith(".webm", ignoreCase = true) != true)

    var previewError by remember(readyUri) { mutableStateOf(false) }
    var videoAspect by remember(readyUri) { mutableStateOf<Float?>(null) }
    var telegramNotInstalled by remember(readyUri) { mutableStateOf(false) }
    var telegramImportFailure by remember(readyUri) { mutableStateOf<String?>(null) }

    val resultPlayer = remember(readyUri, isPlayableVideo || isPlayableAudio) {
        if (isPlayableVideo || isPlayableAudio) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(readyUri))
                if (isPlayableVideo) {
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = true
                }
                if (result.op == StudioOp.STICKER) {
                    volume = 0f
                }
                prepare()
            }
        } else null
    }

    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(resultPlayer) {
        val player = resultPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                previewError = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val isSaving = result.running && result.phase == 2

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Result Preview or Visual Card
            item {
                when {
                    previewError -> {
                        Surface(
                            shape = studyShape(20),
                            color = cs.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.preview_failed),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.error,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.studio_preview_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    isPlayableVideo && resultPlayer != null -> {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewMax)
                                .clip(studyShape(20))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            val targetAspect = videoAspect ?: (16f / 9f)
                            val surfaceWidth = minOf(maxWidth, maxHeight * targetAspect)
                            val surfaceHeight = surfaceWidth / targetAspect

                            PlayerSurface(
                                player = resultPlayer,
                                modifier = Modifier
                                    .width(surfaceWidth)
                                    .height(surfaceHeight),
                            )
                            IconButton(
                                onClick = { if (isPlaying) resultPlayer.pause() else resultPlayer.play() },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = .6f), CircleShape),
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    stringResource(if (isPlaying) R.string.studio_pause else R.string.studio_play),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    isImageResult -> {
                        // Display GIF or Frame directly via AsyncImage, preserving true aspect ratio
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewMax)
                                .clip(studyShape(20))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = readyUri,
                                contentDescription = opLabel(result.op),
                                imageLoader = remember(context) { app.snag.SnagApp.createImageLoader(context) },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                onError = { previewError = true },
                            )
                        }
                    }
                    else -> {
                        // For Audio / Ringtone
                        Surface(
                            shape = studyShape(20),
                            color = cs.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = cs.primaryContainer,
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            when (result.op) {
                                                StudioOp.MP3, StudioOp.RINGTONE -> Icons.Filled.Audiotrack
                                                StudioOp.STICKER, StudioOp.STICKER_STATIC -> Icons.Filled.EmojiEmotions
                                                StudioOp.FRAME -> Icons.Filled.PhotoCamera
                                                StudioOp.GIF -> Icons.Filled.Gif
                                                else -> Icons.Filled.ContentCut
                                            },
                                            contentDescription = null,
                                            tint = cs.onPrimaryContainer,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = when (result.op) {
                                            StudioOp.STICKER -> stringResource(R.string.studio_sticker_video)
                                            StudioOp.STICKER_STATIC -> stringResource(R.string.studio_sticker_static)
                                            else -> opLabel(result.op)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = result.name ?: job.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isPlayableAudio && resultPlayer != null) {
                                    IconButton(
                                        onClick = { if (isPlaying) resultPlayer.pause() else resultPlayer.play() },
                                        modifier = Modifier.background(cs.surfaceContainerHighest, CircleShape),
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            stringResource(if (isPlaying) R.string.studio_pause else R.string.studio_play),
                                            tint = cs.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // 2. Summary Card: format, measured size, temporary cache badge
            item {
                if (DesignStudy.active) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudySizeReadout(fmtBytes(sourceBytes), fmtBytes(result.bytes))
                        Text(opLabel(result.op), style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        result.name?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (result.saved) studyText("Копия сохранена на телефоне", "Copy saved on your phone")
                            else stringResource(R.string.studio_ready_cache_badge),
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                } else Surface(
                    shape = studyShape(16),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (result.op) {
                                    StudioOp.STICKER -> stringResource(R.string.studio_sticker_video)
                                    StudioOp.STICKER_STATIC -> stringResource(R.string.studio_sticker_static)
                                    else -> opLabel(result.op)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            val formattedBytes = fmtBytes(result.bytes)
                            if (formattedBytes.isNotEmpty()) {
                                Text(
                                    text = formattedBytes,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                )
                            }
                        }
                        result.name?.let { fileName ->
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (result.op == StudioOp.CLIP && sourceBytes > 0) {
                            Text(stringResource(R.string.compression_comparison, fmtBytes(sourceBytes), fmtBytes(result.bytes)),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Text(
                            text = stringResource(R.string.studio_ready_cache_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        // 3. Persistent Action Footer: Share (Hero), Save copy, Adjust
        Surface(
            color = cs.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Save Copy Error if any
                result.error?.let { err ->
                    Text(
                        text = err,
                        color = cs.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                    )
                }

                // Telegram Error if any
                if (telegramNotInstalled) {
                    Surface(
                        shape = studyShape(12),
                        color = cs.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.telegram_not_installed),
                            color = cs.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                telegramImportFailure?.let { errDetail ->
                    Surface(
                        shape = studyShape(12),
                        color = cs.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.telegram_import_failed, errDetail),
                            color = cs.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                // Phase 2 Saving Progress
                if (isSaving) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.studio_save_copy_saving),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (result.op == StudioOp.STICKER_STATIC) {
                    Text(
                        text = stringResource(R.string.studio_telegram_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )

                    // Hero CTA: "Добавить в Telegram"
                    Button(
                        onClick = {
                            telegramNotInstalled = false
                            telegramImportFailure = null
                            if (!app.snag.core.TelegramSticker.isTelegramInstalled(context)) {
                                telegramNotInstalled = true
                                return@Button
                            }
                            try {
                                val stickerIntent = app.snag.core.TelegramSticker.createImportIntent(context, readyUri)
                                runCatching {
                                    context.grantUriPermission(
                                        app.snag.core.TelegramSticker.TELEGRAM_PACKAGE,
                                        readyUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }
                                context.startActivity(stickerIntent)
                            } catch (e: Exception) {
                                telegramImportFailure = e.localizedMessage ?: e.javaClass.simpleName
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = studyShape(16),
                    ) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_add_to_telegram),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    // Secondary Actions: "Поделиться", "Сохранить копию", "Изменить"
                    if (largeText) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onShare(readyUri) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_share))
                            }
                            OutlinedButton(
                                onClick = onSaveCopy,
                                enabled = !result.saved && !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                if (result.saved) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_saved))
                                } else {
                                    Text(
                                        stringResource(
                                            if (isSaving) R.string.studio_save_copy_saving else R.string.save_copy
                                        )
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onAdjust,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.prepare_change))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onShare(readyUri) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_share))
                            }
                            OutlinedButton(
                                onClick = onSaveCopy,
                                enabled = !result.saved && !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                if (result.saved) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_saved))
                                } else {
                                    Text(
                                        stringResource(
                                            if (isSaving) R.string.studio_save_copy_saving else R.string.save_copy
                                        )
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onAdjust,
                            enabled = !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = studyShape(12),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.prepare_change))
                        }
                    }
                } else {
                    if (result.op == StudioOp.STICKER) {
                        Text(
                            text = stringResource(R.string.studio_video_sticker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }

                    // Primary Hero CTA: "Share"
                    Button(
                        onClick = { onShare(readyUri) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = studyShape(16),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_share),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    // Secondary Actions: "Save a copy" and "Adjust"
                    if (largeText) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onSaveCopy,
                                enabled = !result.saved && !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                if (result.saved) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_saved))
                                } else {
                                    Text(
                                        stringResource(
                                            if (isSaving) R.string.studio_save_copy_saving else R.string.save_copy
                                        )
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onAdjust,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.prepare_change))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Save copy button
                            OutlinedButton(
                                onClick = onSaveCopy,
                                enabled = !result.saved && !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                if (result.saved) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_saved))
                                } else {
                                    Text(
                                        stringResource(
                                            if (isSaving) R.string.studio_save_copy_saving else R.string.save_copy
                                        )
                                    )
                                }
                            }

                            // Adjust button
                            OutlinedButton(
                                onClick = onAdjust,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = studyShape(12),
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.prepare_change))
                            }
                        }
                    }
                }
            }
        }
    }
}
