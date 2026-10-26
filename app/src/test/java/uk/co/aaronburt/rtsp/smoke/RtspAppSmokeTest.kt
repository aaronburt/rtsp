package uk.co.aaronburt.rtsp.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
import androidx.compose.runtime.LaunchedEffect
import uk.co.aaronburt.rtsp.ui.live.PlayerInfo
import uk.co.aaronburt.rtsp.ui.live.PlayerSlot
import uk.co.aaronburt.rtsp.player.FrameSize
import uk.co.aaronburt.rtsp.player.RtspTransport
import uk.co.aaronburt.rtsp.player.StreamDetails
import uk.co.aaronburt.rtsp.player.StreamStatus
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.ui.settings.InMemoryStreamDefaultsPreferences

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
        compose.onNodeWithText("Refresh image previews").performScrollTo().performClick()
        compose.onNodeWithText("Pinch to zoom").performScrollTo().performClick()
        compose.onNodeWithText("Show clock on video").performScrollTo().performClick()
        compose.onNodeWithText("Allow recording").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(preferences.pinchToZoom.value)
        assertTrue(preferences.refreshPreviews.value)
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

    @Test
    fun streamDetailsShowMeasuredVideoAndNegotiatedTransport() {
        val slot: PlayerSlot = { _, _, _, _, onInfo ->
            LaunchedEffect(Unit) {
                onInfo(PlayerInfo(StreamStatus.Live, StreamDetails(
                    FrameSize(1920, 1080), 25f, "H.264", 2_000_000, RtspTransport.UDP,
                )))
            }
        }
        compose.setContent { RtspTheme { RtspApp(AppContainer(), playerSlot = slot) } }
        compose.onAllNodesWithText("Front Door")[0].performClick()
        compose.onNodeWithText("Stream details").performClick()
        assertShown("1920 × 1080")
        assertShown("25.0 fps")
        assertShown("H.264")
        assertShown("2000 kbps")
        assertShown("UDP") // The saved camera requests TCP; display the negotiated result.
    }

    @Test fun streamDefaultsSelectedInSettingsReachTheNextNewCameraForm() {
        val defaults = InMemoryStreamDefaultsPreferences()
        val container = AppContainer(streamDefaults = defaults)
        compose.setContent { RtspTheme { RtspApp(container) } }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("UDP").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Transport.Udp, defaults.defaults.value.transport)
        compose.onNodeWithText("Cameras").performClick()
        compose.onNodeWithContentDescription("Add camera").performClick()
        compose.onNodeWithText("UDP").performScrollTo().assertIsSelected()
    }
}
