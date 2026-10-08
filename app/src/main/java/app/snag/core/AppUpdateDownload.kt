package app.snag.core

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.snag.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppDownloadState(val downloading: Boolean=false, val progress: Int=0,
    val ready: Boolean=false, val failed: Boolean=false, val version: String="")

/** DownloadManager owns background transfers; only this edition's owned download is touched. */
object AppUpdateDownload {
    private val mutable=MutableStateFlow(AppDownloadState())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var poll: kotlinx.coroutines.Job?=null
    private fun prefs(c: Context)=c.getSharedPreferences("apk_update",Context.MODE_PRIVATE)
    private fun file(c: Context)=File(c.getExternalFilesDir("updates"),"update.apk")
    fun start(context: Context) {
        val c=context.applicationContext
        val u=BetaUpdates.state.value
        if(mutable.value.downloading || u.url==null) return
        if(!u.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { BetaUpdates.check(c); return }
        if(!BetaUpdatePolicy.accepted(BuildConfig.VERSION_CODE,u.code,BuildConfig.SNAG_DESIGN,u.url)) return
        cancel(c)
        try {
            val request=DownloadManager.Request(Uri.parse(u.url))
                .setTitle("Snag ${u.version}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file(c)))
            val id=c.getSystemService(DownloadManager::class.java).enqueue(request)
            prefs(c).edit().putLong("id",id).putInt("code",u.code).putString("sha",u.sha256)
                .putString("version",u.version).commit()
            mutable.value=AppDownloadState(downloading=true,version=u.version.orEmpty())
            resume(c)
        } catch(_: Exception) { mutable.value=AppDownloadState(failed=true) }
    }
    fun cancel(c: Context) {
        poll?.cancel(); poll=null
        val id=prefs(c).getLong("id",-1)
        if(id>=0) c.getSystemService(DownloadManager::class.java).remove(id)
        file(c).delete()
        prefs(c).edit().clear().commit()
        mutable.value=AppDownloadState()
    }
    fun resume(context: Context) {
        val c=context.applicationContext
        if(poll?.isActive==true) return
        val id=prefs(c).getLong("id",-1)
        if(id<0) return
        if(prefs(c).getInt("code",0)<=BuildConfig.VERSION_CODE) { cancel(c); return }
        poll=scope.launch {
            val version=prefs(c).getString("version","").orEmpty()
            try {
                while(isActive) {
                    val dm=c.getSystemService(DownloadManager::class.java)
                    var status=DownloadManager.STATUS_FAILED
                    var progress=0
                    dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        if(cursor.moveToFirst()) {
                            status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val bytes=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            require(bytes<=400L*1024*1024)
                            progress=if(total>0) ((bytes*100/total).toInt()).coerceIn(0,100) else 0
                        }
                    }
                    when(status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            verifiedFile(c)
                            ensureActive()
                            mutable.value=AppDownloadState(ready=true,progress=100,version=version)
                            break
                        }
                        DownloadManager.STATUS_FAILED -> error("Download failed")
                        else -> mutable.value=AppDownloadState(downloading=true,progress=progress,version=version)
                    }
                    delay(1000)
                }
            } catch(e: CancellationException) { throw e }
              catch(_: Exception) { mutable.value=AppDownloadState(failed=true,version=version) }
        }
    }
    @Suppress("DEPRECATION")
    fun verifiedFile(c: Context): File {
        val f=file(c)
        require(f.length() in 1..400L*1024*1024)
        val digest=MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val buf=ByteArray(65536); while(true) {
            val n=input.read(buf); if(n<0) break; digest.update(buf,0,n)
        } }
        val hash=digest.digest().joinToString("") { "%02x".format(it) }
        require(hash.equals(prefs(c).getString("sha",null),ignoreCase=true))
        val pm=c.packageManager
        val archive=requireNotNull(pm.getPackageArchiveInfo(f.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES))
        val installed=pm.getPackageInfo(c.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        require(archive.packageName==c.packageName)
        require(archive.longVersionCode==prefs(c).getInt("code",0).toLong() && archive.longVersionCode>installed.longVersionCode)
        val expected=installed.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet()
        require(archive.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet()==expected)
        return f
    }
    fun mediaBusy(): Boolean=PreparationStore.state.value.running || JobStore.jobs.value.any {
        it.status in setOf(JobStatus.RESOLVING,JobStatus.QUEUED,JobStatus.DOWNLOADING,JobStatus.COMPRESSING) || it.savingCopy
    }
    fun install(c: Context) {
        if(mediaBusy()) return
        c.startActivity(Intent(c,app.snag.ui.UpdateInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
