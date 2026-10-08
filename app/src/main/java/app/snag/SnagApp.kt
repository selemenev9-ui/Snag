package app.snag

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.snag.core.Downloader
import app.snag.core.HistoryStore
import android.os.Build.VERSION.SDK_INT
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import java.io.File

class SnagApp : Application(), SingletonImageLoader.Factory {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(app.snag.core.AppPreferences.localized(base))
    }
    override fun newImageLoader(context: PlatformContext): ImageLoader = createImageLoader(context)

    override fun onCreate() {
        super.onCreate()
        app.snag.core.AppPreferences.init(this)
        SingletonImageLoader.setSafe { createImageLoader(it) }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                SnagServiceChannels.DOWNLOADS,
                getString(R.string.channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        // Warm the yt-dlp/python runtime off the main thread; first extract is slow.
        Thread {
            // Keep artifacts long enough for the receiving app to read them.
            app.snag.core.ShareCache.prune(this)
            File(filesDir, "studio").listFiles()?.forEach { it.delete() }
            Downloader.warm(this)
        }.start()
        HistoryStore.init(this)
    }

    companion object {
        fun createImageLoader(context: PlatformContext): ImageLoader =
            ImageLoader.Builder(context)
                .components {
                    if (SDK_INT >= 28) {
                        add(AnimatedImageDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
    }
}

object SnagServiceChannels {
    const val DOWNLOADS = "downloads"
}
