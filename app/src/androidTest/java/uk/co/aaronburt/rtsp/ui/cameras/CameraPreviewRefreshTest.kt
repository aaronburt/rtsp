package uk.co.aaronburt.rtsp.ui.cameras

import android.graphics.BitmapFactory
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.aaronburt.rtsp.MainActivity
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.snapshot.FileSnapshotStore
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotStore
import uk.co.aaronburt.rtsp.ui.live.DrawingEngine
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

class CameraPreviewRefreshTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val enabled = mutableStateOf(false)
    private val produceFrames = AtomicBoolean(true)
    private val engines = CopyOnWriteArrayList<DrawingEngine>()
    private val results = CopyOnWriteArrayList<Boolean>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory by lazy { File(context.cacheDir, "preview-test-${UUID.randomUUID()}") }
    private val store by lazy { FileSnapshotStore(directory) }

    @After fun cleanUp() {
        engines.forEach { engine -> compose.runOnUiThread { engine.release() } }
        directory.listFiles().orEmpty().forEach { it.delete() }
        directory.delete()
    }

    @Test fun refreshCapturesCurrentPixelsThenReleasesAndRetainsTheImageOnFailure() {
        val camera = Camera(id = "preview", name = "Preview", url = "rtsp://fixture/main", subUrl = "rtsp://fixture/sub")
        compose.runOnUiThread {
            compose.activity.setContent {
                RtspTheme {
                    CompositionLocalProvider(LocalSnapshotStore provides store) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                            CameraPreviewRefresh(
                                camera, enabled.value, remember { PreviewRefreshScheduler(500) },
                                onConnection = { results += it },
                                modifier = Modifier.matchParentSize(),
                                engineFactory = {
                                    DrawingEngine().apply { produceFrames = this@CameraPreviewRefreshTest.produceFrames.get() }
                                        .also { engines += it }
                                },
                            )
                            // This matches the still-image cover used by camera tiles.
                            Box(Modifier.matchParentSize().background(Color.Black))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue(engines.isEmpty())
        compose.runOnUiThread { enabled.value = true }
        compose.waitUntil(10_000) { store.snapshots.value[camera.id] != null && engines.firstOrNull()?.releases == 1 }
        val first = store.snapshots.value.getValue(camera.id)
        val bitmap = BitmapFactory.decodeFile(first.file.path)!!
        try {
            assertEquals(640, bitmap.width)
            assertEquals(360, bitmap.height)
            assertTrue(android.graphics.Color.red(bitmap.getPixel(100, 180)) > 200)
            assertTrue(android.graphics.Color.blue(bitmap.getPixel(540, 180)) > 200)
        } finally { bitmap.recycle() }
        assertEquals(camera.subUrl, engines.first().source?.url)
        assertTrue(engines.first().mutedValue)
        assertFalse(engines.first().running)

        produceFrames.set(false)
        compose.waitUntil(10_000) { engines.size >= 2 && engines.last().running }
        val failed = engines.last()
        compose.runOnUiThread { failed.fail() }
        compose.waitUntil(10_000) { failed.releases == 1 && results.contains(false) }
        assertEquals(first.capturedAt, store.snapshots.value.getValue(camera.id).capturedAt)
        produceFrames.set(true)
        compose.waitUntil(10_000) { store.snapshots.value.getValue(camera.id).capturedAt > first.capturedAt }

        // Pausing the camera list cancels an active attempt and releases its decoder.
        produceFrames.set(false)
        compose.waitUntil(10_000) { engines.last().running && !engines.last().produceFrames }
        val paused = engines.last()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitUntil(10_000) { paused.releases == 1 }
        val count = engines.size
        Thread.sleep(750)
        assertEquals(count, engines.size)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { engines.size > count && engines.last().running }
        val cancelled = engines.last()
        compose.runOnUiThread { enabled.value = false }
        compose.waitUntil(10_000) { cancelled.releases == 1 }
        assertTrue(store.snapshots.value.getValue(camera.id).file.exists())
    }
}
