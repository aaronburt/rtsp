package uk.co.aaronburt.rtsp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.aaronburt.rtsp.storage.CameraTransport
import uk.co.aaronburt.rtsp.storage.SavedCamera
import uk.co.aaronburt.rtsp.storage.SavedCameraRepository

class PersistentCameraRepository(
    private val saved: SavedCameraRepository,
    scope: CoroutineScope,
) : CameraRepository {
    override val cameras: StateFlow<List<Camera>> = saved.cameras
        .map { list -> list.map(::toCamera) }
        .stateIn(scope, SharingStarted.Eagerly, saved.cameras.value.map(::toCamera))

    override fun get(id: String): Camera? = saved.get(id)?.let(::toCamera)

    override fun save(camera: Camera) {
        saved.save(
            SavedCamera(
                id = camera.id,
                name = camera.name,
                mainUrl = camera.url,
                subUrl = camera.subUrl,
                username = camera.username,
                password = camera.password,
                transport = CameraTransport.entries.first { it.name == camera.transport.name },
                latencyMs = camera.latencyMs,
                autoReconnect = camera.autoReconnect,
                startMuted = camera.startMuted,
                favourite = camera.favourite,
            ),
        )
    }

    override fun delete(id: String) = saved.delete(id)

    override fun setFavourite(id: String, favourite: Boolean) = saved.setFavourite(id, favourite)

    override fun retry(id: String) = Unit

    override fun newId(): String = saved.newId()

    private fun toCamera(camera: SavedCamera) = Camera(
        id = camera.id,
        name = camera.name,
        url = camera.mainUrl,
        subUrl = camera.subUrl,
        username = camera.username,
        password = camera.password,
        transport = Transport.entries.first { it.name == camera.transport.name },
        latencyMs = camera.latencyMs,
        autoReconnect = camera.autoReconnect,
        startMuted = camera.startMuted,
        favourite = camera.favourite,
    )
}
