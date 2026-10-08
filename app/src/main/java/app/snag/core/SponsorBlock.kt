package app.snag.core

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * SponsorBlock client — community-maintained ad/intro/outro segment marks
 * for YouTube videos. Public API, no key, read-only.
 */
object SponsorBlock {

    private val ID_RX =
        Regex("""(?:v=|youtu\.be/|shorts/)([A-Za-z0-9_-]{11})""")

    fun videoIdOf(url: String): String? =
        ID_RX.find(url)?.groupValues?.get(1)

    data class Segment(val startMs: Long, val endMs: Long, val category: String)

    /** Returns marked segments, empty list when none / unreachable. */
    fun segments(videoId: String): List<Segment> {
        val api = "https://sponsor.ajay.app/api/skipSegments" +
            "?videoID=$videoId" +
            "&categories=[\"sponsor\",\"selfpromo\",\"intro\",\"outro\"]"
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val seg = o.optJSONArray("segment") ?: return@mapNotNull null
                Segment(
                    startMs = (seg.optDouble(0) * 1000).toLong(),
                    endMs = (seg.optDouble(1) * 1000).toLong(),
                    category = o.optString("category"),
                )
            }.sortedBy { it.startMs }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Kept-time ffmpeg select expression for [startMs, endMs] with sponsor
     * ranges cut out. Returns null when nothing is skipped.
     */
    fun keepExpr(startMs: Long, endMs: Long, segs: List<Segment>): String? {
        if (segs.isEmpty()) return null
        val kept = mutableListOf<Pair<Double, Double>>()
        var cursor = startMs.toDouble()
        for (s in segs) {
            val ss = s.startMs.coerceIn(startMs, endMs).toDouble()
            val se = s.endMs.coerceIn(startMs, endMs).toDouble()
            if (se <= ss) continue
            if (ss > cursor) kept += cursor / 1000.0 to ss / 1000.0
            cursor = maxOf(cursor, se)
        }
        if (cursor < endMs) kept += cursor / 1000.0 to endMs / 1000.0
        if (kept.isEmpty()) return null
        if (kept.size == 1 &&
            kept[0].first * 1000 == startMs.toDouble() &&
            kept[0].second * 1000 == endMs.toDouble()
        ) return null
        return kept.joinToString("+") { (a, b) ->
            "between(t,${"%.3f".format(java.util.Locale.US, a)}," +
                "${"%.3f".format(java.util.Locale.US, b)})"
        }
    }
}
