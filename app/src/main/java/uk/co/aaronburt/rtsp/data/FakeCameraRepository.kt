package uk.co.aaronburt.rtsp.data

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeCameraRepository(
    seed: List<Camera> = sampleCameras(),
) : CameraRepository {
    private val state = MutableStateFlow(seed)

    override val cameras: StateFlow<List<Camera>> = state

    override fun get(id: String): Camera? = state.value.firstOrNull { it.id == id }

    override fun save(camera: Camera) {
        state.update { list ->
            if (list.any { it.id == camera.id }) {
                list.map { if (it.id == camera.id) camera else it }
            } else {
                list + camera
            }
        }
    }

    override fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override fun setFavourite(id: String, favourite: Boolean) {
        update(id) { it.copy(favourite = favourite) }
    }

    override fun retry(id: String) {
        update(id) { it.copy(online = true, offlineSince = null, lastFrameAt = "now") }
    }

    override fun newId(): String = UUID.randomUUID().toString()

    private fun update(id: String, change: (Camera) -> Camera) {
        state.update { list -> list.map { if (it.id == id) change(it) else it } }
    }
}

fun sampleCameras(): List<Camera> = listOf(
    Camera(
        id = "front-door",
        name = "Front Door",
        url = "rtsp://192.168.1.20:554/stream1",
        username = "viewer",
        favourite = true,
        lastFrameAt = "13:15:42",
    ),
    Camera(
        id = "driveway",
        name = "Driveway",
        url = "rtsp://192.168.1.21:554/stream1",
        username = "viewer",
        favourite = true,
        lastFrameAt = "13:15:42",
    ),
    Camera(
        id = "garden",
        name = "Garden",
        url = "rtsp://192.168.1.22:554/stream1",
        username = "viewer",
        transport = Transport.Udp,
        lastFrameAt = "13:15:42",
    ),
    Camera(
        id = "garage",
        name = "Garage",
        url = "rtsp://192.168.1.23:554/stream1",
        username = "viewer",
        lastFrameAt = "13:15:42",
    ),
    Camera(
        id = "hallway",
        name = "Hallway",
        url = "rtsp://192.168.1.24:554/stream1",
        username = "viewer",
        transport = Transport.Auto,
        lastFrameAt = "13:15:42",
    ),
    Camera(
        id = "back-gate",
        name = "Back Gate",
        url = "rtsp://192.168.1.25:554/stream1",
        username = "viewer",
        online = false,
        offlineSince = "13:02",
    ),
)
