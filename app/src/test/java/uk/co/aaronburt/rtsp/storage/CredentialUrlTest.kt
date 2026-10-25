package uk.co.aaronburt.rtsp.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialUrlTest {
    @Test
    fun splitLeavesPlainUrlUntouched() {
        val parts = splitUrlCredentials("rtsp://192.168.1.20:554/stream1")
        assertEquals("rtsp://192.168.1.20:554/stream1", parts.url)
        assertNull(parts.username)
        assertNull(parts.password)
    }

    @Test
    fun splitExtractsAndDecodesCredentials() {
        val parts = splitUrlCredentials("rtsp://admin:p%40ss%3Aword@192.168.1.20:554/stream1?x=a@b")
        assertEquals("rtsp://192.168.1.20:554/stream1?x=a@b", parts.url)
        assertEquals("admin", parts.username)
        assertEquals("p@ss:word", parts.password)
    }

    @Test
    fun splitHandlesUserWithoutPassword() {
        val parts = splitUrlCredentials("rtsp://viewer@cam.local/live")
        assertEquals("rtsp://cam.local/live", parts.url)
        assertEquals("viewer", parts.username)
        assertNull(parts.password)
    }

    @Test
    fun splitHandlesIpv6Host() {
        val parts = splitUrlCredentials("rtsp://u:p@[fe80::1]:554/s")
        assertEquals("rtsp://[fe80::1]:554/s", parts.url)
        assertEquals("u", parts.username)
        assertEquals("p", parts.password)
    }

    @Test
    fun splitKeepsLiteralPlusInPassword() {
        val parts = splitUrlCredentials("rtsp://u:a+b@host/s")
        assertEquals("a+b", parts.password)
    }

    @Test
    fun injectEncodesReservedCharacters() {
        val url = injectUrlCredentials("rtsp://192.168.1.20:554/stream1", "ad min", "p@ss:w/rd%é")
        assertEquals("rtsp://ad%20min:p%40ss%3Aw%2Frd%25%C3%A9@192.168.1.20:554/stream1", url)
    }

    @Test
    fun injectRoundTripsThroughSplit() {
        val url = injectUrlCredentials("rtsp://host:8554/a/b?c=d", "us@er", "pä+ss word")
        val parts = splitUrlCredentials(url)
        assertEquals("rtsp://host:8554/a/b?c=d", parts.url)
        assertEquals("us@er", parts.username)
        assertEquals("pä+ss word", parts.password)
    }

    @Test
    fun injectWithoutUsernameReturnsCleanUrl() {
        assertEquals("rtsp://host/s", injectUrlCredentials("rtsp://old:pw@host/s", "", "ignored"))
    }

    @Test
    fun injectReplacesCredentialsAlreadyInUrl() {
        assertEquals("rtsp://new:pw2@host/s", injectUrlCredentials("rtsp://old:pw@host/s", "new", "pw2"))
    }

    @Test
    fun injectUsernameOnly() {
        assertEquals("rtsp://viewer@host/s", injectUrlCredentials("rtsp://host/s", "viewer", ""))
    }

    @Test
    fun injectLeavesNonRtspInputAlone() {
        assertEquals("http://host/s", injectUrlCredentials("http://host/s", "u", "p"))
    }
}
