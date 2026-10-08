package app.snag.core

data class StickerWindow(val start: Float, val end: Float)

object StickerWindowRules {
    fun resolve(start: Float, end: Float, duration: Float, speed: Float): StickerWindow {
        val total = duration.coerceAtLeast(0f)
        val max = minOf(total, 3000f * speed.coerceAtLeast(.1f))
        val min = minOf(500f,max)
        val length = (end-start).coerceIn(min,max)
        val left = start.coerceIn(0f,(total-length).coerceAtLeast(0f))
        return StickerWindow(left,left+length)
    }
    fun move(start: Float, length: Float, duration: Float, speed: Float): StickerWindow =
        resolve(start,start+length,duration,speed)
}
