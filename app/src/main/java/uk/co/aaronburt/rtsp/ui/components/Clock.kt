package uk.co.aaronburt.rtsp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun rememberClock(): String {
    var now by remember { mutableStateOf(LocalTime.now().format(clockFormat)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now().format(clockFormat)
            delay(1000)
        }
    }
    return now
}

fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
