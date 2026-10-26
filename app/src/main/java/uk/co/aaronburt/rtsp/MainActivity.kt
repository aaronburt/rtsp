package uk.co.aaronburt.rtsp

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import uk.co.aaronburt.rtsp.ui.RtspApp
import uk.co.aaronburt.rtsp.ui.components.LocalRecordingController
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotRecorder
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotStore
import uk.co.aaronburt.rtsp.ui.live.Media3PlayerSlot
import uk.co.aaronburt.rtsp.ui.live.ActivityPictureInPicture
import uk.co.aaronburt.rtsp.ui.live.LocalPictureInPicture
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

class MainActivity : ComponentActivity() {
    val pictureInPicture by lazy { ActivityPictureInPicture(this) }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPicture.onModeChanged(isInPictureInPictureMode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RtspApplication).container
        setContent {
            RtspTheme {
                CompositionLocalProvider(
                    LocalSnapshotStore provides container.snapshotStore,
                    LocalSnapshotRecorder provides container.snapshotRecorder,
                    LocalRecordingController provides container.recordingController,
                    LocalPictureInPicture provides pictureInPicture,
                ) {
                    RtspApp(container = container, playerSlot = Media3PlayerSlot)
                }
            }
        }
    }
}
