package uk.co.aaronburt.rtsp.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uk.co.aaronburt.rtsp.player.CameraConnectionTester
import uk.co.aaronburt.rtsp.player.ConnectionResult

/** Connection status is session data: never persist a guessed online flag with a saved camera. */
class MonitoredCameraRepository(
    private val delegate: CameraRepository,
    private val tester: CameraConnectionTester,
    private val scope: CoroutineScope,
) : CameraRepository by delegate {
    private data class Health(
        val status: CameraConnectionStatus,
        val reason: String? = null,
        val since: String? = null,
    )

    private val health = MutableStateFlow<Map<String, Health>>(emptyMap())
    private val jobs = mutableMapOf<String, Job>()
    private val permits = Semaphore(2)

    override val cameras = combine(delegate.cameras, health) { list, states ->
        list.map { camera -> decorate(camera, states[camera.id]) }
    }.stateIn(
        scope, SharingStarted.Eagerly,
        delegate.cameras.value.map { decorate(it, null) },
    )

    init {
        scope.launch {
            var previous = emptyMap<String, Camera>()
            delegate.cameras.collect { list ->
                val current = list.associateBy { it.id }
                (previous.keys - current.keys).forEach { jobs.remove(it)?.cancel() }
                health.update { it.filterKeys(current::containsKey) }
                list.filter { !sameEndpoint(previous[it.id], it) }.forEach { retry(it.id) }
                previous = current
            }
        }
    }

    override fun get(id: String): Camera? = delegate.get(id)?.let { decorate(it, health.value[id]) }

    override fun refresh() {
        delegate.cameras.value.forEach { if (jobs[it.id]?.isActive != true) retry(it.id) }
    }

    override fun retry(id: String) {
        val camera = delegate.get(id) ?: return
        jobs.remove(id)?.cancel()
        setHealth(id, Health(CameraConnectionStatus.Checking))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = try {
                permits.withPermit { tester.test(camera) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ConnectionResult.Failed("Could not connect to the camera")
            }
            if (!sameEndpoint(delegate.get(id), camera)) return@launch
            when (result) {
                ConnectionResult.Available -> setHealth(id, Health(CameraConnectionStatus.Online))
                is ConnectionResult.Failed -> setHealth(id, offline(result.reason))
            }
        }
        jobs[id] = job
        job.start()
    }

    override fun reportConnection(id: String, online: Boolean) {
        if (delegate.get(id) == null) return
        jobs.remove(id)?.cancel()
        setHealth(id, if (online) Health(CameraConnectionStatus.Online) else offline(null))
    }

    override fun delete(id: String) {
        jobs.remove(id)?.cancel()
        health.update { it - id }
        delegate.delete(id)
    }

    private fun setHealth(id: String, value: Health) {
        health.update { it + (id to value) }
    }

    private fun offline(reason: String?) = Health(
        CameraConnectionStatus.Offline, reason,
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    )

    private fun decorate(camera: Camera, value: Health?): Camera = camera.copy(
        online = value?.status != CameraConnectionStatus.Offline,
        connectionStatus = value?.status ?: CameraConnectionStatus.Unknown,
        connectionError = value?.reason,
        offlineSince = value?.since,
    )

    private fun sameEndpoint(first: Camera?, second: Camera): Boolean = first != null &&
        first.url == second.url && first.username == second.username && first.password == second.password &&
        first.transport == second.transport && first.latencyMs == second.latencyMs
}
