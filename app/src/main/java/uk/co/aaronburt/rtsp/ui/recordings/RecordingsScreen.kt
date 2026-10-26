package uk.co.aaronburt.rtsp.ui.recordings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.recording.Recording

@Composable
fun RecordingsScreen(viewModel: RecordingsViewModel, modifier: Modifier = Modifier) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.nav_recordings), style = MaterialTheme.typography.headlineMedium)
        if (recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.recordings_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings, key = { it.id }) { recording ->
                    RecordingRow(
                        recording = recording,
                        onPlay = {
                            val file = viewModel.fileFor(recording) ?: return@RecordingRow
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.recordings", file)
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.recording_no_player, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { pendingDelete = recording },
                    )
                }
            }
        }
    }

    pendingDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.recording_delete_title)) },
            text = { Text(stringResource(R.string.recording_delete_message, recording.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(recording)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RecordingRow(recording: Recording, onPlay: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = recording.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(
                        R.string.recordings_size_date,
                        DateUtils.formatDateTime(
                            context,
                            recording.modifiedMs,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                        ),
                        Formatter.formatShortFileSize(context, recording.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.recording_delete),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
