package uk.co.aaronburt.rtsp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.ui.components.SettingsSection
import uk.co.aaronburt.rtsp.ui.components.SwitchRow
import uk.co.aaronburt.rtsp.ui.components.SegmentedToggle
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.data.CameraRepository
import kotlin.math.roundToInt
import uk.co.aaronburt.rtsp.update.UpdateSettingsSection
import uk.co.aaronburt.rtsp.sharing.CameraBackupSection

@Composable
fun SettingsScreen(
    preferences: DisplayPreferences,
    streamDefaults: StreamDefaultsPreferences,
    cameraRepository: CameraRepository,
    modifier: Modifier = Modifier,
) {
    val showClock by preferences.showClock.collectAsStateWithLifecycle()
    val allowRecording by preferences.allowRecording.collectAsStateWithLifecycle()
    val pinchToZoom by preferences.pinchToZoom.collectAsStateWithLifecycle()
    val multiviewMainStream by preferences.multiviewMainStream.collectAsStateWithLifecycle()
    val defaults by streamDefaults.defaults.collectAsStateWithLifecycle()
    val refreshPreviews by preferences.refreshPreviews.collectAsStateWithLifecycle()
    val latencyLabel = stringResource(R.string.field_latency)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        AppIconSection()
        CameraBackupSection(repository = cameraRepository)
        SettingsSection(title = stringResource(R.string.settings_previews_title)) {
            SwitchRow(
                title = stringResource(R.string.settings_refresh_previews_title),
                subtitle = stringResource(R.string.settings_refresh_previews_subtitle),
                checked = refreshPreviews,
                onCheckedChange = preferences::setRefreshPreviews,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_stream_defaults)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stream_defaults_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.field_transport), style = MaterialTheme.typography.labelMedium)
                SegmentedToggle(
                    options = Transport.entries,
                    selected = defaults.transport,
                    label = { stringResource(when (it) {
                        Transport.Auto -> R.string.transport_auto
                        Transport.Tcp -> R.string.transport_tcp
                        Transport.Udp -> R.string.transport_udp
                    }) },
                    onSelect = streamDefaults::setTransport,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.field_latency), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.latency_ms, defaults.latencyMs))
                }
                Slider(
                    modifier = Modifier.semantics { contentDescription = latencyLabel },
                    value = defaults.latencyMs.toFloat(),
                    onValueChange = { streamDefaults.setLatency((it / 50).roundToInt() * 50) },
                    valueRange = 0f..2000f,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = stringResource(R.string.field_muted),
                subtitle = stringResource(R.string.stream_default_muted_hint),
                checked = defaults.startMuted,
                onCheckedChange = streamDefaults::setStartMuted,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_recording_title)) {
            SwitchRow(
                title = stringResource(R.string.settings_allow_recording_title),
                subtitle = stringResource(R.string.settings_allow_recording_subtitle),
                checked = allowRecording,
                onCheckedChange = preferences::setAllowRecording,
            )
        }
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
