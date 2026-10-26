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

    private val _details = MutableStateFlow(StreamDetails())
    val details: StateFlow<StreamDetails> = _details.asStateFlow()
    private var lastPresentationTimeUs: Long? = null
    private val _videoFrameAvailable = MutableStateFlow(false)
    val videoFrameAvailable: StateFlow<Boolean> = _videoFrameAvailable.asStateFlow()
    var captureGeneration: Long = 0
        private set

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

    fun start(config: StreamConfig, quality: StreamQuality? = null) {
        if (released) return
        val current = _active.value
        val sameConfig = config == requested
        val wanted = quality ?: if (sameConfig) current?.quality ?: StreamQuality.Main else StreamQuality.Main
        val unchanged = sameConfig && current?.quality == wanted && !suspended && _state.value != StreamState.Idle
        requested = config
        suspended = false
        if (unchanged) return
        _active.value = ActiveStream(config, wanted)
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
        captureGeneration++
        _videoFrameAvailable.value = false
        cancelTimers()
        engine.stop()
        _frameSize.value = null
        _details.value = StreamDetails()
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
        captureGeneration++
        _videoFrameAvailable.value = false
        cancelTimers()
        engine.setListener(null)
        engine.release()
        _state.value = StreamState.Idle
    }

    fun setSurface(surface: Surface?) {
        if (this.surface !== surface) {
            captureGeneration++
            _videoFrameAvailable.value = false
        }
        this.surface = surface
        engine.setSurface(surface)
    }

    fun setMuted(muted: Boolean) {
        engine.setMuted(muted)
    }

    fun clearSurface(surface: Surface) {
        if (this.surface !== surface) return
        setSurface(null)
    }

    override fun onBuffering() {
        if (!accepting()) return
        _videoFrameAvailable.value = false
        _state.value = StreamState.Buffering
        armStall()
    }

    override fun onPlaying() {
        if (!accepting()) return
        attempt = 0
        _state.value = StreamState.Playing
        armStall()
    }

    override fun onVideoFrame(presentationTimeUs: Long) {
        if (!accepting() || lastPresentationTimeUs == presentationTimeUs) return
        lastPresentationTimeUs = presentationTimeUs
        _videoFrameAvailable.value = true
        stallJob?.cancel()
        stallJob = null
        armStall()
    }

    override fun onStreamDetails(details: StreamDetails) {
        if (!accepting()) return
        _details.value = details
        details.frameSize?.let { _frameSize.value = it }
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
        captureGeneration++
        _videoFrameAvailable.value = false
        cancelTimers()
        _frameSize.value = null
        _details.value = StreamDetails()
        lastPresentationTimeUs = null
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
        captureGeneration++
        _videoFrameAvailable.value = false
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
