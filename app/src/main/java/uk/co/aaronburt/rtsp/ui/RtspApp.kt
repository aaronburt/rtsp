package uk.co.aaronburt.rtsp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.aaronburt.rtsp.player.StreamConfig
import uk.co.aaronburt.rtsp.player.isRtspUrl

@Composable
fun RtspApp() {
    var url by rememberSaveable { mutableStateOf("") }
    var subUrl by rememberSaveable { mutableStateOf("") }
    var playing by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (playing) {
            LiveViewScreen(
                config = StreamConfig(
                    name = "Camera",
                    mainUrl = url,
                    subUrl = subUrl.ifBlank { null },
                ),
                onClose = { playing = false },
            )
        } else {
            Column(
                modifier = Modifier.safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("RTSP", style = MaterialTheme.typography.headlineLarge)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Main stream URL") },
                    placeholder = { Text("rtsp://192.168.1.10:554/stream1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    label = { Text("Sub stream URL (optional)") },
                    placeholder = { Text("rtsp://192.168.1.10:554/stream2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { playing = true },
                    enabled = isRtspUrl(url) && (subUrl.isBlank() || isRtspUrl(subUrl)),
                ) {
                    Text("Watch")
                }
            }
        }
    }
}
