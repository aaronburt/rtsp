package uk.co.aaronburt.rtsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import uk.co.aaronburt.rtsp.ui.RtspApp
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotRecorder
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotStore
import uk.co.aaronburt.rtsp.ui.live.Media3PlayerSlot
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RtspApplication).container
        setContent {
            RtspTheme {
                CompositionLocalProvider(
                    LocalSnapshotStore provides container.snapshotStore,
                    LocalSnapshotRecorder provides container.snapshotRecorder,
                ) {
                    RtspApp(container = container, playerSlot = Media3PlayerSlot)
                }
            }
        }
    }
}
