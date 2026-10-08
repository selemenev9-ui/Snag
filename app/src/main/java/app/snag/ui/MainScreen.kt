package app.snag.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.snag.R
import app.snag.core.Downloader
import app.snag.core.Job
import app.snag.core.JobStore
import app.snag.core.JobStatus
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpenResult: (android.net.Uri) -> Unit,
    onShareResult: (android.net.Uri) -> Unit,
    onStartDownload: (Job, Downloader.Format, Boolean, Boolean) -> Unit,
    onPickVideo: () -> Unit,
    onEnsureNotifications: () -> Unit = {},
) {
    val jobs by vm.jobs.collectAsState()
    val clipUrl by vm.clipboardUrl.collectAsState()
    val squashTarget by vm.squashTarget.collectAsState()
    val studioJob by vm.studioJob.collectAsState()
    val studioResult by vm.studioResult.collectAsState()
    val playerJob by vm.playerJob.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val playlistOffer by vm.playlistOffer.collectAsState()
    val playlistLoading by vm.playlistLoading.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val undoJob by vm.undoJob.collectAsState()
    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }

    val removedMsg = stringResource(R.string.snack_removed)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undoJob) {
        if (undoJob != null) {
            val res = snackbarHost.showSnackbar(
                message = removedMsg,
                actionLabel = undoLabel,
                // With an action, Material defaults to Indefinite. Keep undo transient.
                duration = androidx.compose.material3.SnackbarDuration.Long,
                withDismissAction = true,
            )
            if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                vm.undoRemove()
            } else {
                vm.consumeUndo()
            }
        }
    }

    val activeJobs = jobs.filter {
        it.status in setOf(JobStatus.RESOLVING, JobStatus.READY, JobStatus.QUEUED,
            JobStatus.DOWNLOADING, JobStatus.COMPRESSING, JobStatus.FAILED)
    }
    val pastJobs = jobs.filter { it !in activeJobs }
    val listState = rememberLazyListState()
    LaunchedEffect(activeJobs.firstOrNull()?.id) {
        if (activeJobs.isNotEmpty()) listState.scrollToItem(0)
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val submit = {
        if (input.isNotBlank()) {
            vm.submitText(input)
            input = ""
            focus.clearFocus()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SnagMark(Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (DesignStudy.paper) "snag" else if (DesignStudy.active) "snag / ${DesignStudy.label}" else "Snag", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Filled.History, stringResource(R.string.section_history))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings_title))
                    }
                }
            }
            item { AppUpdateCard(home = true) }
            if (DesignStudy.active) {
                item { StudyHome(onPickVideo) }
            }
            if (activeJobs.isEmpty() && !DesignStudy.active) {
                item {
                    Column(Modifier.fillMaxWidth().clip(studyShape(28))
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(24.dp)) {
                        Text(stringResource(R.string.home_eyebrow), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.hero_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(22.dp))
                        MomentSignature(Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = studyShape(20),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            TextField(value = input, onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.input_hint)) },
                                leadingIcon = { Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { submit() }))
                            if (input.isNotEmpty()) {
                                IconButton(onClick = { input = "" }) {
                                    Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_input))
                                }
                                FilledIconButton(onClick = { submit() }, enabled = input.isNotBlank()) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward,
                                        stringResource(R.string.action_continue))
                                }
                            } else {
                                IconButton(onClick = {
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    input = cm.primaryClip?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                }) {
                                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste_from_clipboard),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    if (clipUrl != null) {
                        Button(onClick = {
                            clipUrl?.let { vm.submitLink(it); vm.clearClipboardHint(); focus.clearFocus() }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = studyShape(16)) {
                            Icon(Icons.Filled.ContentPaste, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.paste_from_clipboard))
                        }
                    }
                    if (!DesignStudy.active) FilledTonalButton(onClick = onPickVideo,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = studyShape(16)) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_compress_gallery))
                    }
                }
            }
            val hasActivePrep = studioJob == null && (studioResult.running || studioResult.cancelling)
            if (jobs.isEmpty() && !hasActivePrep && !DesignStudy.active) item { EmptyState() }
            if (activeJobs.isNotEmpty() || hasActivePrep) {
                val totalActive = activeJobs.size + if (hasActivePrep) 1 else 0
                item { SectionHeading(stringResource(R.string.section_now), totalActive) }
                if (hasActivePrep) {
                    item(key = "active_prep") {
                        ActivePreparationCard(
                            vm = vm,
                            state = studioResult,
                            onOpenStudio = { vm.ensureStudioOpenedForActivePrep() },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(activeJobs, key = { it.id }) { job ->
                    JobSwipeCard(vm, job, onStartDownload, onOpenResult, onShareResult,
                        Modifier.animateItem())
                }
            }
            if (pastJobs.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.section_recent),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { showHistory = true }) {
                            Text(stringResource(R.string.action_all_history))
                        }
                    }
                }
                items(pastJobs.take(3), key = { it.id }) { job ->
                    JobSwipeCard(vm, job, onStartDownload, onOpenResult, onShareResult,
                        Modifier.animateItem(), compact = true)
                }
            }
        }
    }

    if (showSettings) SettingsSheet(onDismiss = { showSettings = false })
    squashTarget?.let { job ->
        SquashSheet(
            job = job,
            onPick = { target -> vm.squash(job, target) },
            onDismiss = { vm.openSquash(null) },
        )
    }

    playerJob?.let { job ->
        PlayerDialog(job = job, onDismiss = { vm.closePlayer() })
    }

    studioJob?.let { job ->
        StudioDialog(
            vm = vm,
            job = job,
            onShare = onShareResult,
            onDismiss = { vm.closeStudio() },
            onEnsureNotifications = onEnsureNotifications,
        )
    }

    if (searching || searchResults != null) {
        SearchSheet(
            hits = searchResults,
            loading = searching,
            onPick = { hit ->
                vm.dismissSearch()
                vm.submitLink(hit.url)
            },
            onDismiss = { vm.dismissSearch() },
        )
    }

    if (playlistLoading || playlistOffer != null) {
        PlaylistSheet(
            hits = playlistOffer,
            loading = playlistLoading,
            onDownloadAll = { vm.downloadPlaylist(it) },
            onDismiss = { vm.dismissPlaylist() },
        )
    }

    if (showHistory) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showHistory = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ) {
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                var query by remember { mutableStateOf("") }
                val filtered = pastJobs.filter {
                    query.isBlank() ||
                        it.title.contains(query, true) ||
                        it.site.contains(query, true) ||
                        it.publishedName?.contains(query, true) == true
                }
                Column(Modifier.safeDrawingPadding().imePadding()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.section_history),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (pastJobs.isNotEmpty()) {
                            IconButton(onClick = {
                                pastJobs.forEach { vm.removeJob(it.id) }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    stringResource(R.string.action_clear_history),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(onClick = { showHistory = false }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_dismiss))
                        }
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        placeholder = {
                            Text(stringResource(R.string.history_search_hint))
                        },
                        singleLine = true,
                        shape = studyShape(24),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search, null,
                                Modifier.size(20.dp),
                            )
                        },
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filtered, key = { "h-" + it.id }) { job ->
                            JobSwipeCard(
                                vm, job, onStartDownload, onOpenResult, onShareResult,
                                Modifier.animateItem(), compact = true,
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

/** Swipe-to-dismiss wrapper around a job card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobSwipeCard(
    vm: MainViewModel,
    job: Job,
    onStartDownload: (Job, Downloader.Format, Boolean, Boolean) -> Unit,
    onOpenResult: (android.net.Uri) -> Unit,
    onShareResult: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            v != SwipeToDismissBoxValue.EndToStart ||
                (job.status != JobStatus.DOWNLOADING &&
                    job.status != JobStatus.QUEUED &&
                    job.status != JobStatus.COMPRESSING)
        },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptic.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
            vm.removeJob(job.id)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val reveal = if (
                dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            ) 1f else 0f
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(studyShape(32))
                    .background(
                        MaterialTheme.colorScheme.errorContainer
                            .copy(alpha = reveal),
                    )
                    .padding(end = 28.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete, null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                        .copy(alpha = reveal),
                )
            }
        },
    ) {
        JobCard(
            job = job,
            onDownload = { fmt -> onStartDownload(job, fmt, false, false) },
            onQuickShare = { fmt -> onStartDownload(job, fmt, true, false) },
            onPrepare = { fmt -> onStartDownload(job, fmt, true, true) },
            onCancel = { vm.cancelJob(job.id) },
            onOpen = { vm.openPlayer(job) },
            onShare = { job.publishedUri?.let(onShareResult) },
            onSelectQuality = { choice -> JobStore.update(job.id) {
                if (it.status == JobStatus.READY) it.copy(selectedQuality = choice) else it
            } },
            onSaveCopy = { vm.saveResultCopy(job) },
            onSquash = { vm.openStudio(job) },
            onStudio = { vm.openStudio(job) },
            onDismiss = { vm.removeJob(job.id) },
            onRetry = { vm.retry(job) },
            compact = compact,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun JobCard(
    job: Job,
    onDownload: (Downloader.Format) -> Unit,
    onQuickShare: (Downloader.Format) -> Unit,
    onPrepare: (Downloader.Format) -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSelectQuality: (app.snag.core.DownloadQuality?) -> Unit,
    onSaveCopy: () -> Unit,
    onSquash: () -> Unit,
    onStudio: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var expanded by rememberSaveable(job.id) { mutableStateOf(false) }
    var selectedName by rememberSaveable(job.id) { mutableStateOf(Downloader.Format.AUTO.name) }
    val selectedFormat = Downloader.Format.valueOf(selectedName)
    val cs = MaterialTheme.colorScheme
    val busy = job.status in setOf(JobStatus.RESOLVING, JobStatus.QUEUED,
        JobStatus.DOWNLOADING, JobStatus.COMPRESSING)
    val playable = job.status == JobStatus.DONE && job.publishedUri != null
    val isVideo = !job.publishedName.orEmpty().endsWith(".mp3", ignoreCase = true)
    val metadata = listOfNotNull(job.site.takeIf { it.isNotBlank() },
        fmtDur(job.durationSec).takeIf { job.durationSec > 0 },
        fmtBytes(job.publishedBytes).takeIf { job.publishedBytes > 0 }).joinToString(" · ")
    Card(modifier = modifier.animateContentSize(),
        shape = studyShape(24),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f))) {
        Column {
            if (!compact && !job.thumbnail.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().height(172.dp).background(cs.surfaceContainer)) {
                    AsyncImage(model = job.thumbnail, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (playable) {
                        FilledIconButton(onClick = onOpen,
                            modifier = Modifier.align(Alignment.Center).size(56.dp)) {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.action_open))
                        }
                    }
                }
            }
            Column(Modifier.padding(if (compact) 14.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (compact) {
                        Surface(onClick = { if (playable) onOpen() else expanded = !expanded },
                            shape = studyShape(14), color = cs.surfaceContainerHigh,
                            modifier = Modifier.size(64.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(model = job.thumbnail, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (playable) {
                                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.65f)) {
                                        Icon(Icons.Filled.PlayArrow, stringResource(R.string.action_open),
                                            tint = Color.White, modifier = Modifier.padding(6.dp).size(20.dp))
                                    }
                                } else if (job.thumbnail.isNullOrBlank()) {
                                    Icon(Icons.Filled.MusicNote, null, tint = cs.primary)
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).then(if (compact) Modifier.clickable {
                        expanded = !expanded
                    } else Modifier)) {
                        Text(job.title.ifBlank { job.url }, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (metadata.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(metadata, style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant)
                        }
                    }
                    if (compact) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                stringResource(if (expanded) R.string.action_collapse else R.string.action_details))
                        }
                    } else if (!busy) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_dismiss), tint = cs.onSurfaceVariant)
                        }
                    }
                }
                AnimatedVisibility(!compact || expanded || job.status == JobStatus.DONE) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        when (job.status) {
                            JobStatus.RESOLVING -> {
                                SnagProgress(indeterminate = true)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.state_resolving),
                                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                            }
                            JobStatus.READY -> {
                                Text(stringResource(R.string.quality_label), style = MaterialTheme.typography.labelLarge,
                                    color = cs.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = selectedFormat == Downloader.Format.AUTO && job.selectedQuality == null,
                                        onClick = { selectedName = Downloader.Format.AUTO.name; onSelectQuality(null) },
                                        label = { Text(fmtLabel(Downloader.Format.AUTO)) })
                                    job.downloadQualities.forEach { choice ->
                                        FilterChip(selected = selectedFormat == Downloader.Format.AUTO && job.selectedQuality == choice,
                                            onClick = { selectedName = Downloader.Format.AUTO.name; onSelectQuality(choice) },
                                            label = { Text(choice.label) })
                                    }
                                    if (job.audioAvailable) {
                                        FilterChip(selected = selectedFormat == Downloader.Format.AUDIO,
                                            onClick = { selectedName = Downloader.Format.AUDIO.name; onSelectQuality(null) },
                                            label = { Text("MP3") })
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    if (selectedFormat == Downloader.Format.AUDIO) onQuickShare(selectedFormat)
                                    else onPrepare(selectedFormat)
                                },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    shape = studyShape(16)) {
                                    Icon(if (selectedFormat == Downloader.Format.AUDIO) Icons.Filled.Share else Icons.Filled.ContentCut,
                                        null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(if (selectedFormat == Downloader.Format.AUDIO) R.string.action_prepare_share else R.string.action_studio))
                                }
                                Text(stringResource(if (selectedFormat == Downloader.Format.AUDIO) R.string.share_cache_hint else R.string.edit_download_hint),
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp))
                                if (selectedFormat != Downloader.Format.AUDIO) {
                                    OutlinedButton(onClick = { onQuickShare(selectedFormat) },
                                        modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.share_entire_video))
                                    }
                                    Text(stringResource(R.string.edit_download_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant)
                                }
                                TextButton(onClick = { onDownload(selectedFormat) },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.action_save_device))
                                }
                            }
                            JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.COMPRESSING -> {
                                if (job.status != JobStatus.QUEUED) {
                                    SnagProgress(progress = if (job.progress >= 0) job.progress / 100f else null)
                                    Spacer(Modifier.height(8.dp))
                                }
                                Text(when (job.status) {
                                    JobStatus.QUEUED -> stringResource(R.string.state_queued)
                                    JobStatus.COMPRESSING -> stringResource(R.string.state_squashing)
                                    else -> if (job.etaSec > 0) stringResource(R.string.state_downloading_eta,
                                        job.progress.toInt().coerceAtLeast(0), job.etaSec)
                                    else stringResource(R.string.state_downloading, job.progress.toInt().coerceAtLeast(0))
                                }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                            }
                            JobStatus.DONE -> {
                                job.error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }
                                Text(stringResource(if (job.ephemeral) R.string.result_temporary else R.string.result_available),
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                if (job.ephemeral) {
                                    OutlinedButton(onClick = onSaveCopy, enabled = !job.savingCopy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text(stringResource(if (job.savingCopy) R.string.studio_save_copy_saving else R.string.save_copy))
                                    }
                                } else {
                                    Text(stringResource(R.string.result_saved_location),
                                        style = MaterialTheme.typography.bodySmall, color = cs.primary)
                                }
                                Button(onClick = onShare, enabled = job.publishedUri != null,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = studyShape(14)) {
                                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_share))
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (playable && isVideo) {
                                        TextButton(onClick = onStudio) {
                                            Icon(Icons.Filled.ContentCut, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_open_studio))
                                        }
                                        TextButton(onClick = onSquash) { Text(stringResource(R.string.action_squash)) }
                                    }
                                    if (compact) TextButton(onClick = onDismiss) {
                                        Text(stringResource(R.string.action_dismiss), color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                            JobStatus.FAILED -> {
                                Text(job.error ?: stringResource(R.string.state_failed),
                                    color = cs.error, style = MaterialTheme.typography.bodyMedium)
                                if (job.url.startsWith("http")) {
                                    OutlinedButton(onClick = onRetry) {
                                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.action_retry))
                                    }
                                }
                            }
                            JobStatus.CANCELLED -> {
                                Text(stringResource(R.string.state_cancelled), color = cs.onSurfaceVariant)
                                if (compact) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(count.toString(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The selected-moment brackets are the small, static brand signature. */
@Composable
private fun SnagMark(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
            Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke, StrokeCap.Round)
        line(.32f, .16f, .12f, .16f); line(.12f, .16f, .12f, .84f); line(.12f, .84f, .32f, .84f)
        line(.68f, .16f, .88f, .16f); line(.88f, .16f, .88f, .84f); line(.88f, .84f, .68f, .84f)
        drawCircle(color, radius = w * .075f, center = Offset(w * .5f, h * .5f))
    }
}

