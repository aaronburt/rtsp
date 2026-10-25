package uk.co.aaronburt.rtsp.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test
    fun credentialsAreInjectedAndEncoded() {
        assertEquals("rtsp://admin:p%40ss%20w%3Ard@10.0.0.2:554/s1", withCredentials("rtsp://10.0.0.2:554/s1", "admin", "p@ss w:rd"))
    }

    @Test
    fun credentialsWithoutPasswordOmitColon() {
        assertEquals("rtsp://viewer@10.0.0.2/s1", withCredentials("rtsp://10.0.0.2/s1", "viewer", ""))
    }

    @Test
    fun existingUserInfoOrBlankUserLeavesUrlAlone() {
        assertEquals("rtsp://a:b@10.0.0.2/s1", withCredentials("rtsp://a:b@10.0.0.2/s1", "x", "y"))
        assertEquals("rtsp://10.0.0.2/s1", withCredentials("rtsp://10.0.0.2/s1", "", "y"))
    }

    @Test
    fun atSignInPathIsNotUserInfo() {
        assertEquals("rtsp://u@host/a@b", withCredentials("rtsp://host/a@b", "u", ""))
    }
}
