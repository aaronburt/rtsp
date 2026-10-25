package uk.co.aaronburt.rtsp.player

import android.content.Context
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

private const val RTSP_TIMEOUT_MS = 8_000L
private const val MIN_BUFFER_MS = 1_000

@OptIn(UnstableApi::class)
class Media3Engine(context: Context) : PlayerEngine {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var listener: PlayerEngine.Listener? = null
    private var surface: Surface? = null
    private var muted = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> listener?.onBuffering()
                Player.STATE_READY -> listener?.onPlaying()
                Player.STATE_ENDED -> listener?.onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(describe(error))
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                listener?.onFrameSize(
                    FrameSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio),
                )
            }
        }
    }

    override fun setListener(listener: PlayerEngine.Listener?) {
        this.listener = listener
    }

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        player?.setVideoSurface(surface)
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        player?.volume = if (muted) 0f else 1f
    }

    override fun load(source: StreamSource) {
        stop()
        val latency = source.latencyMs.coerceAtLeast(0)
        val minBuffer = latency.coerceAtLeast(MIN_BUFFER_MS)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, minBuffer * 2, latency, latency)
            .build()
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(source.transport == RtspTransport.TCP)
            .setTimeoutMs(RTSP_TIMEOUT_MS)
            .createMediaSource(MediaItem.fromUri(source.url))
        val created = ExoPlayer.Builder(appContext).setLoadControl(loadControl).build()
        created.addListener(playerListener)
        created.setVideoSurface(surface)
        created.volume = if (muted) 0f else 1f
        created.setMediaSource(mediaSource)
        created.playWhenReady = true
        created.prepare()
        player = created
    }

    override fun stop() {
        val current = player ?: return
        player = null
        current.removeListener(playerListener)
        current.release()
    }

    override fun release() {
        stop()
        listener = null
        surface = null
    }

    private fun describe(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is RtspMediaSource.RtspPlaybackException) {
                return "Camera rejected the stream (check path and credentials)"
            }
            cause = cause.cause
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection lost or camera unreachable"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Unsupported or corrupt stream"
            else -> "Playback error (${error.errorCodeName})"
        }
    }
}
