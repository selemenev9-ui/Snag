package app.snag.core
import org.junit.Assert.*
import org.junit.Test
class StickerWindowRulesTest {
    @Test fun longSelectionBecomesThreeSeconds() {
        assertEquals(StickerWindow(0f,3000f),StickerWindowRules.resolve(0f,60000f,60000f,1f))
    }
    @Test fun movingNearEndKeepsWindowLengthAndShorteningIsAllowed() {
        assertEquals(StickerWindow(7000f,10000f),StickerWindowRules.move(9999f,3000f,10000f,1f))
        assertEquals(StickerWindow(4000f,5500f),StickerWindowRules.move(4000f,1500f,10000f,1f))
    }
    @Test fun speedAndShortSourceRespectThreeSecondOutput() {
        assertEquals(6000f,StickerWindowRules.resolve(0f,20000f,20000f,2f).end,0f)
        assertEquals(1000f,StickerWindowRules.resolve(0f,1000f,1000f,1f).end,0f)
    }
}
