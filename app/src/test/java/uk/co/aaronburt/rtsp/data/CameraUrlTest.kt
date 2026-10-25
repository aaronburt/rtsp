package uk.co.aaronburt.rtsp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraUrlTest {
    @Test
    fun splitsUserAndPassword() {
        val parts = splitCredentials("rtsp://admin:secret@192.168.1.20:554/stream1")
        assertEquals("rtsp://192.168.1.20:554/stream1", parts.url)
        assertEquals("admin", parts.username)
        assertEquals("secret", parts.password)
    }

    @Test
    fun splitsUserWithoutPassword() {
        val parts = splitCredentials("rtsp://admin@192.168.1.20/stream1")
        assertEquals("rtsp://192.168.1.20/stream1", parts.url)
        assertEquals("admin", parts.username)
        assertNull(parts.password)
    }

    @Test
    fun decodesPercentEscapes() {
        val parts = splitCredentials("rtsp://admin:p%40ss@host/path")
        assertEquals("p@ss", parts.password)
    }

    @Test
    fun keepsAtSignInPathIntact() {
        val parts = splitCredentials("rtsp://host/path@name")
        assertEquals("rtsp://host/path@name", parts.url)
        assertNull(parts.username)
    }

    @Test
    fun leavesPlainUrlUntouched() {
        val parts = splitCredentials(" rtsp://192.168.1.20:554/stream1 ")
        assertEquals("rtsp://192.168.1.20:554/stream1", parts.url)
        assertNull(parts.username)
        assertNull(parts.password)
    }

    @Test
    fun leavesOtherSchemesUntouched() {
        val parts = splitCredentials("http://admin:secret@host/")
        assertEquals("http://admin:secret@host/", parts.url)
        assertNull(parts.username)
    }
}
