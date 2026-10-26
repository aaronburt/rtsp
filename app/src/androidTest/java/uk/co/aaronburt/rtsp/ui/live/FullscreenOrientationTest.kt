package uk.co.aaronburt.rtsp.ui.live

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.aaronburt.rtsp.MainActivity
import uk.co.aaronburt.rtsp.data.FakeCameraRepository
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

class FullscreenOrientationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullscreenRotatesRestoresAndKeepsThePlayerAlive() {
        val showingLive = mutableStateOf(true)
        var playerMounts = 0
        val repository = FakeCameraRepository()
        val slot: PlayerSlot = { _, _, _, _, _ ->
            DisposableEffect(Unit) {
                playerMounts++
                onDispose { }
            }
        }
        compose.runOnUiThread {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            compose.activity.setContent {
                RtspTheme {
                    if (showingLive.value) {
                        LiveScreen(
                            viewModel = viewModel { LiveViewModel("front-door", repository) },
                            playerSlot = slot, onBack = { showingLive.value = false }, onEdit = {},
                            showClock = false, allowRecording = false, pinchToZoom = false,
                        )
                    } else Text("Camera list")
                }
            }
        }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithContentDescription("Fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        compose.onNodeWithContentDescription("Exit fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
        compose.onNodeWithContentDescription("Fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithContentDescription("Fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
        assertEquals(1, playerMounts)
    }
}
