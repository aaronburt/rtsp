package uk.co.aaronburt.rtsp.ui.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.CameraRepository

enum class StreamQuality { Main, Sub }

data class LiveState(
    val camera: Camera? = null,
    val quality: StreamQuality = StreamQuality.Main,
    val muted: Boolean = true,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val detailsOpen: Boolean = false,
)

class LiveViewModel(
    private val cameraId: String,
    private val repository: CameraRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        LiveState(
            camera = repository.get(cameraId),
            muted = repository.get(cameraId)?.startMuted ?: true,
        ),
    )
    private var timer: Job? = null

    val state: StateFlow<LiveState> = mutableState

    init {
        viewModelScope.launch {
            repository.cameras.collect { cameras ->
                mutableState.update { it.copy(camera = cameras.firstOrNull { camera -> camera.id == cameraId }) }
            }
        }
    }

    fun setQuality(quality: StreamQuality) {
        mutableState.update { it.copy(quality = quality) }
    }

    fun toggleMuted() {
        mutableState.update { it.copy(muted = !it.muted) }
    }

    fun toggleDetails() {
        mutableState.update { it.copy(detailsOpen = !it.detailsOpen) }
    }

    fun toggleFavourite() {
        val camera = mutableState.value.camera ?: return
        repository.setFavourite(camera.id, !camera.favourite)
    }

    fun toggleRecording() {
        if (mutableState.value.recording) {
            timer?.cancel()
            mutableState.update { it.copy(recording = false, recordingSeconds = 0) }
        } else {
            mutableState.update { it.copy(recording = true, recordingSeconds = 0) }
            timer = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    mutableState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                }
            }
        }
    }

    companion object {
        const val ARG_ID = "id"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                LiveViewModel(checkNotNull(handle[ARG_ID]), container.cameraRepository)
            }
        }
    }
}
