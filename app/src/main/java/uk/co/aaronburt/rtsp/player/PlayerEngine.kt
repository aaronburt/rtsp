package uk.co.aaronburt.rtsp.player

import android.view.Surface

data class StreamSource(
    val url: String,
    val transport: RtspTransport,
    val latencyMs: Int,
)

data class FrameSize(val width: Int, val height: Int, val pixelRatio: Float = 1f) {
    val aspectRatio: Float
        get() = if (height <= 0) 0f else width * pixelRatio / height
}

interface PlayerEngine {
    fun setListener(listener: Listener?)
    fun setSurface(surface: Surface?)
    fun setMuted(muted: Boolean)
    fun load(source: StreamSource)
    fun stop()
    fun release()

    interface Listener {
        fun onBuffering()
        fun onPlaying()
        fun onFrameSize(size: FrameSize)
        fun onVideoFrame(presentationTimeUs: Long) {}
        fun onStreamDetails(details: StreamDetails) {}
        fun onError(reason: String)
        fun onEnded()
    }
}
