package uk.co.aaronburt.rtsp.data

import kotlinx.coroutines.flow.StateFlow

interface CameraRepository {
    val cameras: StateFlow<List<Camera>>

    fun get(id: String): Camera?

    fun save(camera: Camera)

    fun delete(id: String)

    fun setFavourite(id: String, favourite: Boolean)

    fun retry(id: String)

    fun refresh() {}

    fun reportConnection(id: String, online: Boolean) {}

    fun newId(): String
}
