package uk.co.aaronburt.rtsp.ui.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.CameraRepository
import uk.co.aaronburt.rtsp.player.StreamStatus

enum class CameraFilter { All, Offline, Favourites }

data class CamerasState(
    val all: List<Camera> = emptyList(),
    val visible: List<Camera> = emptyList(),
    val filter: CameraFilter = CameraFilter.All,
    val query: String = "",
    val searching: Boolean = false,
    val editing: Boolean = false,
) {
    val offlineCount: Int get() = all.count { !it.online }
}

fun visibleCameras(cameras: List<Camera>, filter: CameraFilter, query: String): List<Camera> {
    val needle = query.trim()
    return cameras
        .filter {
            when (filter) {
                CameraFilter.All -> true
                CameraFilter.Offline -> !it.online
                CameraFilter.Favourites -> it.favourite
            }
        }
        .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
        .sortedBy { it.online }
}

class CamerasViewModel(private val repository: CameraRepository) : ViewModel() {
    private val filter = MutableStateFlow(CameraFilter.All)
    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    private val editing = MutableStateFlow(false)

    val state: StateFlow<CamerasState> = combine(
        repository.cameras,
        filter,
        query,
        searching,
        editing,
    ) { cameras, filter, query, searching, editing ->
        buildState(cameras, filter, query, searching, editing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(
            repository.cameras.value,
            filter.value,
            query.value,
            searching.value,
            editing.value,
        ),
    )

    fun setFilter(value: CameraFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleSearch() {
        searching.update { !it }
        if (!searching.value) query.value = ""
    }

    fun toggleEditing() {
        editing.update { !it }
    }

    fun retry(id: String) {
        repository.retry(id)
    }

    fun refresh() = repository.refresh()

    fun reportConnection(id: String, status: StreamStatus) {
        when (status) {
            StreamStatus.Live -> repository.reportConnection(id, true)
            StreamStatus.Error -> repository.reportConnection(id, false)
            StreamStatus.Pending -> Unit
        }
    }

    private fun buildState(
        cameras: List<Camera>,
        filter: CameraFilter,
        query: String,
        searching: Boolean,
        editing: Boolean,
    ) = CamerasState(
        all = cameras,
        visible = visibleCameras(cameras, filter, query),
        filter = filter,
        query = query,
        searching = searching,
        editing = editing,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CamerasViewModel(container.cameraRepository) }
        }
    }
}
