package app.snag.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.snag.BuildConfig
import app.snag.R
import app.snag.core.AppPreferences
import app.snag.core.BetaUpdates
import app.snag.core.BetaUpdatePolicy

private tailrec fun Context.activity(): Activity? = when(this) {
    is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val theme by AppPreferences.theme.collectAsState()
    val update by BetaUpdates.state.collectAsState()
    val language = AppPreferences.language(context)
    var launchFailed by remember { mutableStateOf(false) }
    val open = { url: String ->
        launchFailed = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isFailure
    }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp)
            .padding(bottom=32.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_title),style=MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_language),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("system" to stringResource(R.string.settings_system), "en" to "English", "ru" to "Русский").forEach { (tag,label) ->
                    FilterChip(selected=language==tag,onClick={ context.activity()?.let { AppPreferences.setLanguage(it,tag) } },
                        label={Text(label)},modifier=Modifier.heightIn(min=48.dp))
                }
            }
            Text(stringResource(R.string.settings_theme),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("system" to R.string.settings_system,"light" to R.string.settings_light,"dark" to R.string.settings_dark).forEach { (tag,res) ->
                    FilterChip(selected=theme==tag,onClick={ AppPreferences.setTheme(context,tag) },
                        label={Text(stringResource(res))},modifier=Modifier.heightIn(min=48.dp))
                }
            }
            HorizontalDivider()
            Text("Snag ${if(DesignStudy.paper) "Paper" else "Classic"} · ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_beta_note),style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(when { update.checking -> R.string.update_checking
                update.failed -> R.string.update_failed; update.url != null -> R.string.update_available
                update.checked -> R.string.update_current; else -> R.string.update_hint }),style=MaterialTheme.typography.bodyMedium)
            update.version?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
            Button(onClick={ BetaUpdates.check(context) },enabled=!update.checking,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                shape=studyShape(16)) { Text(stringResource(R.string.update_check)) }
            if(update.url!=null) OutlinedButton(onClick={ open(update.url!!) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                shape=studyShape(16)) { Text(stringResource(R.string.update_download)) }
            Text(stringResource(R.string.update_install_hint),style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={ open(BetaUpdatePolicy.REPO + "/releases") },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.settings_releases))
            }
            TextButton(onClick={ open(BetaUpdatePolicy.REPO + "/issues/new/choose") },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.settings_feedback))
            }
            if(launchFailed) Text(stringResource(R.string.settings_open_failed),color=MaterialTheme.colorScheme.error)
        }
    }
}
