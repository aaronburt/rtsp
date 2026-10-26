package uk.co.aaronburt.rtsp.player

import android.view.Surface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine : PlayerEngine {
    var attached: PlayerEngine.Listener? = null
    val loads = mutableListOf<StreamSource>()
    var stops = 0
    var released = false

    override fun setListener(listener: PlayerEngine.Listener?) {
        this.attached = listener
    }

    override fun setSurface(surface: Surface?) = Unit

    var muted: Boolean? = null

    override fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    override fun load(source: StreamSource) {
        loads += source
    }

    override fun stop() {
        stops += 1
    }

    override fun release() {
        released = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveStreamControllerTest {
    private val config = StreamConfig(
        name = "cam",
        mainUrl = "rtsp://h/main",
        subUrl = "rtsp://h/sub",
        transport = RtspTransport.TCP,
        latencyMs = 500,
    )

    private fun TestScope.controller(
        engine: FakeEngine,
        policy: ReconnectPolicy = ReconnectPolicy(1_000, 30_000, 3),
        stallMs: Long = 15_000,
    ) = LiveStreamController(engine, backgroundScope, policy, stallMs)

    @Test
    fun startLoadsMainStreamAndReachesPlaying() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        assertEquals(StreamState.Connecting, controller.state.value)
        assertEquals(listOf(StreamSource("rtsp://h/main", RtspTransport.TCP, 500)), engine.loads)
        engine.attached!!.onBuffering()
        assertEquals(StreamState.Buffering, controller.state.value)
        engine.attached!!.onPlaying()
        assertEquals(StreamState.Playing, controller.state.value)
    }

    @Test
    fun errorSchedulesReconnectWithBackoff() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onError("Camera unreachable")
        assertEquals(StreamState.Reconnecting(1, 3, 1_000, "Camera unreachable"), controller.state.value)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, engine.loads.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, engine.loads.size)
        assertEquals(StreamState.Connecting, controller.state.value)
        engine.attached!!.onError("Camera unreachable")
        assertEquals(StreamState.Reconnecting(2, 3, 2_000, "Camera unreachable"), controller.state.value)
    }

    @Test
    fun playingResetsAttemptCounter() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onError("x")
        advanceTimeBy(1_000)
        runCurrent()
        engine.attached!!.onPlaying()
        engine.attached!!.onError("x")
        assertEquals(StreamState.Reconnecting(1, 3, 1_000, "x"), controller.state.value)
    }

    @Test
    fun givesUpAfterMaxAttemptsAndRetryRestarts() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        listOf(1_000L, 2_000L, 4_000L).forEach { backoff ->
            engine.attached!!.onError("down")
            advanceTimeBy(backoff)
            runCurrent()
        }
        engine.attached!!.onError("down")
        assertEquals(StreamState.Failed("down"), controller.state.value)
        val loadsBefore = engine.loads.size
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(loadsBefore, engine.loads.size)
        controller.retry()
        assertEquals(StreamState.Connecting, controller.state.value)
        assertEquals(loadsBefore + 1, engine.loads.size)
    }

    @Test
    fun streamEndTriggersReconnect() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onPlaying()
        engine.attached!!.onEnded()
        assertTrue(controller.state.value is StreamState.Reconnecting)
    }

    @Test
    fun stallWatchdogForcesReconnect() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, stallMs = 5_000)
        controller.start(config)
        engine.attached!!.onBuffering()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(StreamState.Reconnecting(1, 3, 1_000, "Stream stalled"), controller.state.value)
    }

    @Test
    fun playingWithoutVideoFramesEventuallyReconnects() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, stallMs = 5_000)
        controller.start(config)
        engine.attached!!.onPlaying()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(StreamState.Reconnecting(1, 3, 1_000, "Stream stalled"), controller.state.value)
    }

    @Test
    fun advancingVideoFramesKeepAStaticSceneAlive() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, stallMs = 5_000)
        controller.start(config)
        engine.attached!!.onPlaying()
        repeat(20) {
            advanceTimeBy(1_000)
            engine.attached!!.onVideoFrame(it * 1_000_000L)
            runCurrent()
        }
        assertEquals(StreamState.Playing, controller.state.value)
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun repeatedPresentationTimestampDoesNotKeepFrozenStreamGreen() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, stallMs = 5_000)
        controller.start(config)
        engine.attached!!.onPlaying()
        engine.attached!!.onVideoFrame(1_000_000)
        runCurrent()
        repeat(5) {
            advanceTimeBy(1_000)
            engine.attached!!.onVideoFrame(1_000_000)
            runCurrent()
        }
        assertTrue(controller.state.value is StreamState.Reconnecting)
    }

    @Test
    fun streamDetailsAreClearedWhenSwitchingQualityAndPausing() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        val details = StreamDetails(FrameSize(1920, 1080), 25f, "H.264", 2_000_000, RtspTransport.TCP)
        engine.attached!!.onStreamDetails(details)
        assertEquals(details, controller.details.value)
        controller.selectQuality(StreamQuality.Sub)
        assertEquals(StreamDetails(), controller.details.value)
        assertEquals(null, controller.frameSize.value)
        engine.attached!!.onStreamDetails(details)
        controller.pause()
        assertEquals(StreamDetails(), controller.details.value)
    }

    @Test
    fun rebufferingAfterPlayingArmsWatchdog() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, stallMs = 5_000)
        controller.start(config)
        engine.attached!!.onPlaying()
        engine.attached!!.onBuffering()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(controller.state.value is StreamState.Reconnecting)
    }

    @Test
    fun switchingToSubReloadsWithSubUrl() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onPlaying()
        controller.selectQuality(StreamQuality.Sub)
        assertEquals("rtsp://h/sub", engine.loads.last().url)
        assertEquals(StreamState.Connecting, controller.state.value)
        controller.selectQuality(StreamQuality.Main)
        assertEquals("rtsp://h/main", engine.loads.last().url)
    }

    @Test
    fun subIgnoredWhenNoSubStream() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config.copy(subUrl = null))
        controller.selectQuality(StreamQuality.Sub)
        assertEquals(1, engine.loads.size)
        assertEquals(StreamQuality.Main, controller.active.value!!.quality)
    }

    @Test
    fun switchingTransportAndLatencyRestartsWithNewSettings() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        controller.setTransport(RtspTransport.UDP)
        assertEquals(RtspTransport.UDP, engine.loads.last().transport)
        controller.setLatency(2_000)
        assertEquals(StreamSource("rtsp://h/main", RtspTransport.UDP, 2_000), engine.loads.last())
        assertEquals(3, engine.loads.size)
    }

    @Test
    fun sameSettingDoesNotRestart() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        controller.setTransport(RtspTransport.TCP)
        controller.setLatency(500)
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun startingSameConfigAgainDoesNotRestart() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        controller.selectQuality(StreamQuality.Sub)
        controller.start(config)
        assertEquals(2, engine.loads.size)
        assertEquals(StreamQuality.Sub, controller.active.value!!.quality)
    }

    @Test
    fun pauseStopsEngineAndIgnoresLateCallbacks() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onPlaying()
        controller.pause()
        assertEquals(StreamState.Idle, controller.state.value)
        assertEquals(1, engine.stops)
        engine.attached!!.onError("late")
        engine.attached!!.onPlaying()
        assertEquals(StreamState.Idle, controller.state.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun pauseCancelsPendingReconnect() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onError("down")
        controller.pause()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun resumeReconnectsAfterPause() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        controller.pause()
        controller.resume()
        assertEquals(StreamState.Connecting, controller.state.value)
        assertEquals(2, engine.loads.size)
        controller.resume()
        assertEquals(2, engine.loads.size)
    }

    @Test
    fun releaseTearsDownEngineAndStopsEverything() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config)
        engine.attached!!.onError("down")
        controller.release()
        assertTrue(engine.released)
        assertEquals(null, engine.attached)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, engine.loads.size)
        controller.start(config)
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun muteIsForwardedToEngine() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.setMuted(true)
        assertEquals(true, engine.muted)
        controller.setMuted(false)
        assertEquals(false, engine.muted)
    }

    @Test
    fun startWithQualityLoadsThatStreamAndChangesRestartOnce() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(config, StreamQuality.Sub)
        assertEquals("rtsp://h/sub", engine.loads.last().url)
        controller.start(config, StreamQuality.Sub)
        assertEquals(1, engine.loads.size)
        controller.start(config, StreamQuality.Main)
        assertEquals("rtsp://h/main", engine.loads.last().url)
        assertEquals(2, engine.loads.size)
    }

    @Test
    fun zeroAttemptsFailsImmediatelyWithoutReconnect() = runTest {
        val engine = FakeEngine()
        val controller = controller(engine, policy = ReconnectPolicy(maxAttempts = 0))
        controller.start(config)
        engine.attached!!.onError("down")
        assertEquals(StreamState.Failed("down"), controller.state.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, engine.loads.size)
    }
}
