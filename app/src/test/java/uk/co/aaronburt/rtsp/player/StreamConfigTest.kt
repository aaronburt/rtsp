package uk.co.aaronburt.rtsp.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamConfigTest {
    @Test
    fun subQualityUsesSubUrlWhenPresent() {
        val config = StreamConfig("cam", "rtsp://h/main", "rtsp://h/sub")
        assertEquals("rtsp://h/main", config.urlFor(StreamQuality.Main))
        assertEquals("rtsp://h/sub", config.urlFor(StreamQuality.Sub))
        assertTrue(config.hasSub)
    }

    @Test
    fun subQualityFallsBackToMainWhenMissing() {
        val config = StreamConfig("cam", "rtsp://h/main", subUrl = " ")
        assertEquals("rtsp://h/main", config.urlFor(StreamQuality.Sub))
        assertFalse(config.hasSub)
    }

    @Test
    fun frameSizeAspectRatioUsesPixelRatio() {
        assertEquals(16f / 9f, FrameSize(1920, 1080).aspectRatio, 0.001f)
        assertEquals(2f, FrameSize(1000, 1000, 2f).aspectRatio, 0.001f)
        assertEquals(0f, FrameSize(100, 0).aspectRatio, 0f)
    }
}
