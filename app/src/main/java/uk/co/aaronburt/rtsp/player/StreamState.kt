package uk.co.aaronburt.rtsp.player

sealed interface StreamState {
    data object Idle : StreamState
    data object Connecting : StreamState
    data object Buffering : StreamState
    data object Playing : StreamState
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val delayMs: Long,
        val reason: String,
    ) : StreamState
    data class Failed(val reason: String) : StreamState
}
