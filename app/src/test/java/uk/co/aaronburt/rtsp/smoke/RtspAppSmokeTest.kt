package uk.co.aaronburt.rtsp.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.ui.RtspApp
import uk.co.aaronburt.rtsp.ui.settings.InMemoryDisplayPreferences
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

@RunWith(RobolectricTestRunner::class)
class RtspAppSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun launch(preferences: InMemoryDisplayPreferences = InMemoryDisplayPreferences()) {
        val container = AppContainer(displayPreferences = preferences)
        compose.setContent { RtspTheme { RtspApp(container = container) } }
    }

    private fun assertShown(text: String) {
        compose.waitForIdle()
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            fail("expected '$text' on screen:\n${compose.onAllNodes(isRoot()).printToString()}")
        }
    }

    @Test
    fun startsOnCamerasListWithNavigationBar() {
        launch()
        assertShown("Front Door")
        assertShown("Back Gate")
        assertShown("Cameras")
        assertShown("Multiview")
        assertShown("Settings")
    }

    @Test
    fun recordingsTabIsHiddenUntilRecordingIsAllowed() {
        launch()
        compose.waitForIdle()
        assertFalse(compose.onAllNodesWithText("Recordings").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun recordingsTabAppearsWhenRecordingIsAllowed() {
        launch(InMemoryDisplayPreferences(initialAllowRecording = true))
        compose.onNodeWithText("Recordings").performClick()
        assertShown("No recordings yet. Press Record on a live view to save one.")
    }

    @Test
    fun settingsTogglesWriteThroughToPreferences() {
        val preferences = InMemoryDisplayPreferences()
        launch(preferences)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Pinch to zoom").performScrollTo().performClick()
        compose.onNodeWithText("Show clock on video").performScrollTo().performClick()
        compose.onNodeWithText("Allow recording").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(preferences.pinchToZoom.value)
        assertTrue(preferences.showClock.value)
        assertTrue(preferences.allowRecording.value)
    }

    @Test
    fun addCameraButtonOpensTheForm() {
        launch()
        compose.onNodeWithContentDescription("Add camera").performClick()
        assertShown("Save camera")
        compose.waitForIdle()
        assertFalse(compose.onAllNodesWithText("Multiview").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun openingACameraLeavesTheTopLevelTabs() {
        launch()
        compose.onAllNodesWithText("Front Door")[0].performClick()
        compose.waitForIdle()
        compose.waitForIdle()
        assertFalse(compose.onAllNodesWithText("Multiview").fetchSemanticsNodes().isNotEmpty())
    }
}
