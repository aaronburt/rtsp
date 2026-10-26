package uk.co.aaronburt.rtsp.recording

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.aaronburt.rtsp.player.VideoSampleTap

interface RecordingController {
    val errors: SharedFlow<RecordingError>

    fun status(cameraId: String): StateFlow<RecordingStatus>

    fun start(cameraId: String, title: String)

    fun stop(cameraId: String)

    /** The tap to attach to the player showing [cameraId]. */
    fun tapFor(cameraId: String): VideoSampleTap?
}

object NoRecordingController : RecordingController {
    private val idle = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle).asStateFlow()

    override val errors: SharedFlow<RecordingError> = MutableSharedFlow<RecordingError>().asSharedFlow()

    override fun status(cameraId: String): StateFlow<RecordingStatus> = idle

    override fun start(cameraId: String, title: String) = Unit

    override fun stop(cameraId: String) = Unit

    override fun tapFor(cameraId: String): VideoSampleTap? = null
}
