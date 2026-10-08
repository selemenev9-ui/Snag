package app.snag.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstallState(val working: Boolean=false, val confirmation: Intent?=null, val failed: Boolean=false, val done: Boolean=false)
object UpdateInstaller {
    private val mutable=MutableStateFlow(InstallState())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Synchronized fun begin(context: Context) {
        if(mutable.value.working) return
        val c=context.applicationContext
        mutable.value=InstallState(working=true)
        scope.launch {
            var sessionId: Int?=null
            val installer=c.packageManager.packageInstaller
            try {
                check(!AppUpdateDownload.mediaBusy())
                val apk=AppUpdateDownload.verifiedFile(c)
                val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(c.packageName); setSize(apk.length())
                    if(Build.VERSION.SDK_INT>=31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id=installer.createSession(params); sessionId=id
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk",0,apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output) }; session.fsync(output)
                    }
                    check(!AppUpdateDownload.mediaBusy())
                    val callback=Intent(c,UpdateInstallReceiver::class.java).setAction("${c.packageName}.UPDATE_RESULT")
                    val flags=PendingIntent.FLAG_UPDATE_CURRENT or
                        (if(Build.VERSION.SDK_INT>=31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(c,id,callback,flags).intentSender)
                }
            } catch(_: Exception) {
                sessionId?.let { runCatching { installer.abandonSession(it) } }
                mutable.value=InstallState(failed=true)
            }
        }
    }
    @Suppress("DEPRECATION")
    fun result(intent: Intent) {
        mutable.value=when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if(confirmation==null) InstallState(failed=true) else InstallState(working=true,confirmation=confirmation)
            }
            PackageInstaller.STATUS_SUCCESS -> InstallState(done=true)
            else -> InstallState(failed=true)
        }
    }
    fun confirmationOpened() { mutable.value=mutable.value.copy(confirmation=null) }
    fun confirmationFailed() { mutable.value=InstallState(failed=true) }
}
class UpdateInstallReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) { UpdateInstaller.result(intent) }
}
