package uk.co.aaronburt.rtsp.recording

sealed interface RecordingStatus {
    data object Idle : RecordingStatus

    /** Recording was requested; the file starts at the next key frame. */
    data object Waiting : RecordingStatus

    data class Active(val startedAtMs: Long) : RecordingStatus
}

data class RecordingError(val cameraId: String, val message: String)
