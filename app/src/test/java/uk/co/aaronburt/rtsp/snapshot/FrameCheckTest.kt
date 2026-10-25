package uk.co.aaronburt.rtsp.snapshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCheckTest {
    @Test
    fun emptyPixelsAreBlank() {
        assertTrue(isBlankFrame(IntArray(0)))
    }

    @Test
    fun allBlackIsBlank() {
        assertTrue(isBlankFrame(IntArray(144) { 0xFF000000.toInt() }))
    }

    @Test
    fun nearBlackIsBlank() {
        assertTrue(isBlankFrame(IntArray(144) { 0xFF050505.toInt() }))
    }

    @Test
    fun oneBrightPixelIsNotBlank() {
        val pixels = IntArray(144) { 0xFF000000.toInt() }
        pixels[10] = 0xFF808080.toInt()
        assertFalse(isBlankFrame(pixels))
    }

    @Test
    fun colouredFrameIsNotBlank() {
        assertFalse(isBlankFrame(IntArray(144) { 0xFF204060.toInt() }))
    }
}
