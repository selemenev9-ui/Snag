package app.snag.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.snag.R
import app.snag.core.*

@Composable fun AppUpdateCard(home: Boolean=false) {
    val context=LocalContext.current
    val update by BetaUpdates.state.collectAsState()
    val download by AppUpdateDownload.state.collectAsState()
    val prep by PreparationStore.state.collectAsState()
    val jobs by JobStore.jobs.collectAsState()
    val busy=prep.running || jobs.any { it.status in setOf(JobStatus.RESOLVING,JobStatus.QUEUED,JobStatus.DOWNLOADING,JobStatus.COMPRESSING) || it.savingCopy }
    val prefs=remember { context.getSharedPreferences("apk_update_banner",android.content.Context.MODE_PRIVATE) }
    var dismissed by remember { mutableIntStateOf(prefs.getInt("dismissed",0)) }
    if(update.url==null && !download.downloading && !download.ready && !download.failed) return
    if(home && dismissed==update.code && !download.downloading && !download.ready && !download.failed) return
    LaunchedEffect(Unit) { AppUpdateDownload.resume(context) }
    Card(Modifier.fillMaxWidth(),shape=studyShape(20)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_available),style=MaterialTheme.typography.titleMedium)
            Text(download.version.ifBlank { update.version.orEmpty() })
            when {
                download.downloading -> {
                    Text(stringResource(R.string.update_progress,download.progress))
                    LinearProgressIndicator(progress={download.progress/100f},modifier=Modifier.fillMaxWidth())
                    TextButton(onClick={ AppUpdateDownload.cancel(context) }) { Text(stringResource(R.string.update_cancel)) }
                }
                download.ready -> {
                    Text(stringResource(if(busy) R.string.update_wait_media else R.string.update_ready))
                    Button(enabled=!busy,onClick={ AppUpdateDownload.install(context) }) { Text(stringResource(R.string.update_install)) }
                }
                else -> {
                    if(download.failed) Text(stringResource(R.string.update_download_failed),color=MaterialTheme.colorScheme.error)
                    Button(onClick={ AppUpdateDownload.start(context) }) {
                        Text(stringResource(if(download.failed) R.string.update_retry else R.string.update_download_native))
                    }
                    if(home) TextButton(onClick={ dismissed=update.code; prefs.edit().putInt("dismissed",dismissed).apply() }) {
                        Text(stringResource(R.string.update_later))
                    }
                }
            }
        }
    }
}
