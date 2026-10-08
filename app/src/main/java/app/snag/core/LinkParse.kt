package app.snag.core

/** Extracts the first http(s) URL from arbitrary shared text (messengers, browsers). */
object LinkParse {
    private val URL = Regex("""https?://[^\s<>"'`\\]+""")

    fun firstUrl(text: String?): String? = allUrls(text).firstOrNull()

    /** All http(s) URLs in arbitrary text — batch paste support. */
    fun allUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return URL.findAll(text).map {
            it.value.trimEnd('.', ',', ')', ']', '}', ';', '!', '?', '"', '\'')
        }.distinct().toList()
    }

    /** Heuristic: YouTube playlist / watch-with-list / sets pages. */
    fun looksLikePlaylist(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("/playlist") ||
            u.contains("list=") ||
            u.contains("/sets/")
    }
}
