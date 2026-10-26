package uk.co.aaronburt.rtsp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.aaronburt.rtsp.player.CameraConnectionTester
import uk.co.aaronburt.rtsp.player.ConnectionResult

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoredCameraRepositoryTest {
    private val camera = Camera("cam", "Camera", "rtsp://host/main")

    @Test
    fun rejectedAuthenticationMarksCameraOfflineAndRetryRunsAnotherCheck() = runTest {
        var calls = 0
        val repository = MonitoredCameraRepository(
            FakeCameraRepository(listOf(camera)),
            CameraConnectionTester {
                calls++
                if (calls == 1) ConnectionResult.Failed("Authentication failed") else ConnectionResult.Available
            }, backgroundScope,
        )
        assertEquals(CameraConnectionStatus.Unknown, repository.get("cam")!!.connectionStatus)
        runCurrent()
        assertFalse(repository.get("cam")!!.online)
        assertEquals("Authentication failed", repository.get("cam")!!.connectionError)
        repository.retry("cam")
        runCurrent()
        assertEquals(2, calls)
        assertTrue(repository.get("cam")!!.online)
        assertEquals(CameraConnectionStatus.Online, repository.cameras.value.single().connectionStatus)
    }

    @Test
    fun aSuccessfulLivePlayerSupersedesAnOlderPendingProbe() = runTest {
        val pending = CompletableDeferred<ConnectionResult>()
        val repository = MonitoredCameraRepository(
            FakeCameraRepository(listOf(camera)), CameraConnectionTester { pending.await() }, backgroundScope,
        )
        runCurrent()
        assertEquals(CameraConnectionStatus.Checking, repository.get("cam")!!.connectionStatus)
        repository.reportConnection("cam", true)
        pending.complete(ConnectionResult.Failed("old failure"))
        runCurrent()
        assertEquals(CameraConnectionStatus.Online, repository.get("cam")!!.connectionStatus)
    }

    @Test
    fun changedCredentialsAreRecheckedButRenamingDoesNotReconnect() = runTest {
        var calls = 0
        val saved = FakeCameraRepository(listOf(camera))
        val repository = MonitoredCameraRepository(saved, CameraConnectionTester {
            calls++
            ConnectionResult.Available
        }, backgroundScope)
        runCurrent()
        repository.save(camera.copy(name = "Renamed"))
        runCurrent()
        assertEquals(1, calls)
        repository.save(camera.copy(password = "changed"))
        runCurrent()
        assertEquals(2, calls)
        repository.delete("cam")
        runCurrent()
        assertEquals(null, repository.get("cam"))
        assertTrue(repository.cameras.value.isEmpty())
    }
}
