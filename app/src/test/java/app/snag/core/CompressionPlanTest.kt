package app.snag.core

import org.junit.Assert.*
import org.junit.Test

class CompressionPlanTest {
    @Test fun shortClipGets720InsteadOfFixed480AndKeepsAspect() {
        val plan = CompressionPlan.calculate(1920,1080,3.0,2859112,524288,true)
        assertEquals(1280, plan.width); assertEquals(720, plan.height)
        assertTrue(plan.feasible); assertFalse(plan.alreadyFits)
    }
    @Test fun portraitRetainsAspectAndDoesNotUpscaleSmallSources() {
        val portrait = CompressionPlan.calculate(1080,1920,3.0,2859112,524288,true)
        assertEquals(720,portrait.width); assertEquals(1280,portrait.height)
        val small = CompressionPlan.calculate(320,180,3.0,2859112,524288,true)
        assertEquals(320,small.width); assertEquals(180,small.height)
    }
    @Test fun tinyLongVideoBudgetIsRejectedAndSilentVideosReserveNoAudio() {
        assertFalse(CompressionPlan.calculate(1920,1080,1200.0,10000000,104857,true).feasible)
        assertEquals(0,CompressionPlan.calculate(1920,1080,3.0,10000000,524288,false).audioBps)
    }
}
