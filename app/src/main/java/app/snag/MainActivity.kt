package app.snag

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.snag.core.CompletionAction
import app.snag.core.JobStatus
import app.snag.core.JobStore
import app.snag.core.LinkParse
import app.snag.ui.MainScreen
import app.snag.ui.MainViewModel
import app.snag.ui.SnagTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(app.snag.core.AppPreferences.localized(base))
    }

    companion object {
        const val ACTION_AUTOPASTE = "app.snag.AUTOPASTE"
    }

    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.addLocalVideo(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A theme/font change recreates the Activity, but keeps its ViewModel.
        // Consume the incoming share once instead of creating a duplicate job.
        if (savedInstanceState == null || intent?.action == app.snag.core.SnagService.ACTION_OPEN_STUDIO) {
            handleIntent(intent)
        }
        if (savedInstanceState?.getBoolean("studio_open") == true) {
            vm.ensureStudioOpenedForActivePrep()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                JobStore.jobs.collect { list ->
                    list.filter { it.status == JobStatus.DONE && it.publishedUri != null &&
                        it.completionAction != CompletionAction.NONE }.forEach { job ->
                        when (vm.consumeCompletion(job)) {
                            CompletionAction.SHARE -> shareResult(job.publishedUri!!)
                            CompletionAction.EDIT -> vm.openStudio(job)
                            CompletionAction.NONE -> Unit
                        }
                    }
                }
            }
        }

        setContent {
            SnagTheme {
                val lightBars = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(
                        vm = vm,
                        onOpenResult = ::openResult,
                        onShareResult = ::shareResult,
                        onStartDownload = { job, fmt, ephemeral, edit ->
                            ensureNotifications()
                            vm.startDownload(job.id, fmt, ephemeral, edit)
                        },
                        onPickVideo = {
                            ensureNotifications()
                            pickVideo.launch(arrayOf("video/*"))
                        },
                        onEnsureNotifications = ::ensureNotifications,
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("studio_open", vm.studioJob.value != null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        app.snag.core.Downloader.checkForUpdates(this)
        app.snag.core.BetaUpdates.checkDaily(this)
        // Clipboard suggest: if the clipboard holds a link, offer a paste chip.
        runCatching {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString()
            vm.onClipboardText(clip)
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            app.snag.core.SnagService.ACTION_OPEN_STUDIO -> {
                vm.ensureStudioOpenedForActivePrep()
            }
            ACTION_AUTOPASTE -> {
                // Quick tile: pull the clipboard and route it through submitText.
                runCatching {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(this)?.toString()
                    vm.submitText(clip ?: return)
                }
            }
            Intent.ACTION_SEND -> {
                // Two payload kinds: a text link, or a video/image stream.
                val stream: Uri? = androidx.core.content.IntentCompat
                    .getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (stream != null) {
                    ensureNotifications()
                    vm.addLocalVideo(stream)
                    return
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: intent.dataString
                vm.submitText(text ?: return)
            }
            Intent.ACTION_VIEW -> {
                intent.data?.takeIf { it.scheme == "http" || it.scheme == "https" }
                    ?.let { vm.submitLink(it.toString()) }
            }
        }
    }

    fun ensureNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openResult(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri) ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(view) }
    }

    private fun shareResult(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "Snag", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
        }
    }
}
