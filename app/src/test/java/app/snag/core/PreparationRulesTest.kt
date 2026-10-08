package app.snag.core

import org.junit.Assert.*
import org.junit.Test

class PreparationRulesTest {
    @Test fun acceptsRussianDecimalSize() {
        assertEquals(13107200L, PreparationRules.targetBytes("12,5"))
        assertEquals(13107200L, PreparationRules.targetBytes("12.5"))
    }
    @Test fun formatsTargetMbAccurately() {
        assertEquals("8", PreparationRules.formatTargetMb(8L * 1024 * 1024))
        assertEquals("25", PreparationRules.formatTargetMb(25L * 1024 * 1024))
        assertEquals("50", PreparationRules.formatTargetMb(50L * 1024 * 1024))
        assertEquals("0.5", PreparationRules.formatTargetMb(524288L))
        assertEquals("1.5", PreparationRules.formatTargetMb(1572864L))
        assertEquals("12.5", PreparationRules.formatTargetMb(13107200L))
        assertEquals("0.125", PreparationRules.formatTargetMb(131072L))

        // Exact round-trip verification
        listOf("0.5", "1.5", "8", "12.5", "25", "50").forEach { str ->
            val bytes = PreparationRules.targetBytes(str)!!
            assertEquals(str, PreparationRules.formatTargetMb(bytes))
            assertEquals(bytes, PreparationRules.targetBytes(PreparationRules.formatTargetMb(bytes)))
        }
    }
    @Test fun rejectsNonFiniteAndUnusableSize() {
        listOf("NaN", "Infinity", "-1", "0", "0.09", "4097", "abc", "").forEach {
            assertNull(it, PreparationRules.targetBytes(it))
        }
    }
    @Test fun parsesLongVideoAndFractionalBoundaries() {
        assertEquals(3723500L, PreparationRules.timeMs("1:02:03,5"))
        assertEquals(90500L, PreparationRules.timeMs("1:30.5"))
        assertEquals(500L, PreparationRules.timeMs("0.5"))
    }
    @Test fun rejectsMalformedTime() {
        listOf("1:60", "1:2:90", "-1:20", "1.5:02", "Infinity", "::", "1:2:3:4").forEach {
            assertNull(it, PreparationRules.timeMs(it))
        }
    }
    @Test fun enforcesRangeAndMinimumDuration() {
        assertTrue(PreparationRules.validRange(1000, 1500, 2000))
        assertFalse(PreparationRules.validRange(1000, 1499, 2000))
        assertFalse(PreparationRules.validRange(-1, 1500, 2000))
        assertFalse(PreparationRules.validRange(1000, 2500, 2000))
        assertFalse(PreparationRules.validRange(1500, 1000, 2000))
    }
    @Test fun enforcesFramePositionValidity() {
        assertTrue(PreparationRules.validFramePosition(0, 3000))
        assertTrue(PreparationRules.validFramePosition(2800, 3000))
        assertTrue(PreparationRules.validFramePosition(3000, 3000))
        assertFalse(PreparationRules.validFramePosition(-1, 3000))
        assertFalse(PreparationRules.validFramePosition(3001, 3000))
        assertFalse(PreparationRules.validFramePosition(0, 0))
        assertFalse(PreparationRules.validFramePosition(0, -100))
    }
    @Test fun resolvesFramePositionWithinRangeAndFallsBackOtherwise() {
        assertEquals(2800L, PreparationRules.resolveFramePosition(2800, 0, 3000))
        assertEquals(0L, PreparationRules.resolveFramePosition(0, 0, 3000))
        assertEquals(3000L, PreparationRules.resolveFramePosition(3000, 0, 3000))
        // Position outside range falls back to startMs:
        assertEquals(0L, PreparationRules.resolveFramePosition(3500, 0, 3000))
        assertEquals(0L, PreparationRules.resolveFramePosition(-100, 0, 3000))
        assertEquals(1000L, PreparationRules.resolveFramePosition(500, 1000, 3000))
        assertEquals(1000L, PreparationRules.resolveFramePosition(4500, 1000, 3000))
        assertEquals(2500L, PreparationRules.resolveFramePosition(2500, 1000, 3000))
    }
    @Test fun preparationStoreLifecycleAndGuards() {
        PreparationStore.forceReset()
        assertEquals("", PreparationStore.state.value.workId)
        assertFalse(PreparationStore.state.value.running)
        assertFalse(PreparationStore.state.value.cancelling)

        PreparationStore.update { it.copy(workId = "w1", running = true) }
        assertTrue(PreparationStore.state.value.running)
        PreparationStore.reset()
        assertEquals("w1", PreparationStore.state.value.workId)

        PreparationStore.update { it.copy(cancelling = true) }
        assertTrue(PreparationStore.state.value.cancelling)
        PreparationStore.reset()
        assertEquals("w1", PreparationStore.state.value.workId)

        PreparationStore.update { it.copy(running = false, cancelling = false) }
        PreparationStore.reset()
        assertEquals("", PreparationStore.state.value.workId)
    }
}
