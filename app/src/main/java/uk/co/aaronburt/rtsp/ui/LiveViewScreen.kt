package uk.co.aaronburt.rtsp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import uk.co.aaronburt.rtsp.player.LATENCY_PRESETS_MS
import uk.co.aaronburt.rtsp.player.LiveStreamController
import uk.co.aaronburt.rtsp.player.Media3Engine
import uk.co.aaronburt.rtsp.player.RtspTransport
import uk.co.aaronburt.rtsp.player.StreamConfig
import uk.co.aaronburt.rtsp.player.StreamQuality
import uk.co.aaronburt.rtsp.player.StreamState

private val ScrimColor = Color.Black.copy(alpha = 0.55f)

@Composable
fun LiveViewScreen(config: StreamConfig, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val controller = remember { LiveStreamController(Media3Engine(context), scope) }
    DisposableEffect(controller) {
        onDispose {
            controller.release()
            scope.cancel()
        }
    }

    val state by controller.state.collectAsStateWithLifecycle()
    val active by controller.active.collectAsStateWithLifecycle()
    var overlayVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(config) { controller.start(config) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.resume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause() }
    ImmersiveMode(activity)
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { overlayVisible = !overlayVisible },
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(controller = controller)

        StatusPanel(state = state, onRetry = controller::retry)

        if (overlayVisible) {
            val current = active
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScrimColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onClose) { Text("Back", color = Color.White) }
                    Text(
                        text = current?.config?.name ?: config.name,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = state.badge(), color = Color.White)
                }
                if (current != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ScrimColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ChipRow {
                            StreamQuality.entries.forEach { quality ->
                                FilterChip(
                                    selected = current.quality == quality,
                                    onClick = { controller.selectQuality(quality) },
                                    enabled = quality == StreamQuality.Main || current.config.hasSub,
                                    label = { Text(quality.name) },
                                )
                            }
                            RtspTransport.entries.forEach { transport ->
                                FilterChip(
                                    selected = current.config.transport == transport,
                                    onClick = { controller.setTransport(transport) },
                                    label = { Text(transport.name) },
                                )
                            }
                        }
                        ChipRow {
                            Text("Latency", color = Color.White)
                            LATENCY_PRESETS_MS.forEach { ms ->
                                FilterChip(
                                    selected = current.config.latencyMs == ms,
                                    onClick = { controller.setLatency(ms) },
                                    label = { Text("$ms ms") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun StatusPanel(state: StreamState, onRetry: () -> Unit) {
    when (state) {
        StreamState.Playing, StreamState.Idle -> Unit
        else -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (state !is StreamState.Failed) CircularProgressIndicator(color = Color.White)
            Text(text = state.headline(), color = Color.White, textAlign = TextAlign.Center)
            state.detail()?.let { Text(text = it, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center) }
            if (state is StreamState.Failed) Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun ImmersiveMode(activity: Activity?) {
    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private fun StreamState.badge(): String = when (this) {
    StreamState.Idle -> "Idle"
    StreamState.Connecting -> "Connecting"
    StreamState.Buffering -> "Buffering"
    StreamState.Playing -> "Live"
    is StreamState.Reconnecting -> "Reconnecting"
    is StreamState.Failed -> "Offline"
}

private fun StreamState.headline(): String = when (this) {
    StreamState.Idle -> ""
    StreamState.Connecting -> "Connecting"
    StreamState.Buffering -> "Buffering"
    StreamState.Playing -> ""
    is StreamState.Reconnecting -> "Reconnecting in ${(delayMs + 999) / 1000}s (attempt $attempt of $maxAttempts)"
    is StreamState.Failed -> "Could not connect"
}

private fun StreamState.detail(): String? = when (this) {
    is StreamState.Reconnecting -> reason
    is StreamState.Failed -> reason
    else -> null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
