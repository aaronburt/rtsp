package uk.co.aaronburt.rtsp.player

enum class StreamStatus { Live, Pending, Error }

fun StreamState.toStatus(): StreamStatus = when (this) {
    StreamState.Playing -> StreamStatus.Live
    is StreamState.Failed -> StreamStatus.Error
    StreamState.Idle, StreamState.Connecting, StreamState.Buffering, is StreamState.Reconnecting -> StreamStatus.Pending
}
