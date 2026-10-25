package uk.co.aaronburt.rtsp.ui.live

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.data.displayUrl
import uk.co.aaronburt.rtsp.ui.components.ImmersiveEffect
import uk.co.aaronburt.rtsp.ui.components.SegmentedToggle
import uk.co.aaronburt.rtsp.ui.components.formatElapsed
import uk.co.aaronburt.rtsp.ui.components.rememberClock
import uk.co.aaronburt.rtsp.ui.theme.Amber
import uk.co.aaronburt.rtsp.ui.theme.Danger
import uk.co.aaronburt.rtsp.ui.theme.MonoSmall
import uk.co.aaronburt.rtsp.ui.theme.OnDangerContainer
import uk.co.aaronburt.rtsp.ui.theme.RecordBadge
import uk.co.aaronburt.rtsp.ui.theme.RecordingContainer
import uk.co.aaronburt.rtsp.ui.theme.RecordingText
import uk.co.aaronburt.rtsp.ui.theme.Scrim
import uk.co.aaronburt.rtsp.ui.theme.Success

@Composable
fun LiveScreen(
    viewModel: LiveViewModel,
    playerSlot: PlayerSlot,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera = state.camera
    if (camera == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.camera_missing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BackHandler(onBack = onBack)
        return
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var userFullscreen by rememberSaveable { mutableStateOf(false) }
    val fullscreen = landscape || userFullscreen
    ImmersiveEffect(enabled = fullscreen)
    BackHandler(enabled = userFullscreen) { userFullscreen = false }
    val clock = rememberClock()

    if (fullscreen) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            playerSlot(camera, Modifier.fillMaxSize())
            VideoOverlays(
                state = state,
                clock = clock,
                fullscreen = true,
                onFullscreen = { userFullscreen = false },
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LiveHeader(
            camera = camera,
            onBack = onBack,
            onToggleFavourite = viewModel::toggleFavourite,
            onEdit = { onEdit(camera.id) },
        )
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            playerSlot(camera, Modifier.fillMaxSize())
            VideoOverlays(
                state = state,
                clock = clock,
                fullscreen = false,
                onFullscreen = { userFullscreen = true },
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }
        StatsRow(state = state, onToggleDetails = viewModel::toggleDetails)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecordButton(
                recording = state.recording,
                seconds = state.recordingSeconds,
                onClick = viewModel::toggleRecording,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    icon = R.drawable.ic_photo_camera,
                    label = stringResource(R.string.action_snapshot),
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    icon = if (state.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                    label = stringResource(if (state.muted) R.string.action_unmute else R.string.action_mute),
                    enabled = true,
                    onClick = viewModel::toggleMuted,
                    modifier = Modifier.weight(1f),
                )
            }
            SegmentedToggle(
                options = StreamQuality.entries,
                selected = state.quality,
                label = {
                    stringResource(
                        if (it == StreamQuality.Main) R.string.stream_main else R.string.stream_sub,
                    )
                },
                onSelect = viewModel::setQuality,
            )
        }
    }
}

@Composable
private fun LiveHeader(
    camera: Camera,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = camera.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleFavourite, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(
                    if (camera.favourite) R.drawable.ic_star_filled else R.drawable.ic_star,
                ),
                contentDescription = stringResource(
                    if (camera.favourite) R.string.unfavourite else R.string.favourite,
                ),
                tint = if (camera.favourite) Amber else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.edit_camera),
            )
        }
    }
}

@Composable
private fun VideoOverlays(
    state: LiveState,
    clock: String,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (state.recording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(RecordBadge)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                Text(
                    text = stringResource(R.string.rec_badge, formatElapsed(state.recordingSeconds)),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (fullscreen) {
            OverlayButton(
                icon = R.drawable.ic_arrow_back,
                description = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
        Text(
            text = clock,
            style = MonoSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Scrim)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        OverlayButton(
            icon = if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
            description = stringResource(if (fullscreen) R.string.exit_fullscreen else R.string.fullscreen),
            onClick = onFullscreen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

@Composable
private fun OverlayButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp).clip(CircleShape).background(Scrim),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun StatsRow(state: LiveState, onToggleDetails: () -> Unit) {
    val camera = state.camera ?: return
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (camera.online) Success else Danger),
            )
            Text(
                text = stringResource(R.string.buffer_ms, camera.latencyMs),
                style = MonoSmall,
                color = if (camera.online) Success else Danger,
            )
            Text(
                text = stringResource(
                    if (state.quality == StreamQuality.Main) R.string.stream_main_short else R.string.stream_sub_short,
                ),
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onToggleDetails,
                modifier = Modifier
                    .height(36.dp)
                    .semantics { selected = state.detailsOpen },
                border = BorderStroke(0.dp, Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.stream_details),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (state.detailsOpen) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DetailLine(stringResource(R.string.detail_url), displayUrl(camera))
                DetailLine(
                    stringResource(R.string.detail_transport),
                    when (camera.transport) {
                        Transport.Auto -> stringResource(R.string.transport_auto)
                        Transport.Tcp -> stringResource(R.string.transport_tcp)
                        Transport.Udp -> stringResource(R.string.transport_udp)
                    },
                )
                DetailLine(
                    stringResource(R.string.detail_reconnect),
                    stringResource(if (camera.autoReconnect) R.string.on else R.string.off),
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(text = value, style = MonoSmall)
    }
}

@Composable
private fun RecordButton(recording: Boolean, seconds: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (recording) RecordingContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.5.dp, if (recording) Danger else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(if (recording) RoundedCornerShape(3.dp) else CircleShape)
                    .background(Danger),
            )
            Text(
                text = stringResource(if (recording) R.string.stop_recording else R.string.record),
                color = if (recording) OnDangerContainer else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (recording) {
                Text(text = formatElapsed(seconds), style = MonoSmall.copy(fontSize = 14.sp), color = RecordingText)
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.Button },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
