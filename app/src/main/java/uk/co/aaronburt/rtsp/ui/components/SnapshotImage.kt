package uk.co.aaronburt.rtsp.ui.components

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.snapshot.NoSnapshotRecorder
import uk.co.aaronburt.rtsp.snapshot.NoSnapshotStore
import uk.co.aaronburt.rtsp.snapshot.Snapshot
import uk.co.aaronburt.rtsp.snapshot.SnapshotRecorder
import uk.co.aaronburt.rtsp.snapshot.SnapshotStore
import uk.co.aaronburt.rtsp.ui.theme.MonoTiny
import uk.co.aaronburt.rtsp.ui.theme.Scrim

private const val JUST_NOW_MS = 60_000L
private const val CLOCK_TICK_MS = 30_000L

val LocalSnapshotStore = staticCompositionLocalOf<SnapshotStore> { NoSnapshotStore }

val LocalSnapshotRecorder = staticCompositionLocalOf<SnapshotRecorder> { NoSnapshotRecorder }

@Composable
fun rememberSnapshot(cameraId: String): Snapshot? {
    val store = LocalSnapshotStore.current
    val snapshots by store.snapshots.collectAsStateWithLifecycle()
    return snapshots[cameraId]
}

@Composable
private fun rememberNowMillis(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_TICK_MS)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun SnapshotImage(snapshot: Snapshot, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, snapshot.file, snapshot.capturedAt) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(snapshot.file.path)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.alpha(alpha),
        )
    }
}

@Composable
fun LastSeenChip(snapshot: Snapshot, modifier: Modifier = Modifier) {
    val now by rememberNowMillis()
    val relative = if (now - snapshot.capturedAt < JUST_NOW_MS) {
        stringResource(R.string.snapshot_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(
            snapshot.capturedAt,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
    Text(
        text = stringResource(R.string.snapshot_last_seen, relative),
        style = MonoTiny,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Scrim)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
