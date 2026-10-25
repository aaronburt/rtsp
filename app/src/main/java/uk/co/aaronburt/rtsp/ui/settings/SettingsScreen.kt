package uk.co.aaronburt.rtsp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.ui.components.SwitchRow
import uk.co.aaronburt.rtsp.update.UpdateSettingsSection

@Composable
fun SettingsScreen(preferences: DisplayPreferences, modifier: Modifier = Modifier) {
    val showClock by preferences.showClock.collectAsStateWithLifecycle()
    val allowRecording by preferences.allowRecording.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_display_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SwitchRow(
                title = stringResource(R.string.settings_show_clock_title),
                subtitle = stringResource(R.string.settings_show_clock_subtitle),
                checked = showClock,
                onCheckedChange = preferences::setShowClock,
            )
            SwitchRow(
                title = stringResource(R.string.settings_allow_recording_title),
                subtitle = stringResource(R.string.settings_allow_recording_subtitle),
                checked = allowRecording,
                onCheckedChange = preferences::setAllowRecording,
            )
        }
        UpdateSettingsSection()
    }
}
