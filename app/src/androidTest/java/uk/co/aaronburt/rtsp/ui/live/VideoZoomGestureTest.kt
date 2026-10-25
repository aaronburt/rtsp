package uk.co.aaronburt.rtsp.ui.live

import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.aaronburt.rtsp.player.LiveStreamController
import uk.co.aaronburt.rtsp.player.PlayerEngine
import uk.co.aaronburt.rtsp.player.StreamSource
import uk.co.aaronburt.rtsp.ui.PlayerSurface

class VideoZoomGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = SurfaceOnlyEngine()
    private val controller = LiveStreamController(engine, scope)

    @After
    fun cleanUp() {
        compose.runOnIdle { controller.release() }
        scope.cancel()
    }

    @Test
    fun pinchScalesTheNativeVideoWithoutReplacingItsSurfaceAndResetRestoresIt() {
        showVideo(enabled = true)
        compose.waitUntil { engine.attachments > 0 }
        val initialAttachments = engine.attachments
        assertVideoPixel(Color.RED)
        pinchOut()
        compose.runOnIdle {
            val video = findVideo(compose.activity.window.decorView)!!
            assertTrue(video.scaleX > 1.5f)
            assertEquals(video.scaleX, video.scaleY, 0f)
            assertTrue(video.clipBounds!!.width() < video.width)
            assertEquals(initialAttachments, engine.attachments)
        }
        assertVideoPixel(Color.GREEN)
        compose.onNodeWithTag("video").performTouchInput {
            swipe(center, Offset(width * 0.9f, center.y))
        }
        compose.runOnIdle {
            val video = findVideo(compose.activity.window.decorView)!!
            assertTrue(video.translationX > 0f)
            assertTrue(video.translationX <= video.width * (video.scaleX - 1f) / 2f)
        }
        compose.onNodeWithText("Reset zoom").performClick()
        compose.runOnIdle {
            val video = findVideo(compose.activity.window.decorView)!!
            assertEquals(1f, video.scaleX, 0f)
            assertEquals(0f, video.translationX, 0f)
            assertEquals(0f, video.translationY, 0f)
        }
        assertVideoPixel(Color.RED)
    }

    @Test
    fun pinchDoesNotZoomWhenTheSettingIsDisabled() {
        showVideo(enabled = false)
        compose.waitUntil { engine.attachments > 0 }
        pinchOut()
        compose.runOnIdle {
            assertEquals(1f, findVideo(compose.activity.window.decorView)!!.scaleX, 0f)
        }
        compose.onNodeWithText("Reset zoom").assertDoesNotExist()
    }

    private fun showVideo(enabled: Boolean) {
        compose.setContent {
            Box(Modifier.size(240.dp).testTag("video")) {
                ZoomableVideo(enabled = enabled) {
                    PlayerSurface(controller, Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun pinchOut() {
        compose.onNodeWithTag("video").performTouchInput {
            pinch(
                start0 = Offset(center.x - width * 0.1f, center.y),
                start1 = Offset(center.x + width * 0.1f, center.y),
                end0 = Offset(center.x - width * 0.35f, center.y),
                end1 = Offset(center.x + width * 0.35f, center.y),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
    }

    private fun findVideo(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findVideo(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun assertVideoPixel(expected: Int) {
        val point = IntArray(2)
        compose.runOnIdle {
            val video = findVideo(compose.activity.window.decorView)!!
            val viewport = video.parent as View
            viewport.getLocationOnScreen(point)
            point[0] += viewport.width / 5
            point[1] += viewport.height / 2
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                try {
                    screenshot.getPixel(point[0], point[1]) == expected
                } finally {
                    screenshot.recycle()
                }
            }
        } catch (error: AssertionError) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(instrumentation.targetContext.getExternalFilesDir(null), "zoom-failure.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                screenshot.recycle()
            }
            throw error
        }
    }

    private class SurfaceOnlyEngine : PlayerEngine {
        @Volatile var attachments = 0
        override fun setSurface(surface: Surface?) {
            if (surface != null) {
                attachments++
                val canvas = surface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.GREEN)
                    canvas.drawRect(0f, 0f, canvas.width * 0.25f, canvas.height.toFloat(), Paint().apply { color = Color.RED })
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
            }
        }
        override fun setListener(listener: PlayerEngine.Listener?) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun load(source: StreamSource) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
