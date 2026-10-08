package app.snag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationSnapshotTest {

    @Test
    fun snapshotMatchesExactParameters() {
        val snap = PreparationSnapshot(
            op = StudioOp.CLIP,
            startMs = 1000L,
            endMs = 4000L,
            opts = StudioOptsData(speed = 1.25f, mute = true, crop = FfmpegRunner.Crop.S11, rotate = 90),
            targetBytes = 8L * 1024 * 1024,
        )

        assertTrue(
            "Snapshot must match identical parameters",
            snap.matches(
                currentOp = StudioOp.CLIP,
                currentStartMs = 1000L,
                currentEndMs = 4000L,
                currentOpts = StudioOptsData(speed = 1.25f, mute = true, crop = FfmpegRunner.Crop.S11, rotate = 90),
                currentTargetBytes = 8L * 1024 * 1024,
            ),
        )
    }

    @Test
    fun snapshotDetectsDifferences() {
        val snap = PreparationSnapshot(
            op = StudioOp.CLIP,
            startMs = 1000L,
            endMs = 4000L,
            opts = StudioOptsData(speed = 1.0f),
            targetBytes = null,
        )

        assertFalse(
            "Different op must not match",
            snap.matches(StudioOp.GIF, 1000L, 4000L, StudioOptsData(), null),
        )
        assertFalse(
            "Different start must not match",
            snap.matches(StudioOp.CLIP, 1500L, 4000L, StudioOptsData(), null),
        )
        assertFalse(
            "Different end must not match",
            snap.matches(StudioOp.CLIP, 1000L, 5000L, StudioOptsData(), null),
        )
        assertFalse(
            "Different speed must not match",
            snap.matches(StudioOp.CLIP, 1000L, 4000L, StudioOptsData(speed = 1.5f), null),
        )
        assertFalse(
            "Different target size must not match",
            snap.matches(StudioOp.CLIP, 1000L, 4000L, StudioOptsData(), 8L * 1024 * 1024),
        )
    }

    @Test
    fun snapshotMatchesStaticStickerAndFrameByResolvedPosition() {
        val staticSnap = PreparationSnapshot(
            op = StudioOp.STICKER_STATIC,
            startMs = 2800L,
            endMs = 3000L,
            framePositionMs = 2800L,
        )

        assertTrue(
            "Static sticker with matching resolved frame position must match",
            staticSnap.matches(
                currentOp = StudioOp.STICKER_STATIC,
                currentStartMs = 0L,
                currentEndMs = 3000L,
                currentOpts = StudioOptsData(),
                currentTargetBytes = null,
                currentFramePosMs = 2800L,
            ),
        )

        assertFalse(
            "Static sticker with different frame position must not match",
            staticSnap.matches(
                currentOp = StudioOp.STICKER_STATIC,
                currentStartMs = 0L,
                currentEndMs = 3000L,
                currentOpts = StudioOptsData(),
                currentTargetBytes = null,
                currentFramePosMs = 1500L,
            ),
        )
    }

    @Test
    fun preparationStorePreservesPreviousResultOnStateUpdates() {
        PreparationStore.forceReset()

        val snap1 = PreparationSnapshot(
            op = StudioOp.CLIP,
            startMs = 500L,
            endMs = 2500L,
        )

        // 1. Successful first export publishes state
        PreparationStore.update {
            PreparationState(
                workId = "prep-1",
                running = false,
                pct = 100,
                name = "clip.mp4",
                bytes = 54321L,
                op = StudioOp.CLIP,
                snapshot = snap1,
                showReady = true,
            )
        }

        val state1 = PreparationStore.state.value
        assertEquals("clip.mp4", state1.name)
        assertEquals(54321L, state1.bytes)
        assertEquals(snap1, state1.snapshot)
        assertTrue(state1.showReady)

        // 2. User taps "Изменить": showReady becomes false, previous result preserved
        PreparationStore.update { it.copy(showReady = false) }
        val stateAdjust = PreparationStore.state.value
        assertFalse(stateAdjust.showReady)
        assertEquals("clip.mp4", stateAdjust.name)
        assertEquals(snap1, stateAdjust.snapshot)

        // 3. User starts a new export attempt: workId changes, running = true, showReady = false,
        // but previous name, bytes, and snapshot remain intact
        PreparationStore.update {
            it.copy(
                workId = "prep-2",
                running = true,
                cancelling = false,
                pct = 0,
                op = StudioOp.MP3,
                error = null,
                showReady = false,
            )
        }
        val stateRunning = PreparationStore.state.value
        assertTrue(stateRunning.running)
        assertEquals("prep-2", stateRunning.workId)
        assertEquals("clip.mp4", stateRunning.name)
        assertEquals(54321L, stateRunning.bytes)
        assertEquals(snap1, stateRunning.snapshot)

        // 4. New export is cancelled: running = false, error recorded, previous result STILL intact
        PreparationStore.update {
            it.copy(
                running = false,
                cancelling = false,
                error = "Cancelled",
                showReady = false,
            )
        }
        val stateCancelled = PreparationStore.state.value
        assertFalse(stateCancelled.running)
        assertEquals("Cancelled", stateCancelled.error)
        assertEquals("clip.mp4", stateCancelled.name)
        assertEquals(54321L, stateCancelled.bytes)
        assertEquals(snap1, stateCancelled.snapshot)

        // 5. Reopening ready screen without re-encoding
        PreparationStore.update {
            it.copy(showReady = true)
        }
        val stateReopened = PreparationStore.state.value
        assertTrue(stateReopened.showReady)
        assertEquals("clip.mp4", stateReopened.name)

        PreparationStore.forceReset()
    }
}
