package uk.co.aaronburt.rtsp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import uk.co.aaronburt.rtsp.player.createRtspPlayer

@Composable
fun StreamScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) { createRtspPlayer(context, url) }
    DisposableEffect(player) { onDispose { player.release() } }
    BackHandler(onBack = onClose)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { PlayerView(it).apply { this.player = player; useController = false } },
    )
}
