package uk.co.aaronburt.rtsp.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.ui.components.SettingsSection

@Composable
fun AppIconSection() {
    val context = LocalContext.current
    val manager = remember(context) { AppIconManager(context.applicationContext) }
    var selected by remember(manager) { mutableStateOf(manager.current) }
    var choosing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val previews = remember(context) {
        AppIcon.entries.associateWith { icon ->
            checkNotNull(ContextCompat.getDrawable(context, icon.image)).toBitmap(128, 128).asImageBitmap()
        }
    }

    SettingsSection(title = stringResource(R.string.settings_app_icon)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(previews.getValue(selected), contentDescription = null, modifier = Modifier.size(56.dp))
                Text(stringResource(selected.label), style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = { failed = false; choosing = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_icon_choose))
            }
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.app_icon_choose)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.app_icon_launcher_hint), style = MaterialTheme.typography.bodySmall)
                    AppIcon.entries.forEach { icon ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = selected == icon, role = Role.RadioButton, onClick = {
                                    try {
                                        manager.select(icon)
                                        selected = manager.current
                                        choosing = false
                                    } catch (_: Exception) {
                                        failed = true
                                    }
                                })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Image(previews.getValue(icon), contentDescription = null, modifier = Modifier.size(48.dp))
                            Text(stringResource(icon.label), Modifier.weight(1f))
                            RadioButton(selected = selected == icon, onClick = null)
                        }
                    }
                    if (failed) Text(stringResource(R.string.app_icon_failed), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
