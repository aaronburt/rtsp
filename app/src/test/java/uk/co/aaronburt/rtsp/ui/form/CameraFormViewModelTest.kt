package uk.co.aaronburt.rtsp.ui.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.aaronburt.rtsp.data.FakeCameraRepository
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.data.sampleCameras

class CameraFormViewModelTest {
    @Test
    fun validatesNameAndUrl() {
        assertTrue(validateForm("", "rtsp://host/s").nameMissing)
        assertTrue(validateForm("Cam", "http://host/s").urlInvalid)
        assertTrue(validateForm("Cam", "rtsp://").urlInvalid)
        assertFalse(validateForm("Cam", "rtsp://user:pw@host/s").any)
    }

    @Test
    fun rejectsInvalidSave() {
        val repository = FakeCameraRepository(emptyList())
        val form = CameraFormViewModel(null, repository)
        assertFalse(form.save())
        assertTrue(form.state.value.showErrors)
        assertTrue(repository.cameras.value.isEmpty())
    }

    @Test
    fun savesNewCameraAndMovesCredentialsOutOfUrl() {
        val repository = FakeCameraRepository(emptyList())
        val form = CameraFormViewModel(null, repository)
        form.setName("  Shed ")
        form.setUrl("rtsp://admin:secret@10.0.0.9:554/s")
        form.setTransport(Transport.Udp)
        form.setLatency(500)
        assertTrue(form.save())
        val saved = repository.cameras.value.single()
        assertEquals("Shed", saved.name)
        assertEquals("rtsp://10.0.0.9:554/s", saved.url)
        assertEquals("admin", saved.username)
        assertEquals("secret", saved.password)
        assertEquals(Transport.Udp, saved.transport)
        assertEquals(500, saved.latencyMs)
    }

    @Test
    fun editKeepsIdentityAndStatus() {
        val repository = FakeCameraRepository(sampleCameras())
        val form = CameraFormViewModel("back-gate", repository)
        assertTrue(form.state.value.isEdit)
        form.setName("Rear Gate")
        assertTrue(form.save())
        val saved = checkNotNull(repository.get("back-gate"))
        assertEquals("Rear Gate", saved.name)
        assertFalse(saved.online)
        assertNotNull(saved.offlineSince)
        assertEquals(sampleCameras().size, repository.cameras.value.size)
    }

    @Test
    fun deleteRemovesEditedCamera() {
        val repository = FakeCameraRepository(sampleCameras())
        CameraFormViewModel("garden", repository).delete()
        assertEquals(null, repository.get("garden"))
    }

    @Test
    fun subUrlIsOptionalButMustBeRtspWhenGiven() {
        assertFalse(validateForm("Cam", "rtsp://host/s", "").any)
        assertFalse(validateForm("Cam", "rtsp://host/s", "rtsp://u:p@host/sub").any)
        assertTrue(validateForm("Cam", "rtsp://host/s", "http://host/sub").subUrlInvalid)
    }

    @Test
    fun savesSubUrlWithoutCredentials() {
        val repository = FakeCameraRepository(emptyList())
        val form = CameraFormViewModel(null, repository)
        form.setName("Shed")
        form.setUrl("rtsp://10.0.0.9/main")
        form.setSubUrl("rtsp://a:b@10.0.0.9/sub")
        assertTrue(form.save())
        assertEquals("rtsp://10.0.0.9/sub", repository.cameras.value.single().subUrl)
    }

    @Test
    fun blankSubUrlIsStoredAsNull() {
        val repository = FakeCameraRepository(emptyList())
        val form = CameraFormViewModel(null, repository)
        form.setName("Shed")
        form.setUrl("rtsp://10.0.0.9/main")
        form.setSubUrl("  ")
        assertTrue(form.save())
        assertEquals(null, repository.cameras.value.single().subUrl)
    }
}
