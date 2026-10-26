package uk.co.aaronburt.rtsp.ui.cameras

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.player.LiveStreamController
import uk.co.aaronburt.rtsp.player.Media3Engine
import uk.co.aaronburt.rtsp.player.PlayerEngine
import uk.co.aaronburt.rtsp.player.ReconnectPolicy
import uk.co.aaronburt.rtsp.player.StreamQuality
import uk.co.aaronburt.rtsp.player.StreamState
import uk.co.aaronburt.rtsp.player.toStreamConfig
import uk.co.aaronburt.rtsp.snapshot.SurfaceCapture
import uk.co.aaronburt.rtsp.ui.PlayerSurface
import uk.co.aaronburt.rtsp.ui.components.LocalSnapshotStore

internal class PreviewRefreshScheduler(private val intervalMs: Long = 15_000) {
    private val permits = Semaphore(2)

    suspend fun run(capture: suspend () -> Unit) {
        while (true) {
            permits.withPermit { capture() }
            delay(intervalMs)
        }
    }
}

private data class PreviewSession(val controller: LiveStreamController, val capture: SurfaceCapture)

@Composable
internal fun CameraPreviewRefresh(
    camera: Camera,
    enabled: Boolean,
    scheduler: PreviewRefreshScheduler,
    onConnection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    engineFactory: (Context) -> PlayerEngine = { Media3Engine(it) },
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = LocalSnapshotStore.current
    val currentOnConnection by rememberUpdatedState(onConnection)
    val currentEngineFactory by rememberUpdatedState(engineFactory)
    val config = remember(camera.url, camera.subUrl, camera.username, camera.password, camera.transport, camera.latencyMs) {
        camera.toStreamConfig()
    }
    var session by remember(camera.id) { mutableStateOf<PreviewSession?>(null) }

    LaunchedEffect(camera.id, config, enabled, lifecycle, scheduler, store) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            scheduler.run {
                coroutineScope {
                    val controller = LiveStreamController(currentEngineFactory(context), this, ReconnectPolicy(maxAttempts = 0))
                    val capture = SurfaceCapture()
                    val currentSession = PreviewSession(controller, capture)
                    session = currentSession
                    var connected = false
                    try {
                        controller.setMuted(true)
                        controller.start(config, StreamQuality.Sub)
                        withTimeoutOrNull(20_000) {
                            connected = combine(controller.state, controller.videoFrameAvailable) { state, frame ->
                                when {
                                    state is StreamState.Failed -> false
                                    state == StreamState.Playing && frame -> true
                                    else -> null
                                }
                            }.filterNotNull().first()
                            if (!connected) return@withTimeoutOrNull
                            val generation = controller.captureGeneration
                            val bitmap = capture.captureFrame(controller.frameSize.value, maxDimension = 640)
                                ?: return@withTimeoutOrNull
                            val jpeg = try {
                                if (generation != controller.captureGeneration || !controller.videoFrameAvailable.value) {
                                    return@withTimeoutOrNull
                                }
                                withContext(Dispatchers.Default) {
                                    ByteArrayOutputStream().use { output ->
                                        if (bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)) output.toByteArray() else null
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                            if (jpeg != null) withContext(Dispatchers.IO) {
                                ensureActive()
                                store.save(camera.id, jpeg)
                            }
                        }
                        currentOnConnection(connected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentOnConnection(connected)
                    } finally {
                        if (session === currentSession) session = null
                        controller.release()
                    }
                }
            }
        }
    }

    session?.let { PlayerSurface(it.controller, modifier, it.capture, keepScreenOn = false) }
}
