package uk.co.aaronburt.rtsp.player

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.aaronburt.rtsp.data.Camera

sealed interface ConnectionResult {
    data object Available : ConnectionResult
    data class Failed(val reason: String) : ConnectionResult
}

fun interface CameraConnectionTester {
    suspend fun test(camera: Camera): ConnectionResult
}

object NoConnectionTester : CameraConnectionTester {
    override suspend fun test(camera: Camera): ConnectionResult =
        ConnectionResult.Failed("Connection testing is unavailable")
}

/** Tests the actual RTSP stream, including authentication and incoming media, without saving it. */
class Media3ConnectionTester(context: Context) : CameraConnectionTester {
    private val appContext = context.applicationContext

    override suspend fun test(camera: Camera): ConnectionResult = withContext(Dispatchers.Main.immediate) {
        val result = CompletableDeferred<ConnectionResult>()
        val engine = Media3Engine(appContext)
        var ready = false
        var receivedVideo = false
        fun completeIfReady() {
            if (ready && receivedVideo) result.complete(ConnectionResult.Available)
        }
        engine.setListener(object : PlayerEngine.Listener {
            override fun onBuffering() { ready = false }
            override fun onFrameSize(size: FrameSize) = Unit
            override fun onPlaying() {
                ready = true
                completeIfReady()
            }
            override fun onStreamDetails(details: StreamDetails) {
                receivedVideo = receivedVideo || (details.videoBitrate ?: 0) > 0
                completeIfReady()
            }
            override fun onError(reason: String) { result.complete(ConnectionResult.Failed(reason)) }
            override fun onEnded() { result.complete(ConnectionResult.Failed("Stream ended")) }
        })
        try {
            val config = camera.toStreamConfig()
            engine.setMuted(true)
            engine.load(StreamSource(config.mainUrl, config.transport, config.latencyMs))
            withTimeoutOrNull(20_000) { result.await() }
                ?: ConnectionResult.Failed("Camera did not respond with a playable stream")
        } finally {
            engine.release()
        }
    }
}
