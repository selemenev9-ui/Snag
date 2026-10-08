package app.snag.core

import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import app.snag.MainActivity

/** Quick Settings tile: tap → Snag opens and grabs the link from clipboard. */
class SnagTileService : TileService() {

    // The Intent overload is required on Android 13 and earlier.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val i = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_AUTOPASTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
