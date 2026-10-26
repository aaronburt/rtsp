package uk.co.aaronburt.rtsp.ui.live

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import uk.co.aaronburt.rtsp.player.StreamStatus
import uk.co.aaronburt.rtsp.player.toStatus
import uk.co.aaronburt.rtsp.player.withCredentials
import uk.co.aaronburt.rtsp.snapshot.SurfaceCapture
import uk.co.aaronburt.rtsp.ui.PlayerSurface
import uk.co.aaronburt.rtsp.ui.components.LastSeenChip
import uk.co.aaronburt.rtsp.ui.components.LocalRecordingController
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotRecorder
import uk.co.aaronburt.rtsp.ui.components.SnapshotImage
import uk.co.aaronburt.rtsp.ui.components.rememberSnapshot

private const val RECONNECT_ATTEMPTS = 10
private const val SNAPSHOT_FIRST_FRAME_DELAY_MS = 2_000L
private const val SNAPSHOT_REFRESH_MS = 60_000L

val Media3PlayerSlot: PlayerSlot = { camera, quality, muted, modifier, onStatus ->
    Media3Player(camera, quality, muted, modifier, onStatus)
}

@Composable
private fun Media3Player(
    camera: Camera,
    quality: StreamQuality,
    muted: Boolean,
    modifier: Modifier,
    onStatus: (StreamStatus) -> Unit,
) {
    val context = LocalContext.current
    val scope = remember(camera.id) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val recording = LocalRecordingController.current
    val controller = remember(scope) {
        val attempts = if (camera.autoReconnect) RECONNECT_ATTEMPTS else 0
        LiveStreamController(
            Media3Engine(context, recording.tapFor(camera.id)),
            scope,
            ReconnectPolicy(maxAttempts = attempts),
        )
    }
    val recorder = LocalSnapshotRecorder.current
    val capture = remember(camera.id) { SurfaceCapture() }
    DisposableEffect(controller) {
        onDispose {
            if (controller.state.value == StreamState.Playing) recorder.record(camera.id, capture)
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
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (controller.state.value == StreamState.Playing) recorder.record(camera.id, capture)
        controller.pause()
    }
    LaunchedEffect(controller, recorder, capture) {
        while (true) {
            controller.state.first { it == StreamState.Playing }
            delay(SNAPSHOT_FIRST_FRAME_DELAY_MS)
            if (controller.state.value == StreamState.Playing) recorder.record(camera.id, capture)
            delay(SNAPSHOT_REFRESH_MS)
        }
    }

    val state by controller.state.collectAsStateWithLifecycle()
    val currentOnStatus by rememberUpdatedState(onStatus)
    LaunchedEffect(state) { currentOnStatus(state.toStatus()) }
    val snapshot = rememberSnapshot(camera.id)
    val backdropAlpha by animateFloatAsState(
        targetValue = if (state == StreamState.Playing) 0f else 1f,
        label = "snapshotBackdrop",
    )
    Box(modifier = modifier) {
        PlayerSurface(controller = controller, modifier = Modifier.matchParentSize(), capture = capture)
        if (snapshot != null && backdropAlpha > 0f) {
            Box(modifier = Modifier.matchParentSize().alpha(backdropAlpha)) {
                SnapshotImage(snapshot, Modifier.matchParentSize())
                LastSeenChip(snapshot, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
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
