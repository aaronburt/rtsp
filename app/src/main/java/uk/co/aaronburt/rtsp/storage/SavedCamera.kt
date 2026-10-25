package uk.co.aaronburt.rtsp.storage

enum class CameraTransport { Auto, Tcp, Udp }

enum class StreamKind { Main, Sub }

data class SavedCamera(
    val id: String,
    val name: String,
    val mainUrl: String,
    val subUrl: String? = null,
    val username: String = "",
    val password: String = "",
    val transport: CameraTransport = CameraTransport.Tcp,
    val latencyMs: Int = 200,
    val autoReconnect: Boolean = true,
    val startMuted: Boolean = true,
    val favourite: Boolean = false,
    val createdAt: Long = 0,
) {
    val hasSubStream: Boolean get() = !subUrl.isNullOrBlank()

    fun streamUrl(kind: StreamKind): String {
        val base = if (kind == StreamKind.Sub && hasSubStream) subUrl!! else mainUrl
        return injectUrlCredentials(base, username, password)
    }

    override fun toString(): String =
        "SavedCamera(id=$id, name=$name, transport=$transport, hasPassword=${password.isNotEmpty()})"
}
