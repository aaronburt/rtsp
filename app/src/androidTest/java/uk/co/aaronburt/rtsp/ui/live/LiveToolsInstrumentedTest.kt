package uk.co.aaronburt.rtsp.ui.live

import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import uk.co.aaronburt.rtsp.MainActivity
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.FakeCameraRepository
import uk.co.aaronburt.rtsp.player.FrameSize
import uk.co.aaronburt.rtsp.player.PlayerEngine
import uk.co.aaronburt.rtsp.player.StreamDetails
import uk.co.aaronburt.rtsp.player.StreamSource
import uk.co.aaronburt.rtsp.ui.RtspApp
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

@SdkSuppress(minSdkVersion = 29)
class LiveToolsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val engine = DrawingEngine()
    @Volatile private var info = PlayerInfo()
    private var initialPhotos = emptySet<Uri>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launch(produceFrames: Boolean = true) {
        initialPhotos = photos()
        engine.produceFrames = produceFrames
        val repository = FakeCameraRepository(listOf(Camera(
            "fixture", "Test camera", "rtsp://fixture/main", subUrl = "rtsp://fixture/sub", autoReconnect = false,
        )))
        val slot: PlayerSlot = { camera, quality, muted, modifier, onInfo ->
            Media3Player(camera, quality, muted, modifier, {
                info = it
                onInfo(it)
            }, engineFactory = { _, _ -> engine })
        }
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalPictureInPicture provides compose.activity.pictureInPicture) {
                    RtspTheme { RtspApp(AppContainer(repository = repository), playerSlot = slot) }
                }
            }
        }
        compose.onNodeWithText("Test camera").performClick()
        compose.waitUntil(10_000) { engine.loads > 0 }
    }

    @After fun cleanUp() {
        (photos() - initialPhotos).forEach { context.contentResolver.delete(it, null, null) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.release() }
    }

    @Test fun snapshotsSaveRealSurfacePixelsInNormalAndFullscreenAndRejectOfflineFrames() {
        launch(produceFrames = false)
        compose.onNodeWithText("Snapshot").assertIsNotEnabled()
        compose.runOnUiThread { engine.produceFrames = true }
        compose.waitUntil(10_000) { info.snapshotSource != null }
        val liveSource = checkNotNull(info.snapshotSource)
        val loads = engine.loads
        compose.onNodeWithText("Snapshot").performClick()
        compose.waitUntil(10_000) { (photos() - initialPhotos).size == 1 }
        assertPhoto((photos() - initialPhotos).single())
        val framesBeforeRotation = engine.frames
        compose.onNodeWithContentDescription("Fullscreen").performClick()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { engine.frames > framesBeforeRotation + 2 && info.snapshotSource != null }
        compose.onNodeWithContentDescription("Snapshot").performClick()
        compose.waitUntil(10_000) { (photos() - initialPhotos).size == 2 }
        (photos() - initialPhotos).forEach(::assertPhoto)
        assertEquals(loads, engine.loads)
        captureScreenshot("fullscreen")
        compose.runOnUiThread { engine.fail() }
        compose.waitUntil(10_000) { info.snapshotSource == null }
        compose.onNodeWithContentDescription("Snapshot").assertIsNotEnabled()
        assertNull(runBlocking { liveSource.capture() })
    }

    @Test fun pictureInPictureKeepsThePlayerQualityAndMuteThenCleansUpOnClose() {
        assumeTrue(compose.activity.pictureInPicture.supported)
        launch()
        compose.waitUntil(10_000) { info.snapshotSource != null }
        compose.onNodeWithText("Sub stream").performScrollTo().performClick()
        compose.onNodeWithText("Unmute").performScrollTo().performClick()
        compose.waitUntil(10_000) { engine.source?.url == "rtsp://fixture/sub" && !engine.mutedValue && info.snapshotSource != null }
        val loads = engine.loads
        compose.onNodeWithText("Picture-in-picture").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.activity.isInPictureInPictureMode }
        val firstFrame = engine.frames
        compose.waitUntil(10_000) { engine.frames > firstFrame + 3 }
        assertEquals(loads, engine.loads)
        assertTrue(engine.running)
        captureScreenshot("pip")
        returnToApp()
        compose.waitUntil(10_000) { !compose.activity.isInPictureInPictureMode && engine.running }
        compose.onNodeWithText("Sub stream").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Mute").performScrollTo().assertExists()
        assertEquals(loads, engine.loads)
        assertEquals("rtsp://fixture/sub", engine.source!!.url)
        assertEquals(false, engine.mutedValue)
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        compose.waitUntil(10_000) { !engine.running }
        returnToApp()
        compose.waitUntil(10_000) { engine.running && engine.loads == loads + 1 }
        compose.onNodeWithText("Picture-in-picture").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.activity.isInPictureInPictureMode }
        compose.runOnUiThread { compose.activity.finishAndRemoveTask() }
        compose.waitUntil(10_000) { engine.releases == 1 }
        assertEquals(false, engine.running)
    }

    private fun returnToApp() {
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun photos(): Set<Uri> = buildSet {
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
            arrayOf("RTSP_snapshot_%"), null)?.use { cursor ->
            while (cursor.moveToNext()) add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)))
        }
    }

    private fun assertPhoto(uri: Uri) {
        val bitmap = context.contentResolver.openInputStream(uri).use(BitmapFactory::decodeStream)!!
        try {
            assertEquals(1280, bitmap.width)
            assertEquals(720, bitmap.height)
            assertTrue(Color.red(bitmap.getPixel(320, 360)) > 230)
            assertTrue(Color.blue(bitmap.getPixel(960, 360)) > 230)
        } finally { bitmap.recycle() }
    }

    private fun captureScreenshot(name: String) {
        runCatching {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "v050-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}

internal class DrawingEngine : PlayerEngine {
    private var listener: PlayerEngine.Listener? = null
    private var surface: Surface? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile var produceFrames = true
    @Volatile var loads = 0
    @Volatile var frames = 0
    @Volatile var releases = 0
    @Volatile var running = false
    @Volatile var mutedValue = true
    @Volatile var source: StreamSource? = null
    private val draw = object : Runnable {
        override fun run() {
            if (!running) return
            val target = surface
            if (produceFrames && target?.isValid == true) {
                val canvas = runCatching { target.lockCanvas(null) }.getOrNull()
                if (canvas != null) {
                    canvas.drawColor(Color.RED)
                    canvas.drawRect(canvas.width / 2f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), Paint().apply { color = Color.BLUE })
                    target.unlockCanvasAndPost(canvas)
                    frames++
                    listener?.onFrameSize(FrameSize(1280, 720))
                    listener?.onStreamDetails(StreamDetails(frameSize = FrameSize(1280, 720), frameRate = 5f, codec = "Fixture"))
                    listener?.onVideoFrame(frames * 200_000L)
                }
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun setListener(listener: PlayerEngine.Listener?) { this.listener = listener }
    override fun setSurface(surface: Surface?) { this.surface = surface }
    override fun setMuted(muted: Boolean) { mutedValue = muted }
    override fun load(source: StreamSource) {
        handler.removeCallbacks(draw)
        this.source = source
        loads++
        running = true
        listener?.onPlaying()
        handler.post(draw)
    }
    fun fail() { listener?.onError("Fixture disconnected") }
    override fun stop() { running = false; handler.removeCallbacks(draw) }
    override fun release() {
        if (releases > 0) return
        releases++
        stop()
        listener = null
        surface = null
    }
}
