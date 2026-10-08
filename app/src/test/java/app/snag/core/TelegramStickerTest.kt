package app.snag.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramStickerTest {

    @Test
    fun constantsMatchTelegramOfficialSpecification() {
        assertEquals("org.telegram.messenger", TelegramSticker.TELEGRAM_PACKAGE)
        assertEquals("org.telegram.messenger.CREATE_STICKER_PACK", TelegramSticker.ACTION_CREATE_STICKER_PACK)
    }
}
