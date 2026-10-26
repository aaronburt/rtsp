package uk.co.aaronburt.rtsp.player

/** Current video measurements. Null means that the stream has not supplied that value yet. */
data class StreamDetails(
    val frameSize: FrameSize? = null,
    val frameRate: Float? = null,
    val codec: String? = null,
    val videoBitrate: Long? = null,
    val transport: RtspTransport? = null,
)
