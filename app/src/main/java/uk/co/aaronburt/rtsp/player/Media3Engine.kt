package uk.co.aaronburt.rtsp.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val RTSP_TIMEOUT_MS = 8_000L
private const val MIN_BUFFER_MS = 1_000
private const val DIAGNOSTICS_INTERVAL_MS = 1_000L

@OptIn(UnstableApi::class)
class Media3Engine(context: Context, private val tap: VideoSampleTap? = null) : PlayerEngine {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var listener: PlayerEngine.Listener? = null
    private var surface: Surface? = null
    private var muted = false
    private var diagnostics: Runnable? = null

    override fun setListener(listener: PlayerEngine.Listener?) { this.listener = listener }

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
        // Keep measurements separate for each load so late callbacks cannot alter a new session.
        val metrics = VideoMetrics(tap)
        val latency = source.latencyMs.coerceAtLeast(0)
        val minBuffer = latency.coerceAtLeast(MIN_BUFFER_MS)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, minBuffer * 2, latency, latency)
            .build()
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(source.transport == RtspTransport.TCP)
            .setSocketFactory(TransportTrackingSocketFactory { metrics.transport.set(it) })
            .setTimeoutMs(RTSP_TIMEOUT_MS)
            .createMediaSource(MediaItem.fromUri(source.url))
        val created = ExoPlayer.Builder(appContext, TappingRenderersFactory(appContext, metrics))
            .setLoadControl(loadControl)
            .build()
        player = created
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== created) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> listener?.onBuffering()
                    Player.STATE_READY -> listener?.onPlaying()
                    Player.STATE_ENDED -> listener?.onEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player === created) listener?.onError(describePlaybackError(error))
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (player !== created || videoSize.width <= 0 || videoSize.height <= 0) return
                val size = FrameSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
                metrics.size = size
                listener?.onFrameSize(size)
            }
        })
        created.setVideoFrameMetadataListener { presentationTimeUs, _, format, _ ->
            metrics.format.set(format)
            metrics.frameTimeUs.set(presentationTimeUs)
            metrics.frames.incrementAndGet()
        }
        created.setVideoSurface(surface)
        created.volume = if (muted) 0f else 1f
        created.setMediaSource(mediaSource)
        created.playWhenReady = true
        created.prepare()
        var previousReportMs = SystemClock.elapsedRealtime()
        diagnostics = object : Runnable {
            override fun run() {
                if (player !== created) return
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = (now - previousReportMs).coerceAtLeast(1)
                previousReportMs = now
                val format = metrics.format.get()
                val frames = metrics.frames.getAndSet(0)
                val bitrate = metrics.bytes.getAndSet(0) * 8_000 / elapsedMs
                listener?.onStreamDetails(
                    StreamDetails(
                        frameSize = metrics.size ?: format?.takeIf { it.width > 0 && it.height > 0 }
                            ?.let { FrameSize(it.width, it.height, it.pixelWidthHeightRatio) },
                        frameRate = format?.frameRate?.takeIf { it > 0 && it.isFinite() }
                            ?: (frames * 1_000f / elapsedMs).takeIf { frames > 0 },
                        codec = format?.let(::videoCodec),
                        videoBitrate = bitrate.takeIf { format != null },
                        transport = metrics.transport.get(),
                    ),
                )
                val timeUs = metrics.frameTimeUs.get()
                if (timeUs != Long.MIN_VALUE) listener?.onVideoFrame(timeUs)
                if (player === created) handler.postDelayed(this, DIAGNOSTICS_INTERVAL_MS)
            }
        }.also { handler.postDelayed(it, DIAGNOSTICS_INTERVAL_MS) }
    }

    override fun stop() {
        diagnostics?.let(handler::removeCallbacks)
        diagnostics = null
        val current = player ?: return
        player = null
        current.release()
        tap?.onStopped()
    }

    override fun release() {
        stop()
        listener = null
        surface = null
    }
}

@OptIn(UnstableApi::class)
private class VideoMetrics(private val tap: VideoSampleTap?) : VideoSampleTap {
    val format = AtomicReference<Format?>()
    val transport = AtomicReference<RtspTransport?>()
    val frameTimeUs = AtomicLong(Long.MIN_VALUE)
    val frames = AtomicLong()
    val bytes = AtomicLong()
    var size: FrameSize? = null // Only read and written on the application thread.

    override fun onFormat(format: Format) {
        this.format.set(format)
        tap?.onFormat(format)
    }

    override fun onSample(data: ByteBuffer, timeUs: Long, keyFrame: Boolean) {
        bytes.addAndGet(data.remaining().toLong())
        tap?.onSample(data, timeUs, keyFrame)
    }

    override fun onStopped() = Unit
}

@OptIn(UnstableApi::class)
private fun videoCodec(format: Format): String? = when (format.sampleMimeType) {
    "video/avc" -> "H.264"
    "video/hevc" -> "H.265"
    "video/av01" -> "AV1"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/x-vnd.on2.vp9" -> "VP9"
    else -> format.codecs ?: format.sampleMimeType
}

@OptIn(UnstableApi::class)
internal fun describePlaybackError(error: PlaybackException): String {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is RtspMediaSource.RtspPlaybackException) {
            // Media3 emits "<METHOD> <STATUS>" after authentication retries are exhausted.
            val status = Regex("(?:OPTIONS|DESCRIBE|SETUP|PLAY) (\\d{3})").find(cause.message.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()
            return when (status) {
                400 -> "Camera rejected the RTSP request (check the stream URL and path)"
                401 -> "Authentication failed (check username and password)"
                403 -> "Camera denied access to this stream"
                404 -> "Stream path not found (check the RTSP URL)"
                461 -> "Camera does not support the requested transport"
                else -> "Camera rejected the stream (check path and credentials)"
            }
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
