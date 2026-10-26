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
import uk.co.aaronburt.rtsp.ui.components.SettingsSection
import uk.co.aaronburt.rtsp.ui.components.SwitchRow
import uk.co.aaronburt.rtsp.update.UpdateSettingsSection

@Composable
fun SettingsScreen(preferences: DisplayPreferences, modifier: Modifier = Modifier) {
    val showClock by preferences.showClock.collectAsStateWithLifecycle()
    val pinchToZoom by preferences.pinchToZoom.collectAsStateWithLifecycle()
    val multiviewMainStream by preferences.multiviewMainStream.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        SettingsSection(title = stringResource(R.string.settings_live_title)) {
            SwitchRow(
                title = stringResource(R.string.settings_multiview_main_stream_title),
                subtitle = stringResource(R.string.settings_multiview_main_stream_subtitle),
                checked = multiviewMainStream,
                onCheckedChange = preferences::setMultiviewMainStream,
            )
            SwitchRow(
                title = stringResource(R.string.settings_pinch_to_zoom_title),
                subtitle = stringResource(R.string.settings_pinch_to_zoom_subtitle),
                checked = pinchToZoom,
                onCheckedChange = preferences::setPinchToZoom,
            )
            SwitchRow(
                title = stringResource(R.string.settings_show_clock_title),
                subtitle = stringResource(R.string.settings_show_clock_subtitle),
                checked = showClock,
                onCheckedChange = preferences::setShowClock,
            )
        }
        UpdateSettingsSection()
    }
}
