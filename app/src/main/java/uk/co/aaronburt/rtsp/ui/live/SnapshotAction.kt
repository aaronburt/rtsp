package uk.co.aaronburt.rtsp.ui.live

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.snapshot.SnapshotExporter
import uk.co.aaronburt.rtsp.snapshot.SnapshotSource

data class SnapshotAction(val enabled: Boolean, val capture: () -> Unit, val messages: SnackbarHostState)

@Composable
fun rememberSnapshotAction(cameraId: String, source: SnapshotSource?): SnapshotAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember(context) { SnapshotExporter(context) }
    val messages = remember(cameraId) { SnackbarHostState() }
    val currentSource by rememberUpdatedState(source)
    var pendingPath by rememberSaveable(cameraId) { mutableStateOf<String?>(null) }
    var busy by remember(cameraId) { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.snapshot_saved)
    val failedMessage = stringResource(R.string.snapshot_failed)
    val shareLabel = stringResource(R.string.action_share)
    val shareFailed = stringResource(R.string.snapshot_share_failed)

    fun saved(uri: Uri) {
        scope.launch {
            if (messages.showSnackbar(savedMessage, shareLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                try {
                    context.startActivity(Intent.createChooser(exporter.shareIntent(uri), shareLabel))
                } catch (_: RuntimeException) {
                    messages.showSnackbar(shareFailed)
                }
            }
        }
    }

    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        scope.launch {
            try {
                if (uri != null && file != null) {
                    exporter.writeDocument(file, uri)
                    saved(uri)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.showSnackbar(failedMessage)
            } finally {
                file?.delete()
                busy = false
            }
        }
    }

    return SnapshotAction(
        enabled = source != null && !busy && pendingPath == null,
        messages = messages,
        capture = {
            val available = currentSource
            if (available != null && !busy && pendingPath == null) {
                busy = true
                scope.launch {
                    var file: File? = null
                    var awaitingDestination = false
                    try {
                        val bitmap = available.capture() ?: throw IOException("No live frame")
                        file = try { exporter.prepare(bitmap) } finally { bitmap.recycle() }
                        if (Build.VERSION.SDK_INT >= 29) {
                            saved(exporter.saveToPhotos(file))
                        } else {
                            pendingPath = file.path
                            destination.launch(file.name)
                            awaitingDestination = true
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        pendingPath = null
                        scope.launch { messages.showSnackbar(failedMessage) }
                    } finally {
                        if (!awaitingDestination) {
                            file?.delete()
                            busy = false
                        }
                    }
                }
            }
        },
    )
}