/** Expressive wavy progress (M3 1.5 expressive kit). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SnagProgress(progress: Float? = null, indeterminate: Boolean = false) {
    if (progress != null) {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** A selected slice of a waveform is Snag's recurring visual signature. */
@Composable
private fun MomentSignature(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier) {
        val spacing = size.width / 35f
        repeat(35) { i ->
            val selected = i in 10..24
            val amplitude = (0.18f + ((i * 7) % 11) / 15f) * size.height
            val x = (i + .5f) * spacing
            drawLine(ink.copy(alpha = if (selected) .9f else .25f),
                Offset(x, (size.height - amplitude) / 2f), Offset(x, (size.height + amplitude) / 2f),
                spacing * .36f, StrokeCap.Round)
        }
        for (x in listOf(spacing * 9.6f, spacing * 25.4f)) {
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SquashSheet(
    job: Job,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.squash_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.squash_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            SquashOption(
                title = stringResource(R.string.squash_lite),
                detail = stringResource(R.string.squash_lite_hint),
            ) { onPick(null) }
            SquashOption(title = stringResource(R.string.squash_opt_8), detail = "Telegram") { onPick(8L * 1024 * 1024) }
            SquashOption(title = stringResource(R.string.squash_opt_25), detail = "Discord / WhatsApp") { onPick(25L * 1024 * 1024) }
            SquashOption(title = stringResource(R.string.squash_opt_50), detail = stringResource(R.string.squash_50_hint)) { onPick(50L * 1024 * 1024) }
            CustomSizeRow(onPick)
        }
    }
}

