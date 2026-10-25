package uk.co.aaronburt.rtsp.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

enum class RtspTransport { TCP, UDP }

fun createRtspPlayer(
    context: Context,
    url: String,
    transport: RtspTransport = RtspTransport.TCP,
): ExoPlayer {
    val source = RtspMediaSource.Factory()
        .setForceUseRtpTcp(transport == RtspTransport.TCP)
        .createMediaSource(MediaItem.fromUri(url))
    return ExoPlayer.Builder(context).build().apply {
        setMediaSource(source)
        prepare()
        playWhenReady = true
    }
}
