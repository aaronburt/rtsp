package uk.co.aaronburt.rtsp.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import uk.co.aaronburt.rtsp.recording.NoRecordingController
import uk.co.aaronburt.rtsp.recording.RecordingController

val LocalRecordingController = staticCompositionLocalOf<RecordingController> { NoRecordingController }
