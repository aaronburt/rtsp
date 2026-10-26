package uk.co.aaronburt.rtsp.data

enum class Transport { Auto, Tcp, Udp }
enum class CameraConnectionStatus { Unknown, Checking, Online, Offline }

data class Camera(
    val id: String,
    val name: String,
    val url: String,
    val subUrl: String? = null,
    val username: String = "",
    val password: String = "",
    val transport: Transport = Transport.Tcp,
    val latencyMs: Int = 200,
    val autoReconnect: Boolean = true,
    val startMuted: Boolean = true,
    val favourite: Boolean = false,
    val online: Boolean = true,
    val offlineSince: String? = null,
    val lastFrameAt: String? = null,
    val connectionStatus: CameraConnectionStatus = if (online) CameraConnectionStatus.Online else CameraConnectionStatus.Offline,
    val connectionError: String? = null,
)
