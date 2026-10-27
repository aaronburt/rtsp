package uk.co.aaronburt.rtsp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

// Returned as State so only the Text that reads it recomposes each second, not the whole screen.
@Composable
fun rememberClock(enabled: Boolean = true): State<String> {
    val now = remember { mutableStateOf(LocalTime.now().format(clockFormat)) }
    LaunchedEffect(enabled) {
        while (enabled) {
            now.value = LocalTime.now().format(clockFormat)
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
