package uk.co.aaronburt.rtsp.sharing

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.CameraRepository
import uk.co.aaronburt.rtsp.ui.components.SettingsSection

@Composable
fun CameraBackupSection(repository: CameraRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameras by repository.cameras.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showExportPrompt by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportConfirmation by remember { mutableStateOf("") }
    var backupPages by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var selectedPages by remember { mutableStateOf<List<String>>(emptyList()) }
    var restoredCameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var savePages by remember { mutableStateOf<List<String>>(emptyList()) }

    fun save(pageSet: List<String>, tree: Uri? = null) {
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { CameraBackupImages.save(context, pageSet, tree) }
                savePages = emptyList()
                backupPages = emptyList()
            } catch (_: Exception) {
                error = context.getString(R.string.camera_backup_save_failed)
            } finally {
                busy = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val pages = savePages
        if (uri != null && pages.isNotEmpty()) save(pages, uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            error = null
            scope.launch {
                try {
                    selectedPages = withContext(Dispatchers.IO) {
                        require(uris.size <= 256)
                        uris.map { CameraBackupImages.readQr(context, it) }
                    }
                    restorePassword = ""
                    showRestorePassword = true
                } catch (_: Exception) {
                    error = context.getString(R.string.camera_backup_read_failed)
                } finally {
                    busy = false
                }
            }
        }
    }

    SettingsSection(title = stringResource(R.string.camera_backup_title)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.camera_backup_summary), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { showExportPrompt = true },
                enabled = cameras.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.camera_backup_create, cameras.size))
            }
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.camera_backup_restore))
            }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showExportPrompt) {
        AlertDialog(
            onDismissRequest = { if (!busy) showExportPrompt = false },
            title = { Text(stringResource(R.string.camera_backup_create_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.camera_backup_secret_warning))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { if (it.length <= 1_024) exportPassword = it },
                        label = { Text(stringResource(R.string.camera_backup_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !busy,
                    )
                    OutlinedTextField(
                        value = exportConfirmation,
                        onValueChange = { if (it.length <= 1_024) exportConfirmation = it },
                        label = { Text(stringResource(R.string.camera_backup_confirm_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !busy,
                    )
                    if (exportPassword.length in 1..9) {
                        Text(
                            stringResource(R.string.camera_backup_short_secret_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (busy) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && exportPassword.isNotEmpty() && exportPassword == exportConfirmation,
                    onClick = {
                        val secret = exportPassword.toCharArray()
                        busy = true
                        error = null
                        showExportPrompt = false
                        scope.launch {
                            try {
                                backupPages = withContext(Dispatchers.Default) {
                                    EncryptedCameraBackup.encode(cameras, secret)
                                }
                                savePages = backupPages
                            } catch (_: Exception) {
                                error = context.getString(R.string.camera_backup_create_failed)
                            } finally {
                                secret.fill('\u0000')
                                exportPassword = ""
                                exportConfirmation = ""
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.camera_backup_create_qr)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPrompt = false; exportPassword = ""; exportConfirmation = "" }, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (backupPages.isNotEmpty()) {
        val first = backupPages.first()
        val bitmap = remember(first) { CameraBackupImages.qrBitmap(first) }
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    backupPages = emptyList()
                    savePages = emptyList()
                }
            },
            title = { Text(stringResource(R.string.camera_backup_ready_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.camera_backup_ready_message, backupPages.size))
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.camera_backup_qr_description))
                    }
                    DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
                    Text(stringResource(R.string.camera_backup_page_saved, backupPages.size))
                    if (busy) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        savePages = backupPages
                        if (Build.VERSION.SDK_INT >= 29) save(backupPages)
                        else folderPicker.launch(null)
                    },
                ) { Text(stringResource(R.string.camera_backup_save_images)) }
            },
            dismissButton = {
                TextButton(onClick = { if (!busy) { backupPages = emptyList(); savePages = emptyList() } }, enabled = !busy) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showRestorePassword) {
        AlertDialog(
            onDismissRequest = { if (!busy) showRestorePassword = false },
            title = { Text(stringResource(R.string.camera_backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.camera_backup_restore_secret_hint, selectedPages.size))
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { if (it.length <= 1_024) restorePassword = it },
                        label = { Text(stringResource(R.string.camera_backup_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !busy,
                    )
                    if (busy) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !busy && restorePassword.isNotEmpty(), onClick = {
                    val secret = restorePassword.toCharArray()
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            restoredCameras = withContext(Dispatchers.Default) {
                                EncryptedCameraBackup.decode(selectedPages, secret)
                            }
                            showRestorePassword = false
                        } catch (_: Exception) {
                            error = context.getString(R.string.camera_backup_decrypt_failed)
                        } finally {
                            secret.fill('\u0000')
                            restorePassword = ""
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.camera_backup_unlock)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestorePassword = false
                    restorePassword = ""
                    selectedPages = emptyList()
                    error = null
                }, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (restoredCameras.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { restoredCameras = emptyList() },
            title = { Text(stringResource(R.string.camera_backup_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.camera_backup_preview_message, restoredCameras.size))
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(restoredCameras) { Text(it.name, Modifier.padding(vertical = 4.dp)) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    restoredCameras.forEach { repository.save(it.copy(id = repository.newId())) }
                    error = context.getString(R.string.camera_backup_imported, restoredCameras.size)
                    restoredCameras = emptyList()
                    selectedPages = emptyList()
                }) { Text(stringResource(R.string.camera_backup_add_cameras)) }
            },
            dismissButton = {
                TextButton(onClick = { restoredCameras = emptyList(); selectedPages = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
