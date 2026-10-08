package app.snag.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.snag.R
import app.snag.core.PreparationState

/** One focused processing state; settings remain intact behind it. */
@Composable
fun StudioProgressContent(
    state: PreparationState,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
    onSharePrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(shape = studyShape(28), color = cs.primaryContainer) {
                Icon(Icons.Filled.ContentCut, null, Modifier.padding(24.dp).size(40.dp), tint = cs.onPrimaryContainer)
            }
            Text(stringResource(if (state.cancelling) R.string.prepare_cancelling else R.string.processing_title),
                style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.processing_background_hint), style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant)
            Surface(shape = studyShape(24), color = cs.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (state.phase == 1) R.string.processing_compressing else R.string.processing_rendering),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!state.cancelling && state.pct > 0) Text("${state.pct}%", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = cs.primary)
                    }
                    if (state.pct == 0 || state.cancelling) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { state.pct.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (state.uri != null) {
                OutlinedButton(onClick = onSharePrevious, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_previous_result))
                }
            }
        }
        Surface(color = cs.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackground, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = studyShape(18)) {
                    Text(stringResource(R.string.processing_background_action))
                }
                TextButton(onClick = onCancel, enabled = !state.cancelling, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_cancel), color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
