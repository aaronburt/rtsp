package uk.co.aaronburt.rtsp.data

import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.aaronburt.rtsp.storage.CameraTransport
import uk.co.aaronburt.rtsp.storage.SavedCamera
import uk.co.aaronburt.rtsp.storage.SavedCameraRepository

private class MemorySavedRepository(initial: List<SavedCamera> = emptyList()) : SavedCameraRepository {
    private val state = MutableStateFlow(initial)
    private val ready = MutableStateFlow(true)

    override val cameras: StateFlow<List<SavedCamera>> = state
    override val loaded: StateFlow<Boolean> = ready

    override fun get(id: String): SavedCamera? = state.value.firstOrNull { it.id == id }

    override fun save(camera: SavedCamera) {
        state.update { list -> list.filterNot { it.id == camera.id } + camera }
    }

    override fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override fun setFavourite(id: String, favourite: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(favourite = favourite) else it } }
    }

    override fun newId(): String = UUID.randomUUID().toString()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentCameraRepositoryTest {
    private fun TestScope.repository(saved: SavedCameraRepository) =
        PersistentCameraRepository(saved, TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope)

    private val camera = Camera(
        id = "a",
        name = "Front",
        url = "rtsp://10.0.0.2:554/main",
        subUrl = "rtsp://10.0.0.2:554/sub",
        username = "admin",
        password = "secret",
        transport = Transport.Udp,
        latencyMs = 350,
        autoReconnect = false,
        startMuted = false,
        favourite = true,
    )

    @Test
    fun savedCameraRoundTripsThroughTheAdapter() = runTest {
        val saved = MemorySavedRepository()
        val repository = repository(saved)
        repository.save(camera)
        assertEquals(camera, repository.get("a"))
        assertEquals(listOf(camera), repository.cameras.value)
        val stored = saved.get("a")!!
        assertEquals("rtsp://10.0.0.2:554/sub", stored.subUrl)
        assertEquals(CameraTransport.Udp, stored.transport)
        assertEquals("secret", stored.password)
    }

    @Test
    fun existingStoredCamerasAreVisibleImmediately() = runTest {
        val saved = MemorySavedRepository(
            listOf(SavedCamera(id = "x", name = "Shed", mainUrl = "rtsp://h/s", transport = CameraTransport.Auto)),
        )
        val repository = repository(saved)
        val loaded = repository.cameras.value.single()
        assertEquals("Shed", loaded.name)
        assertEquals(Transport.Auto, loaded.transport)
        assertTrue(loaded.online)
        assertNull(loaded.subUrl)
    }

    @Test
    fun deleteAndFavouriteAreForwarded() = runTest {
        val saved = MemorySavedRepository()
        val repository = repository(saved)
        repository.save(camera.copy(favourite = false))
        repository.setFavourite("a", true)
        assertTrue(repository.get("a")!!.favourite)
        assertTrue(repository.cameras.value.single().favourite)
        repository.delete("a")
        assertNull(repository.get("a"))
        assertTrue(repository.cameras.value.isEmpty())
    }

    @Test
    fun newIdsAreUnique() = runTest {
        val repository = repository(MemorySavedRepository())
        assertTrue(repository.newId() != repository.newId())
    }
}