/** Free-form size target: "12" or "12.5" MB → onPick(bytes). */
@Composable
private fun CustomSizeRow(onPick: (Long?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = text.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed > 0.05f && parsed < 4096f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.squash_custom_hint)) },
            singleLine = true,
            suffix = { Text(stringResource(R.string.unit_mb)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            shape = studyShape(20),
        )
        androidx.compose.material3.Button(
            onClick = { onPick(((parsed ?: 0f) * 1024 * 1024).toLong()) },
            enabled = valid,
            shape = CircleShape,
        ) {
            Text(stringResource(R.string.action_go))
        }
    }
}

@Composable
private fun SquashOption(title: String, detail: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = studyShape(20),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Onboarding card shown while there are no jobs yet. */
@Composable
private fun EmptyState() {
    Card(
        shape = studyShape(32),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Icons.Filled.Link,
                    Icons.Filled.ContentCut,
                    Icons.Filled.Gif,
                    Icons.Filled.MusicNote,
                ).forEach { ic ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Icon(
                            ic, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp).size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun fmtLabel(fmt: Downloader.Format): String = when (fmt) {
    Downloader.Format.AUTO -> stringResource(R.string.quality_max)
    Downloader.Format.P1080 -> "1080p"
    Downloader.Format.P720 -> "720p"
    Downloader.Format.SMALL -> "SD"
    Downloader.Format.AUDIO -> "MP3"
}

private fun fmtDur(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

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

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** YouTube search results — tap a row to stage it as a normal job. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchSheet(
    hits: List<Downloader.SearchHit>?,
    loading: Boolean,
    onPick: (Downloader.SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.search_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> LinearWavyProgressIndicator(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
                hits.isNullOrEmpty() -> Text(
                    stringResource(R.string.search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(hits, key = { it.url }) { hit ->
                        Surface(
                            onClick = { onPick(hit) },
                            shape = studyShape(20),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AsyncImage(
                                    model = hit.thumb,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(96.dp, 54.dp)
                                        .clip(studyShape(12)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        hit.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(
                                            hit.uploader.takeIf { it.isNotBlank() },
                                            fmtMs(hit.durationSec * 1000)
                                                .takeIf { hit.durationSec > 0 },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Playlist detected — offer to queue every entry at once. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistSheet(
    hits: List<Downloader.SearchHit>?,
    loading: Boolean,
    onDownloadAll: (List<Downloader.SearchHit>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.playlist_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> LinearWavyProgressIndicator(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
                hits.isNullOrEmpty() -> Text(
                    stringResource(R.string.search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> {
                    Text(
                        stringResource(R.string.playlist_count, hits.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(260.dp),
                    ) {
                        items(hits, key = { it.url }) { hit ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(
                                    model = hit.thumb,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp, 36.dp)
                                        .clip(studyShape(8)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Text(
                                    hit.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style
                                        .TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    fmtMs(hit.durationSec * 1000),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.Button(
                        onClick = { onDownloadAll(hits) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.playlist_download_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePreparationCard(
    vm: MainViewModel,
    state: StudioResult,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val req = remember(state.workId) { app.snag.core.PreparationStore.currentRequest() }
    val title = req?.title?.ifBlank { null } ?: stringResource(R.string.notif_prepare_title)
    val op = req?.op ?: state.op
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = studyShape(20),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val subtitle = when {
                        state.cancelling -> stringResource(R.string.state_cancelled)
                        state.phase == 2 -> stringResource(R.string.prepare_saving)
                        else -> "${opLabel(op)} · ${state.pct}%"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onOpenStudio,
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.action_open))
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.pct / 100f },
                modifier = Modifier.fillMaxWidth().clip(studyShape(4)),
            )
            if (!state.cancelling && state.phase != 2) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.cancelStudioExport() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}
