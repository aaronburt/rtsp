package uk.co.aaronburt.rtsp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.player.StreamQuality
import uk.co.aaronburt.rtsp.player.StreamStatus
import uk.co.aaronburt.rtsp.ui.components.LastSeenChip
import uk.co.aaronburt.rtsp.ui.components.SnapshotImage
import uk.co.aaronburt.rtsp.ui.components.rememberSnapshot

typealias PlayerSlot = @Composable (
    camera: Camera,
    quality: StreamQuality,
    muted: Boolean,
    modifier: Modifier,
    onStatus: (StreamStatus) -> Unit,
) -> Unit

val PlaceholderPlayerSlot: PlayerSlot = { camera, _, _, modifier, onStatus ->
    LaunchedEffect(camera.online) {
        onStatus(if (camera.online) StreamStatus.Live else StreamStatus.Error)
    }
    VideoPlaceholder(camera, modifier)
}

private val tints = listOf(
    Color(0xFF1F2A35),
    Color(0xFF2C2820),
    Color(0xFF1F2A20),
    Color(0xFF28233A),
    Color(0xFF2E2124),
    Color(0xFF1F2E2E),
)

fun cameraTint(id: String): Color = tints[id.hashCode().absoluteValue % tints.size]

@Composable
fun VideoPlaceholder(camera: Camera, modifier: Modifier = Modifier) {
    val snapshot = rememberSnapshot(camera.id)
    Box(
        modifier = modifier.background(cameraTint(camera.id)),
        contentAlignment = Alignment.Center,
    ) {
        if (snapshot != null) {
            SnapshotImage(snapshot, Modifier.matchParentSize())
            LastSeenChip(snapshot, Modifier.align(Alignment.BottomStart).padding(8.dp))
            return@Box
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_videocam),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = camera.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}
