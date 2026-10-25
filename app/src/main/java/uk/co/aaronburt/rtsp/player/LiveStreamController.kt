package uk.co.aaronburt.rtsp.player

import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEFAULT_STALL_TIMEOUT_MS = 15_000L

class LiveStreamController(
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    private val policy: ReconnectPolicy = ReconnectPolicy(),
    private val stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
) : PlayerEngine.Listener {

    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _active = MutableStateFlow<ActiveStream?>(null)
    val active: StateFlow<ActiveStream?> = _active.asStateFlow()

    private val _frameSize = MutableStateFlow<FrameSize?>(null)
    val frameSize: StateFlow<FrameSize?> = _frameSize.asStateFlow()

    private var requested: StreamConfig? = null
    private var attempt = 0
    private var suspended = false
    private var released = false
    private var surface: Surface? = null
    private var reconnectJob: Job? = null
    private var stallJob: Job? = null

    init {
        engine.setListener(this)
    }

    fun start(config: StreamConfig) {
        if (released) return
        val unchanged = config == requested && !suspended && _state.value != StreamState.Idle
        requested = config
        suspended = false
        if (unchanged) return
        _active.value = ActiveStream(config, StreamQuality.Main)
        connectFresh()
    }

    fun selectQuality(quality: StreamQuality) {
        val current = _active.value ?: return
        if (current.quality == quality) return
        if (quality == StreamQuality.Sub && !current.config.hasSub) return
        _active.value = current.copy(quality = quality)
        restartIfRunning()
    }

    fun setTransport(transport: RtspTransport) {
        val current = _active.value ?: return
        if (current.config.transport == transport) return
        _active.value = current.copy(config = current.config.copy(transport = transport))
        restartIfRunning()
    }

    fun setLatency(latencyMs: Int) {
        val current = _active.value ?: return
        if (current.config.latencyMs == latencyMs) return
        _active.value = current.copy(config = current.config.copy(latencyMs = latencyMs))
        restartIfRunning()
    }

    fun retry() {
        if (_active.value == null || suspended || released) return
        connectFresh()
    }

    fun pause() {
        if (released || _active.value == null) return
        suspended = true
        cancelTimers()
        engine.stop()
        _frameSize.value = null
        _state.value = StreamState.Idle
    }

    fun resume() {
        if (released || !suspended) return
        suspended = false
        connectFresh()
    }

    fun release() {
        if (released) return
        released = true
        cancelTimers()
        engine.setListener(null)
        engine.release()
        _state.value = StreamState.Idle
    }

    fun setSurface(surface: Surface?) {
        this.surface = surface
        engine.setSurface(surface)
    }

    fun setMuted(muted: Boolean) {
        engine.setMuted(muted)
    }

    fun clearSurface(surface: Surface) {
        if (this.surface !== surface) return
        this.surface = null
        engine.setSurface(null)
    }

    override fun onBuffering() {
        if (!accepting()) return
        _state.value = StreamState.Buffering
        armStall()
    }

    override fun onPlaying() {
        if (!accepting()) return
        stallJob?.cancel()
        attempt = 0
        _state.value = StreamState.Playing
    }

    override fun onFrameSize(size: FrameSize) {
        if (!accepting()) return
        _frameSize.value = size
    }

    override fun onError(reason: String) {
        if (!accepting()) return
        handleFailure(reason)
    }

    override fun onEnded() {
        if (!accepting()) return
        handleFailure("Stream ended")
    }

    private fun accepting(): Boolean = !released && !suspended && when (_state.value) {
        StreamState.Connecting, StreamState.Buffering, StreamState.Playing -> true
        else -> false
    }

    private fun restartIfRunning() {
        if (suspended || released) return
        connectFresh()
    }

    private fun connectFresh() {
        attempt = 0
        connect()
    }

    private fun connect() {
        val current = _active.value ?: return
        cancelTimers()
        _state.value = StreamState.Connecting
        armStall()
        try {
            engine.load(StreamSource(current.url, current.config.transport, current.config.latencyMs))
        } catch (e: Exception) {
            handleFailure(e.message ?: "Could not start stream")
        }
    }

    private fun armStall() {
        if (stallJob?.isActive == true) return
        stallJob = scope.launch {
            delay(stallTimeoutMs)
            handleFailure("Stream stalled")
        }
    }

    private fun handleFailure(reason: String) {
        cancelTimers()
        engine.stop()
        attempt += 1
        if (attempt > policy.maxAttempts) {
            _state.value = StreamState.Failed(reason)
            return
        }
        val delayMs = policy.delayFor(attempt)
        _state.value = StreamState.Reconnecting(attempt, policy.maxAttempts, delayMs, reason)
        reconnectJob = scope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun cancelTimers() {
        reconnectJob?.cancel()
        reconnectJob = null
        stallJob?.cancel()
        stallJob = null
    }
}
