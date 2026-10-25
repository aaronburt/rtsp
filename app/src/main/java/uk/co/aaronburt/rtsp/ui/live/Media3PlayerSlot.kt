package uk.co.aaronburt.rtsp.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.data.splitCredentials
import uk.co.aaronburt.rtsp.player.LiveStreamController
import uk.co.aaronburt.rtsp.player.Media3Engine
import uk.co.aaronburt.rtsp.player.ReconnectPolicy
import uk.co.aaronburt.rtsp.player.RtspTransport
import uk.co.aaronburt.rtsp.player.StreamConfig
import uk.co.aaronburt.rtsp.player.StreamQuality
import uk.co.aaronburt.rtsp.player.StreamState
import uk.co.aaronburt.rtsp.player.withCredentials
import uk.co.aaronburt.rtsp.ui.PlayerSurface

private const val RECONNECT_ATTEMPTS = 10

val Media3PlayerSlot: PlayerSlot = { camera, quality, muted, modifier ->
    Media3Player(camera, quality, muted, modifier)
}

@Composable
private fun Media3Player(
    camera: Camera,
    quality: StreamQuality,
    muted: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val controller = remember(camera.id) {
        val attempts = if (camera.autoReconnect) RECONNECT_ATTEMPTS else 0
        LiveStreamController(Media3Engine(context), scope, ReconnectPolicy(maxAttempts = attempts))
    }
    DisposableEffect(controller) {
        onDispose {
            controller.release()
            scope.cancel()
        }
    }

    val config = remember(camera.url, camera.username, camera.password, camera.transport, camera.latencyMs) {
        camera.toStreamConfig()
    }
    LaunchedEffect(controller, muted) { controller.setMuted(muted) }
    LaunchedEffect(controller, config, quality) { controller.start(config, quality) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.resume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause() }

    val state by controller.state.collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        PlayerSurface(controller = controller, modifier = Modifier.matchParentSize())
        StatusPanel(state = state, onRetry = controller::retry, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun StatusPanel(state: StreamState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (state) {
        StreamState.Playing, StreamState.Idle -> return
        StreamState.Connecting -> stringResource(R.string.player_connecting)
        StreamState.Buffering -> stringResource(R.string.player_buffering)
        is StreamState.Reconnecting -> stringResource(
            R.string.player_reconnecting,
            ((state.delayMs + 999) / 1000).toInt(),
            state.attempt,
            state.maxAttempts,
        )
        is StreamState.Failed -> stringResource(R.string.player_failed)
    }
    val detail = when (state) {
        is StreamState.Reconnecting -> state.reason
        is StreamState.Failed -> state.reason
        else -> null
    }
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state !is StreamState.Failed) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (state is StreamState.Failed) {
            Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
        }
    }
}

fun Camera.toStreamConfig(): StreamConfig {
    val cleaned = splitCredentials(url).url
    return StreamConfig(
        name = name,
        mainUrl = withCredentials(cleaned, username, password),
        subUrl = subUrl?.takeIf { it.isNotBlank() }?.let { withCredentials(splitCredentials(it).url, username, password) },
        transport = when (transport) {
            Transport.Udp -> RtspTransport.UDP
            Transport.Tcp, Transport.Auto -> RtspTransport.TCP
        },
        latencyMs = latencyMs,
    )
}
