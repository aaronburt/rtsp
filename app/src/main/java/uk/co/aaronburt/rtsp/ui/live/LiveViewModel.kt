package uk.co.aaronburt.rtsp.ui.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.CameraRepository
import uk.co.aaronburt.rtsp.player.StreamQuality
import uk.co.aaronburt.rtsp.recording.NoRecordingController
import uk.co.aaronburt.rtsp.recording.RecordingController
import uk.co.aaronburt.rtsp.recording.RecordingStatus

data class LiveState(
    val camera: Camera? = null,
    val quality: StreamQuality = StreamQuality.Main,
    val muted: Boolean = true,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val recordingError: String? = null,
    val detailsOpen: Boolean = false,
)

class LiveViewModel(
    private val cameraId: String,
    private val repository: CameraRepository,
    private val recorder: RecordingController = NoRecordingController,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        LiveState(
            camera = repository.get(cameraId),
            muted = repository.get(cameraId)?.startMuted ?: true,
        ),
    )
    val state: StateFlow<LiveState> = mutableState

    init {
        observeRecording()
        viewModelScope.launch {
            repository.cameras.collect { cameras ->
                mutableState.update { it.copy(camera = cameras.firstOrNull { camera -> camera.id == cameraId }) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecording() {
        viewModelScope.launch {
            recorder.status(cameraId)
                .flatMapLatest { status -> elapsedSeconds(status) }
                .collect { seconds ->
                    mutableState.update { it.copy(recording = seconds != null, recordingSeconds = seconds ?: 0) }
                }
        }
        viewModelScope.launch {
            recorder.errors.collect { error ->
                if (error.cameraId == cameraId) mutableState.update { it.copy(recordingError = error.message) }
            }
        }
    }

    private fun elapsedSeconds(status: RecordingStatus): Flow<Int?> = when (status) {
        RecordingStatus.Idle -> flowOf(null)
        RecordingStatus.Waiting -> flowOf(0)
        is RecordingStatus.Active -> flow {
            while (true) {
                emit(((clock() - status.startedAtMs) / 1000).toInt().coerceAtLeast(0))
                delay(1000)
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
        val camera = mutableState.value.camera ?: return
        if (mutableState.value.recording) {
            recorder.stop(camera.id)
        } else {
            mutableState.update { it.copy(recordingError = null) }
            recorder.start(camera.id, camera.name)
        }
    }

    override fun onCleared() {
        recorder.stop(cameraId)
    }

    companion object {
        const val ARG_ID = "id"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                LiveViewModel(checkNotNull(handle[ARG_ID]), container.cameraRepository, container.recordingController)
            }
        }
    }
}
