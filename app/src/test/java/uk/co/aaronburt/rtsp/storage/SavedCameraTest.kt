package uk.co.aaronburt.rtsp.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedCameraTest {
    private val camera = SavedCamera(
        id = "cam",
        name = "Front",
        mainUrl = "rtsp://10.0.0.5:554/main",
        subUrl = "rtsp://10.0.0.5:554/sub",
        username = "admin",
        password = "p@ss",
    )

    @Test
    fun mainStreamUrlCarriesCredentials() {
        assertEquals("rtsp://admin:p%40ss@10.0.0.5:554/main", camera.streamUrl(StreamKind.Main))
    }

    @Test
    fun subStreamUrlCarriesCredentials() {
        assertEquals("rtsp://admin:p%40ss@10.0.0.5:554/sub", camera.streamUrl(StreamKind.Sub))
    }

    @Test
    fun subFallsBackToMainWhenMissing() {
        val noSub = camera.copy(subUrl = null)
        assertFalse(noSub.hasSubStream)
        assertEquals("rtsp://admin:p%40ss@10.0.0.5:554/main", noSub.streamUrl(StreamKind.Sub))
        assertFalse(camera.copy(subUrl = "  ").hasSubStream)
    }

    @Test
    fun storedUrlsStayCredentialFree() {
        assertEquals("rtsp://10.0.0.5:554/main", camera.mainUrl)
        assertTrue(camera.hasSubStream)
    }

    @Test
    fun toStringRedactsSecrets() {
        val text = camera.toString()
        assertFalse(text.contains("p@ss"))
        assertFalse(text.contains("admin"))
        assertFalse(text.contains("10.0.0.5"))
    }
}
