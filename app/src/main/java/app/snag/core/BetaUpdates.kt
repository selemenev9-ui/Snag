package app.snag.core

import android.content.Context
import app.snag.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class BetaUpdateState(val checking: Boolean = false, val version: String? = null,
    val url: String? = null, val failed: Boolean = false, val checked: Boolean = false,
    val code: Int = 0, val sha256: String = "")

object BetaUpdatePolicy {
    const val REPO = "https://github.com/selemenev9-ui/Snag"
    fun accepted(currentCode: Int, remoteCode: Int, edition: String, url: String): Boolean {
        val asset = when(edition) { "paper" -> "Snag-Paper.apk"; "classic" -> "Snag-Classic.apk"; else -> return false }
        return remoteCode > currentCode && url.startsWith("$REPO/releases/download/") &&
            url.endsWith("/$asset") && !url.contains("..") && !url.contains('?') && !url.contains('#')
    }
}

/** Checks a small public release manifest. Installation remains an explicit Android action. */
object BetaUpdates {
    private val mutable = MutableStateFlow(BetaUpdateState())
    val state = mutable.asStateFlow()
    private val busy = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val MANIFEST = "https://raw.githubusercontent.com/selemenev9-ui/Snag/main/update.json"
    private const val DAY = 24 * 60 * 60 * 1000L
    fun checkDaily(context: Context) = check(context, false)
    fun check(context: Context, force: Boolean = true) {
        if (BuildConfig.SNAG_DESIGN !in setOf("paper", "classic")) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("beta_updates", Context.MODE_PRIVATE)
        if (!force && prefs.getString("sha256", "").orEmpty().length == 64 &&
            System.currentTimeMillis() - prefs.getLong("checkedAt",0) < DAY) {
            val code = prefs.getInt("code",0)
            val url = prefs.getString("url", "") ?: ""
            if (BetaUpdatePolicy.accepted(BuildConfig.VERSION_CODE,code,BuildConfig.SNAG_DESIGN,url))
                mutable.value = BetaUpdateState(version=prefs.getString("version", ""),url=url,checked=true,
                    code=code,sha256=prefs.getString("sha256", "") ?: "")
            return
        }
        if (!busy.compareAndSet(false,true)) return
        mutable.value = mutable.value.copy(checking=true,failed=false)
        scope.launch {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(MANIFEST).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000; connection.readTimeout = 8000
                connection.setRequestProperty("Accept","application/json")
                connection.setRequestProperty("User-Agent","Snag/${BuildConfig.VERSION_NAME}")
                check(connection.responseCode == 200)
                val data = connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while(true) {
                        val n = input.read(buffer)
                        if(n < 0) break
                        check(out.size() + n <= 65536)
                        out.write(buffer,0,n)
                    }
                    out.toByteArray()
                }
                val builds = JSONObject(String(data, Charsets.UTF_8)).getJSONArray("builds")
                val entry = (0 until builds.length()).map { builds.getJSONObject(it) }
                    .first { it.getString("applicationId") == app.packageName }
                val code = entry.getInt("versionCode")
                val version = entry.getString("versionName")
                val url = entry.getString("url")
                val sha256 = entry.getString("sha256")
                require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
                val available = BetaUpdatePolicy.accepted(BuildConfig.VERSION_CODE,code,BuildConfig.SNAG_DESIGN,url)
                mutable.value = BetaUpdateState(version=if(available) version else null,url=if(available) url else null,checked=true,
                    code=code,sha256=sha256)
                prefs.edit().putLong("checkedAt",System.currentTimeMillis()).putInt("code",code)
                    .putString("version",version).putString("url",url).putString("sha256",sha256).apply()
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(checking=false,failed=true)
            } finally {
                connection?.disconnect(); busy.set(false)
            }
        }
    }
}
