package app.snag.core

/** Pure rules shared by the screen and export boundary. */
object PreparationRules {
    fun targetBytes(text: String): Long? {
        val size = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!size.isFinite() || size < 0.1 || size > 4096) return null
        return (size * 1024 * 1024).toLong()
    }

    fun formatTargetMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb % 1.0 == 0.0) {
            mb.toLong().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.3f", mb)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    fun validRange(startMs: Long, endMs: Long, durationMs: Long): Boolean =
        startMs >= 0 && endMs <= durationMs && endMs - startMs >= 500

    fun validFramePosition(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs in 0..durationMs

    fun resolveFramePosition(currentPositionMs: Long, startMs: Long, endMs: Long): Long {
        val minMs = minOf(startMs, endMs)
        val maxMs = maxOf(startMs, endMs)
        return if (currentPositionMs in minMs..maxMs) currentPositionMs else startMs
    }

    fun timeMs(text: String): Long? {
        val parts = text.trim().replace(',', '.').split(':')
        if (parts.size !in 1..3) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        if (numbers.any { !it.isFinite() || it < 0 }) return null
        if (numbers.drop(1).any { it >= 60 }) return null
        if (numbers.dropLast(1).any { it % 1.0 != 0.0 }) return null
        val seconds = numbers.fold(0.0) { a, n -> a * 60 + n }
        if (seconds > Long.MAX_VALUE / 1000.0) return null
        return (seconds * 1000).toLong()
    }
}
