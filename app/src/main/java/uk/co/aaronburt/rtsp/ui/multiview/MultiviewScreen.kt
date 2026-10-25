package uk.co.aaronburt.rtsp.ui.multiview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.player.StreamQuality
import uk.co.aaronburt.rtsp.player.StreamStatus
import uk.co.aaronburt.rtsp.ui.cameras.CamerasViewModel
import uk.co.aaronburt.rtsp.ui.components.ImmersiveEffect
import uk.co.aaronburt.rtsp.ui.components.LandscapeLockEffect
import uk.co.aaronburt.rtsp.ui.components.SnapshotImage
import uk.co.aaronburt.rtsp.ui.components.StatusDot
import uk.co.aaronburt.rtsp.ui.components.rememberClock
import uk.co.aaronburt.rtsp.ui.components.rememberSnapshot
import uk.co.aaronburt.rtsp.ui.live.PlayerSlot
import uk.co.aaronburt.rtsp.ui.theme.Danger
import uk.co.aaronburt.rtsp.ui.theme.DangerContainer
import uk.co.aaronburt.rtsp.ui.theme.DangerText
import uk.co.aaronburt.rtsp.ui.theme.MonoTiny
import uk.co.aaronburt.rtsp.ui.theme.OfflineVideo
import uk.co.aaronburt.rtsp.ui.theme.OnDangerContainer
import uk.co.aaronburt.rtsp.ui.theme.Scrim

enum class GridLayout(val columns: Int, val rows: Int, val label: String) {
    Single(1, 1, "1"),
    Quad(2, 2, "2×2"),
    Six(3, 2, "2×3"),
}

@Composable
fun MultiviewScreen(
    viewModel: CamerasViewModel,
    playerSlot: PlayerSlot,
    onOpenCamera: (String) -> Unit,
    onExit: () -> Unit,
    showClock: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var layoutIndex by rememberSaveable { mutableIntStateOf(GridLayout.Quad.ordinal) }
    val layout = GridLayout.entries[layoutIndex]
    val clock = rememberClock()
    LandscapeLockEffect()
    ImmersiveEffect(enabled = true)

    Row(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(layout.rows) { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(layout.columns) { column ->
                        val camera = state.all.getOrNull(row * layout.columns + column)
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (camera != null) {
                                key(camera.id) {
                                    MultiviewTile(
                                        camera = camera,
                                        playerSlot = playerSlot,
                                        onOpen = { onOpenCamera(camera.id) },
                                        onRetry = { viewModel.retry(camera.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        ControlRail(
            clock = clock,
            showClock = showClock,
            layout = layout,
            onLayout = { layoutIndex = it.ordinal },
            onExit = onExit,
        )
    }
}

@Composable
private fun MultiviewTile(
    camera: Camera,
    playerSlot: PlayerSlot,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val openLabel = stringResource(R.string.multiview_open, camera.name)
    val snapshot = rememberSnapshot(camera.id)
    var playerStatus by remember(camera.id) { mutableStateOf(StreamStatus.Pending) }
    val status = if (camera.online) playerStatus else StreamStatus.Error
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (camera.online) {
                    Modifier.clickable(onClickLabel = openLabel, onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = camera.name },
    ) {
        if (camera.online) {
            playerSlot(camera, StreamQuality.Sub, true, Modifier.fillMaxSize()) { playerStatus = it }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OfflineVideo)
                    .border(1.5.dp, Danger),
                contentAlignment = Alignment.Center,
            ) {
                if (snapshot != null) {
                    SnapshotImage(snapshot, Modifier.matchParentSize(), alpha = 0.35f)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_videocam_off),
                        contentDescription = null,
                        tint = DangerText,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.camera_offline_since, camera.offlineSince.orEmpty()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerText,
                    )
                    Surface(
                        onClick = onRetry,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = DangerContainer,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.retry),
                                color = OnDangerContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Scrim)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(status = status, size = 8.dp)
            Text(
                text = camera.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ControlRail(
    clock: String,
    showClock: Boolean,
    layout: GridLayout,
    onLayout: (GridLayout) -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.multiview_exit),
                )
            }
            if (showClock) {
                Text(text = clock, style = MonoTiny.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(4.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GridLayout.entries.forEach { option ->
                    val selected = option == layout
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onLayout(option) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            style = MonoTiny.copy(fontSize = 13.sp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.size(44.dp))
    }
}
