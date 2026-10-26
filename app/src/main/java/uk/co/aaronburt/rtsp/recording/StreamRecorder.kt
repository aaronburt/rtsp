package uk.co.aaronburt.rtsp.recording

import androidx.media3.common.Format
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.aaronburt.rtsp.player.VideoSampleTap

/**
 * Records the compressed video samples of the streams that are already being played,
 * without a second camera connection and without re-encoding.
 */
class StreamRecorder(
    private val store: RecordingStore,
    private val writerFactory: RecordingWriterFactory = Mp4RecordingWriter.Factory,
    private val clock: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "stream-recorder").apply { isDaemon = true }
    },
) : RecordingController {
    private val taps = ConcurrentHashMap<String, CameraTap>()
    private val mutableErrors = MutableSharedFlow<RecordingError>(extraBufferCapacity = 4)

    override val errors: SharedFlow<RecordingError> = mutableErrors.asSharedFlow()

    override fun status(cameraId: String): StateFlow<RecordingStatus> = tap(cameraId).status

    override fun start(cameraId: String, title: String) = tap(cameraId).request(title)

    override fun stop(cameraId: String) = tap(cameraId).cancel()

    override fun tapFor(cameraId: String): VideoSampleTap = tap(cameraId)

    private fun tap(cameraId: String): CameraTap = taps.getOrPut(cameraId) { CameraTap(cameraId) }

    private inner class CameraTap(private val cameraId: String) : VideoSampleTap {
        private val mutableStatus = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
        val status: StateFlow<RecordingStatus> = mutableStatus.asStateFlow()

        @Volatile
        private var wanted = false

        // Only touched on the executor.
        private var format: Format? = null
        private var title = ""
        private var file: File? = null
        private var writer: RecordingWriter? = null

        fun request(title: String) {
            executor.execute { this.title = title }
            mutableStatus.value = RecordingStatus.Waiting
            wanted = true
        }

        fun cancel() {
            wanted = false
            executor.execute { finish() }
        }

        override fun onFormat(format: Format) {
            executor.execute { this.format = format }
        }

        override fun onSample(data: ByteBuffer, timeUs: Long, keyFrame: Boolean) {
            if (!wanted) return
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            executor.execute { write(bytes, timeUs, keyFrame) }
        }

        override fun onStopped() {
            wanted = false
            executor.execute {
                format = null
                finish()
            }
        }

        private fun write(bytes: ByteArray, timeUs: Long, keyFrame: Boolean) {
            if (!wanted && writer == null) return
            try {
                if (writer == null) {
                    if (!keyFrame) return
                    val current = format ?: return
                    val startedAt = clock()
                    val target = store.create(title, startedAt)
                    file = target
                    writer = writerFactory.open(target, current)
                    mutableStatus.value = RecordingStatus.Active(startedAt)
                }
                writer?.write(bytes, timeUs, keyFrame)
            } catch (e: Exception) {
                wanted = false
                abort()
                mutableErrors.tryEmit(RecordingError(cameraId, e.message ?: "Recording failed"))
            }
        }

        private fun finish() {
            val target = file
            val open = writer
            writer = null
            file = null
            try {
                open?.close()
                target?.let(store::commit)
            } catch (e: Exception) {
                target?.let(store::discard)
                mutableErrors.tryEmit(RecordingError(cameraId, e.message ?: "Could not save recording"))
            } finally {
                mutableStatus.value = RecordingStatus.Idle
            }
        }

        private fun abort() {
            val target = file
            val open = writer
            writer = null
            file = null
            runCatching { open?.close() }
            target?.let(store::discard)
            mutableStatus.value = RecordingStatus.Idle
        }
    }
}
