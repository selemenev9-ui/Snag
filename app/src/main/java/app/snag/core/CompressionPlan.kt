package app.snag.core

/** Shared size-budget policy; quality is an estimate, not a perceptual measurement. */
data class CompressionPlan(val width: Int, val height: Int, val videoBps: Long,
    val audioBps: Int, val alreadyFits: Boolean, val feasible: Boolean) {
    companion object {
        fun calculate(width: Int, height: Int, durationSec: Double, sourceBytes: Long,
            targetBytes: Long, hasAudio: Boolean): CompressionPlan {
            val total = (targetBytes * 8.0 / durationSec.coerceAtLeast(0.1) * 0.88).toLong()
            val audio = if (hasAudio) (total * 0.20).toInt().coerceIn(if (total >= 96000) 32000 else 16000, 128000) else 0
            val video = (total - audio).coerceAtLeast(8000)
            val cap = when {
                video >= 8_000_000 -> 2160
                video >= 1_400_000 -> 1080
                video >= 400_000 -> 720
                video >= 180_000 -> 540
                video >= 80_000 -> 360
                else -> 240
            }
            val factor = (cap.toDouble() / minOf(width, height).coerceAtLeast(1)).coerceAtMost(1.0)
            fun even(n: Int) = ((n * factor).toInt() / 2 * 2).coerceAtLeast(2)
            val fits = sourceBytes in 1..targetBytes
            return CompressionPlan(if (fits) width else even(width), if (fits) height else even(height), video, audio,
                fits, fits || total >= audio + 8000)
        }
    }
}
