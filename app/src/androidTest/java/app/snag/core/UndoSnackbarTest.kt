package app.snag.core

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.snag.R
import app.snag.ui.MainScreen
import app.snag.ui.MainViewModel
import app.snag.ui.SnagTheme
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UndoSnackbarTest {
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun removalExpiresAndLatestRemovalCanBeUndone() {
        val vm = MainViewModel(app)
        val owned = (1..3).map { Job("undo-test-${UUID.randomUUID()}", "", title="Undo test $it", status=JobStatus.CANCELLED, ephemeral=true) }
        try {
            compose.setContent { SnagTheme { MainScreen(vm, {}, {}, { _,_,_,_ -> }, {}) } }
            compose.runOnIdle { owned.forEach(JobStore::add); vm.removeJob(owned[0].id) }
            compose.onNodeWithText(app.getString(R.string.snack_removed)).assertIsDisplayed()
            // Real host timeout; do not advance an isolated fake SnackbarHost clock.
            compose.waitUntil(15000) { vm.undoJob.value == null }
            compose.waitForIdle()
            compose.onNodeWithText(app.getString(R.string.snack_removed)).assertDoesNotExist()
            assertNull(JobStore.get(owned[0].id))
            compose.runOnIdle { vm.removeJob(owned[1].id) }
            compose.onNodeWithText(app.getString(R.string.snack_removed)).assertIsDisplayed()
            compose.runOnIdle { vm.removeJob(owned[2].id) }
            compose.onNodeWithText(app.getString(R.string.action_undo)).performClick()
            compose.runOnIdle {
                assertNull(vm.undoJob.value)
                assertNull(JobStore.get(owned[1].id))
                assertNotNull(JobStore.get(owned[2].id))
            }
            compose.onNodeWithText(app.getString(R.string.snack_removed)).assertDoesNotExist()
        } finally {
            compose.runOnIdle { vm.consumeUndo(); owned.forEach { JobStore.remove(it.id) } }
        }
    }
}
