package uk.co.aaronburt.rtsp.player

const val DEFAULT_LATENCY_MS = 500

val LATENCY_PRESETS_MS = listOf(0, 250, 500, 1000, 2000)

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
