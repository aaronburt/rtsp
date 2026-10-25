package uk.co.aaronburt.rtsp.update

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.BuildConfig
import uk.co.aaronburt.rtsp.R

object Updater {
    fun start(application: Application) {
        UpdateManager.init(application)
        UpdateManager.checkOnLaunch()
    }
}

@Composable
fun UpdatePrompt() {
    val release by UpdateManager.prompt.collectAsStateWithLifecycle()
    val state by UpdateManager.state.collectAsStateWithLifecycle()
    val current = release ?: return
    AlertDialog(
        onDismissRequest = UpdateManager::dismissPrompt,
        title = { Text(stringResource(R.string.update_available_title, current.tag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpdateProgress(state)
                if (state is UpdateState.Available) ReleaseNotesText(current.notes)
            }
        },
        confirmButton = { UpdateActions(state, onDone = UpdateManager::dismissPrompt) },
        dismissButton = {
            TextButton(onClick = UpdateManager::dismissPrompt) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}

@Composable
fun UpdateSettingsSection(modifier: Modifier = Modifier) {
    val state by UpdateManager.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.update_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.update_installed_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UpdateProgress(state)
        val available = (state as? UpdateState.Available)?.release
        if (available != null) ReleaseNotesText(available.notes)
        UpdateActions(state, onDone = {})
        if (state !is UpdateState.Checking && state !is UpdateState.Downloading && state !is UpdateState.Installing) {
            OutlinedButton(onClick = { UpdateManager.check() }) {
                Text(stringResource(R.string.update_check))
            }
        }
    }
}

@Composable
private fun UpdateProgress(state: UpdateState) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> Text(stringResource(R.string.update_checking), color = muted)
        is UpdateState.UpToDate -> {
            val ago = DateUtils.getRelativeTimeSpanString(
                state.checkedAtMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            Text(stringResource(R.string.update_up_to_date, ago), color = muted)
        }
        is UpdateState.Available ->
            Text(stringResource(R.string.update_available, state.release.tag), color = MaterialTheme.colorScheme.primary)
        is UpdateState.Downloading -> {
            Text(stringResource(R.string.update_downloading, state.release.tag), color = muted)
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is UpdateState.Ready -> {
            Text(stringResource(R.string.update_ready, state.release.tag), color = muted)
            if (state.needsPermission) Text(stringResource(R.string.update_permission_needed), color = muted)
        }
        is UpdateState.Installing -> Text(stringResource(R.string.update_installing), color = muted)
        is UpdateState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun UpdateActions(state: UpdateState, onDone: () -> Unit) {
    when (state) {
        is UpdateState.Available -> Button(onClick = { UpdateManager.download(state.release) }) {
            Text(stringResource(R.string.update_download))
        }
        is UpdateState.Ready -> Button(onClick = {
            UpdateManager.install()
            onDone()
        }) {
            Text(stringResource(R.string.update_install))
        }
        is UpdateState.Failed -> Button(onClick = UpdateManager::retry) {
            Text(stringResource(R.string.update_retry))
        }
        else -> Unit
    }
}

@Composable
private fun ReleaseNotesText(notes: String) {
    if (notes.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.update_whats_new),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(text = notes, style = MaterialTheme.typography.bodyMedium)
    }
}
