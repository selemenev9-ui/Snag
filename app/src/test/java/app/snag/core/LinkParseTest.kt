package app.snag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkParseTest {

    @Test fun `plain url`() =
        assertEquals("https://youtu.be/abc", LinkParse.firstUrl("https://youtu.be/abc"))

    @Test fun `url inside messenger text`() = assertEquals(
        "https://tiktok.com/@a/video/1",
        LinkParse.firstUrl("смотри что нашел https://tiktok.com/@a/video/1 жесть!"),
    )

    @Test fun `trailing punctuation stripped`() = assertEquals(
        "https://x.com/a",
        LinkParse.firstUrl("https://x.com/a)."),
    )

    @Test fun `null and blank`() {
        assertNull(LinkParse.firstUrl(null))
        assertNull(LinkParse.firstUrl("   "))
    }

    @Test fun `no url`() = assertNull(LinkParse.firstUrl("просто текст без ссылок"))

    @Test fun `first url wins`() = assertEquals(
        "https://a.com",
        LinkParse.firstUrl("https://a.com https://b.com"),
    )

    @Test fun `query params preserved`() = assertEquals(
        "https://youtu.be/x?t=42&si=abc",
        LinkParse.firstUrl("https://youtu.be/x?t=42&si=abc"),
    )

    @Test fun `all urls in pasted block`() = assertEquals(
        listOf("https://a.com/1", "https://b.com/2", "https://c.com/3"),
        LinkParse.allUrls("раз https://a.com/1 два\nhttps://b.com/2, https://c.com/3"),
    )

    @Test fun `duplicate urls deduplicated`() = assertEquals(
        listOf("https://a.com/x"),
        LinkParse.allUrls("https://a.com/x и снова https://a.com/x"),
    )

    @Test fun `playlist detection`() {
        assertEquals(
            true,
            LinkParse.looksLikePlaylist(
                "https://youtube.com/playlist?list=PLxyz",
            ),
        )
        assertEquals(
            true,
            LinkParse.looksLikePlaylist(
                "https://youtube.com/watch?v=abc&list=PLxyz",
            ),
        )
        assertEquals(false, LinkParse.looksLikePlaylist("https://youtu.be/abc"))
        assertEquals(false, LinkParse.looksLikePlaylist("https://tiktok.com/a"))
    }
}
