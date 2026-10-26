package uk.co.aaronburt.rtsp.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.ui.PlayerSurface

/** Opt-in tests against the authenticated local RTSP fixtures described in docs/verification-0.4.0.md. */
class Media3PlaybackInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: LiveStreamController? = null
    private val host get() = InstrumentationRegistry.getArguments().getString("rtspTestHost")!!

    @Before fun requireFixture() {
        assumeTrue("Requires an explicitly configured RTSP test fixture", InstrumentationRegistry.getArguments().containsKey("rtspTestHost"))
    }

    @After fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller?.release() }
        scope.cancel()
    }

    private fun start(port: Int, transport: RtspTransport, stallMs: Long = 15_000): LiveStreamController {
        compose.runOnUiThread {
            controller = LiveStreamController(Media3Engine(compose.activity), scope, ReconnectPolicy(), stallMs)
        }
        val current = checkNotNull(controller)
        compose.setContent { PlayerSurface(current, Modifier.fillMaxSize()) }
        compose.runOnUiThread {
            current.setMuted(true)
            current.start(StreamConfig("Fixture", "rtsp://viewer:streamtest@$host:$port/camera", transport = transport))
        }
        return current
    }

    @Test fun reportsVideoFormatAndNegotiatedTcpAndUdp() {
        val port = InstrumentationRegistry.getArguments().getString("rtspTestUdpPort")?.toInt() ?: 18554
        val current = start(port, RtspTransport.TCP)
        compose.waitUntil(25_000) {
            current.state.value == StreamState.Playing && current.details.value.transport == RtspTransport.TCP &&
                (current.details.value.videoBitrate ?: 0) > 0 && (current.details.value.frameRate ?: 0f) > 0f
        }
        assertEquals(FrameSize(1280, 720), current.details.value.frameSize)
        assertEquals("H.264", current.details.value.codec)
        assertTrue(checkNotNull(current.details.value.frameRate) > 0)
        compose.runOnUiThread { current.setTransport(RtspTransport.UDP) }
        compose.waitUntil(25_000) {
            current.state.value == StreamState.Playing && current.details.value.transport == RtspTransport.UDP &&
                (current.details.value.videoBitrate ?: 0) > 0
        }
    }

    @Test fun udpRequestReportsTcpWhenTheServerRequiresFallback() {
        val current = start(28554, RtspTransport.UDP)
        compose.waitUntil(25_000) {
            current.state.value == StreamState.Playing && current.details.value.transport == RtspTransport.TCP
        }
    }

    @Test fun authenticationPathErrorsAndSuccessUseTheRealPlayer() = runBlocking {
        val tester = Media3ConnectionTester(compose.activity)
        val camera = Camera("fixture", "Fixture", "rtsp://$host:18554/camera", username = "viewer", password = "wrong")
        val rejected = tester.test(camera)
        assertTrue(rejected.toString(), rejected is ConnectionResult.Failed && rejected.reason.contains("Authentication failed"))
        assertEquals(ConnectionResult.Available, tester.test(camera.copy(password = "streamtest")))
        val missing = tester.test(camera.copy(url = "rtsp://$host:18554/missing", password = "streamtest"))
        assertTrue(missing.toString(), missing is ConnectionResult.Failed && missing.reason.contains("path not found"))
        val invalid = tester.test(camera.copy(url = "rtsp://$host:18554/unconfigured", password = "streamtest"))
        assertTrue(invalid.toString(), invalid is ConnectionResult.Failed && invalid.reason.contains("stream URL and path"))
    }

    @Test fun frozenTcpConnectionLeavesPlayingWhenFramesStop() {
        val current = start(38554, RtspTransport.TCP, stallMs = 3_000)
        compose.waitUntil(15_000) { current.state.value == StreamState.Playing }
        compose.waitUntil(20_000) {
            val state = current.state.value
            state is StreamState.Reconnecting && state.reason == "Stream stalled"
        }
    }
}
