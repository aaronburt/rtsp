package uk.co.aaronburt.rtsp.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlTest {
    @Test
    fun acceptsRtspUrl() {
        assertTrue(isRtspUrl("rtsp://192.168.1.10:554/stream1"))
    }

    @Test
    fun rejectsOtherSchemes() {
        assertFalse(isRtspUrl("http://192.168.1.10/stream1"))
    }

    @Test
    fun rejectsEmptyInput() {
        assertFalse(isRtspUrl(""))
    }
}
