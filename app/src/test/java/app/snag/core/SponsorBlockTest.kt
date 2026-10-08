package app.snag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorBlockTest {

    private fun seg(s: Long, e: Long) = SponsorBlock.Segment(s, e, "sponsor")

    @Test fun `no segments gives null expr`() =
        assertNull(SponsorBlock.keepExpr(0, 60_000, emptyList()))

    @Test fun `segment outside range keeps everything`() =
        assertNull(
            SponsorBlock.keepExpr(0, 10_000, listOf(seg(20_000, 25_000))),
        )

    @Test fun `middle segment splits into two kept parts`() {
        val expr = SponsorBlock.keepExpr(0, 60_000, listOf(seg(10_000, 20_000)))
        assertEquals("between(t,0.000,10.000)+between(t,20.000,60.000)", expr)
    }

    @Test fun `overlapping segments merge via cursor`() {
        val expr = SponsorBlock.keepExpr(
            0, 60_000,
            listOf(seg(10_000, 30_000), seg(20_000, 40_000)),
        )
        assertEquals("between(t,0.000,10.000)+between(t,40.000,60.000)", expr)
    }

    @Test fun `trailing skip to end`() {
        val expr = SponsorBlock.keepExpr(0, 60_000, listOf(seg(50_000, 60_000)))
        assertEquals("between(t,0.000,50.000)", expr)
    }

    @Test fun `segment fully covering range`() =
        assertNull(SponsorBlock.keepExpr(0, 60_000, listOf(seg(0, 60_000))))
}
