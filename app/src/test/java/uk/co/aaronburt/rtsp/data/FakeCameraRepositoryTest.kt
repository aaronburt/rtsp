package uk.co.aaronburt.rtsp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCameraRepositoryTest {
    private fun repository() = FakeCameraRepository(sampleCameras())

    @Test
    fun savesNewCamera() {
        val repository = repository()
        val before = repository.cameras.value.size
        repository.save(Camera(id = repository.newId(), name = "Shed", url = "rtsp://10.0.0.9/s"))
        assertEquals(before + 1, repository.cameras.value.size)
    }

    @Test
    fun updatesExistingCamera() {
        val repository = repository()
        val original = checkNotNull(repository.get("garden"))
        repository.save(original.copy(name = "Back Garden"))
        assertEquals("Back Garden", repository.get("garden")?.name)
        assertEquals(repository.cameras.value.size, sampleCameras().size)
    }

    @Test
    fun deletesCamera() {
        val repository = repository()
        repository.delete("garage")
        assertNull(repository.get("garage"))
    }

    @Test
    fun togglesFavourite() {
        val repository = repository()
        repository.setFavourite("garden", true)
        assertTrue(checkNotNull(repository.get("garden")).favourite)
    }

    @Test
    fun retryBringsCameraOnline() {
        val repository = repository()
        assertFalse(checkNotNull(repository.get("back-gate")).online)
        repository.retry("back-gate")
        val camera = checkNotNull(repository.get("back-gate"))
        assertTrue(camera.online)
        assertNull(camera.offlineSince)
    }

    @Test
    fun generatesDistinctIds() {
        val repository = repository()
        assertNotNull(repository.newId())
        assertFalse(repository.newId() == repository.newId())
    }
}
