package uk.co.aaronburt.rtsp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uk.co.aaronburt.rtsp.ui.RtspApp
import uk.co.aaronburt.rtsp.ui.theme.RtspTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RtspApplication).container
        setContent {
            RtspTheme {
                RtspApp(container = container)
            }
        }
    }
}
