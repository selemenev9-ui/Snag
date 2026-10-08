package app.snag.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.snag.R
import app.snag.core.UpdateInstaller

/** Visible owner of the permission/confirmation flow. No background activity launches. */
class UpdateInstallActivity: ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(app.snag.core.AppPreferences.localized(newBase))
    }
    private var allowed by mutableStateOf(false)
    private var requested=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requested=savedInstanceState?.getBoolean("requested") ?: false
        setContent { SnagTheme {
            val state by UpdateInstaller.state.collectAsState()
            LaunchedEffect(state.confirmation) {
                state.confirmation?.let {
                    try { startActivity(it); UpdateInstaller.confirmationOpened() }
                    catch(_: Exception) { UpdateInstaller.confirmationFailed() }
                }
            }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),verticalArrangement=Arrangement.Center) {
                    Text(stringResource(R.string.update_available),style=MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    if(!allowed) {
                        Text(stringResource(R.string.update_permission_hint))
                        Button(onClick={ runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName"))) } }) {
                            Text(stringResource(R.string.update_allow))
                        }
                    } else if(state.failed) {
                        Text(stringResource(R.string.update_install_failed))
                        Button(onClick={ UpdateInstaller.begin(this@UpdateInstallActivity) }) { Text(stringResource(R.string.update_retry)) }
                    } else if(state.done) {
                        Text(stringResource(R.string.update_current))
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.update_installing))
                    }
                    TextButton(onClick={ finish() }) { Text(stringResource(R.string.update_close)) }
                }
            }
        } }
    }
    override fun onResume() {
        super.onResume()
        allowed=packageManager.canRequestPackageInstalls()
        if(allowed && !requested) { requested=true; UpdateInstaller.begin(this) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested",requested); super.onSaveInstanceState(outState)
    }
}
