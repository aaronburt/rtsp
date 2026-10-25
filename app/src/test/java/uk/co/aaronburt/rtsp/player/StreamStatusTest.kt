package uk.co.aaronburt.rtsp.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamStatusTest {
    @Test
    fun playingIsLive() {
        assertEquals(StreamStatus.Live, StreamState.Playing.toStatus())
    }

    @Test
    fun connectingBufferingIdleAndReconnectingArePending() {
        assertEquals(StreamStatus.Pending, StreamState.Idle.toStatus())
        assertEquals(StreamStatus.Pending, StreamState.Connecting.toStatus())
        assertEquals(StreamStatus.Pending, StreamState.Buffering.toStatus())
        assertEquals(StreamStatus.Pending, StreamState.Reconnecting(1, 10, 1_000, "x").toStatus())
    }

    @Test
    fun failedIsError() {
        assertEquals(StreamStatus.Error, StreamState.Failed("x").toStatus())
    }
}
