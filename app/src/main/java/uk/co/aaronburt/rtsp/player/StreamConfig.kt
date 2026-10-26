package uk.co.aaronburt.rtsp.player

import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.data.splitCredentials

const val DEFAULT_LATENCY_MS = 500

enum class RtspTransport { TCP, UDP }

enum class StreamQuality { Main, Sub }

data class StreamConfig(
    val name: String,
    val mainUrl: String,
    val subUrl: String? = null,
    val transport: RtspTransport = RtspTransport.TCP,
    val latencyMs: Int = DEFAULT_LATENCY_MS,
) {
    val hasSub: Boolean get() = !subUrl.isNullOrBlank()

    fun urlFor(quality: StreamQuality): String = when (quality) {
        StreamQuality.Main -> mainUrl
        StreamQuality.Sub -> subUrl?.takeIf { it.isNotBlank() } ?: mainUrl
    }
}

data class ActiveStream(val config: StreamConfig, val quality: StreamQuality) {
    val url: String get() = config.urlFor(quality)
}

fun Camera.toStreamConfig(): StreamConfig = StreamConfig(
    name = name,
    mainUrl = withCredentials(splitCredentials(url).url, username, password),
    subUrl = subUrl?.takeIf { it.isNotBlank() }
        ?.let { withCredentials(splitCredentials(it).url, username, password) },
    transport = when (transport) {
        Transport.Udp -> RtspTransport.UDP
        Transport.Tcp, Transport.Auto -> RtspTransport.TCP
    },
    latencyMs = latencyMs,
)
