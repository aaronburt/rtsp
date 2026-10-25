package uk.co.aaronburt.rtsp.ui.cameras

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.ui.live.cameraTint
import uk.co.aaronburt.rtsp.ui.components.LastSeenChip
import uk.co.aaronburt.rtsp.ui.components.SnapshotImage
import uk.co.aaronburt.rtsp.ui.components.rememberSnapshot
import uk.co.aaronburt.rtsp.ui.theme.Amber
import uk.co.aaronburt.rtsp.ui.theme.AmberBorder
import uk.co.aaronburt.rtsp.ui.theme.Danger
import uk.co.aaronburt.rtsp.ui.theme.DangerContainer
import uk.co.aaronburt.rtsp.ui.theme.DangerText
import uk.co.aaronburt.rtsp.ui.theme.OfflineSurface
import uk.co.aaronburt.rtsp.ui.theme.OfflineVideo
import uk.co.aaronburt.rtsp.ui.theme.OnDangerContainer
import uk.co.aaronburt.rtsp.ui.theme.MonoTiny
import uk.co.aaronburt.rtsp.ui.theme.Scrim

@Composable
fun CamerasScreen(
    viewModel: CamerasViewModel,
    onOpenCamera: (String) -> Unit,
    onAddCamera: () -> Unit,
    onEditCamera: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        CamerasHeader(
            state = state,
            onToggleSearch = viewModel::toggleSearch,
            onToggleEditing = viewModel::toggleEditing,
            onAdd = onAddCamera,
            onQueryChange = viewModel::setQuery,
            onFilterChange = viewModel::setFilter,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.visible, key = { it.id }) { camera ->
                CameraTile(
                    camera = camera,
                    editing = state.editing,
                    onClick = {
                        if (state.editing) onEditCamera(camera.id) else onOpenCamera(camera.id)
                    },
                    onRetry = { viewModel.retry(camera.id) },
                )
            }
            if (state.visible.isEmpty() && state.all.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.cameras_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CamerasHeader(
    state: CamerasState,
    onToggleSearch: () -> Unit,
    onToggleEditing: () -> Unit,
    onAdd: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CameraFilter) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.cameras_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = pluralStringResource(R.plurals.cameras_count, state.all.size, state.all.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(if (state.searching) R.drawable.ic_close else R.drawable.ic_search),
                    contentDescription = stringResource(
                        if (state.searching) R.string.cameras_close_search else R.string.cameras_search,
                    ),
                )
            }
            OutlinedButton(
                onClick = onToggleEditing,
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                border = BorderStroke(
                    1.dp,
                    if (state.editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Text(
                    text = stringResource(if (state.editing) R.string.done else R.string.edit),
                    color = if (state.editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
            FilledIconButton(onClick = onAdd, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_camera),
                )
            }
        }
        if (state.searching) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.cameras_search_hint)) },
                shape = RoundedCornerShape(12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraFilter.entries.forEach { filter ->
                val count = when (filter) {
                    CameraFilter.All -> state.all.size
                    CameraFilter.Offline -> state.offlineCount
                    CameraFilter.Favourites -> state.all.count { it.favourite }
                }
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text = when (filter) {
                                CameraFilter.All -> stringResource(R.string.filter_all)
                                CameraFilter.Offline -> stringResource(R.string.filter_offline)
                                CameraFilter.Favourites -> stringResource(R.string.filter_favourites)
                            } + if (filter == CameraFilter.Favourites) "" else " $count",
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.filter == filter,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = AmberBorder,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CameraTile(
    camera: Camera,
    editing: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val offline = !camera.online
    val snapshot = rememberSnapshot(camera.id)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (offline) OfflineSurface else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            if (offline) 1.5.dp else 1.dp,
            if (offline) Danger else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(if (offline) OfflineVideo else cameraTint(camera.id)),
                contentAlignment = Alignment.Center,
            ) {
                if (snapshot != null) {
                    SnapshotImage(snapshot, Modifier.matchParentSize(), alpha = if (offline) 0.35f else 1f)
                }
                if (offline) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_videocam_off),
                            contentDescription = null,
                            tint = DangerText,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = stringResource(R.string.camera_offline_since, camera.offlineSince.orEmpty()),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = DangerText,
                        )
                    }
                } else {
                    if (snapshot == null) {
                        Icon(
                            painter = painterResource(R.drawable.ic_videocam),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (snapshot != null) {
                        LastSeenChip(snapshot, Modifier.align(Alignment.BottomStart).padding(6.dp))
                    } else camera.lastFrameAt?.let { time ->
                        Text(
                            text = time,
                            style = MonoTiny,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Scrim)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (editing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Amber),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (offline) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            color = OnDangerContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(DangerContainer, RoundedCornerShape(13.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (camera.favourite) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star_filled),
                        contentDescription = stringResource(R.string.favourite),
                        tint = Amber,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp),
                    )
                }
            }
        }
    }
}
