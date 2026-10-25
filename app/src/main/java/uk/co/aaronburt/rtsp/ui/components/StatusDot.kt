package uk.co.aaronburt.rtsp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.aaronburt.rtsp.R
import uk.co.aaronburt.rtsp.player.StreamStatus
import uk.co.aaronburt.rtsp.ui.theme.Danger
import uk.co.aaronburt.rtsp.ui.theme.StatusPending
import uk.co.aaronburt.rtsp.ui.theme.Success

@Composable
fun StatusDot(status: StreamStatus, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val description = stringResource(
        when (status) {
            StreamStatus.Live -> R.string.status_live
            StreamStatus.Pending -> R.string.status_pending
            StreamStatus.Error -> R.string.status_error
        },
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when (status) {
                    StreamStatus.Live -> Success
                    StreamStatus.Pending -> StatusPending
                    StreamStatus.Error -> Danger
                },
            )
            .semantics { contentDescription = description },
    )
}
