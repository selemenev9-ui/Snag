package app.snag.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handles Telegram sticker pack import protocol.
 *
 * Telegram's official import protocol (org.telegram.messenger.CREATE_STICKER_PACK)
 * is implemented in Telegram's Android client (org.telegram.ui.Components.xy0 / StickersAlert).
 * In MediaController.getStickerExt(Uri), Telegram inspects the file header and strictly accepts
 * only "png", "webp", and "tgs" magic bytes; WebM is rejected by the client-side importer.
 * For static stickers, Telegram requires PNG/WEBP with at least one side equal to 512px
 * and the other side <= 512px.
 */
object TelegramSticker {
    const val TELEGRAM_PACKAGE = "org.telegram.messenger"
    const val ACTION_CREATE_STICKER_PACK = "org.telegram.messenger.CREATE_STICKER_PACK"

    fun isTelegramInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getPackageInfo(TELEGRAM_PACKAGE, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an intent to import a static sticker (WebP/PNG) into Telegram.
     * Passes the sticker URI directly to Telegram's importer without any hidden conversions.
     */

    fun createImportIntent(context: Context, stickerUri: Uri): Intent {
        return Intent(ACTION_CREATE_STICKER_PACK).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(stickerUri))
            putStringArrayListExtra("STICKER_EMOJIS", arrayListOf("🎬"))
            putExtra("IMPORTER", context.packageName)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("sticker", stickerUri)
        }
    }
}
