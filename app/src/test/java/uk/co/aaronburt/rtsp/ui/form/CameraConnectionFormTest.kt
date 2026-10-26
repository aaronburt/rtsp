package uk.co.aaronburt.rtsp.ui.form

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.FakeCameraRepository
import uk.co.aaronburt.rtsp.player.CameraConnectionTester
import uk.co.aaronburt.rtsp.player.ConnectionResult

@OptIn(ExperimentalCoroutinesApi::class)
class CameraConnectionFormTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun testsUnsavedCredentialsAndShowsTheReturnedError() = runTest {
        var tested: Camera? = null
        val repository = FakeCameraRepository(emptyList())
        val form = CameraFormViewModel(null, repository, CameraConnectionTester {
            tested = it
            ConnectionResult.Failed("Authentication failed")
        })
        form.setUrl("rtsp://viewer:wrong@host/main")
        form.testConnection()
        assertTrue(form.state.value.testing)
        runCurrent()
        assertEquals("viewer", tested!!.username)
        assertEquals("wrong", tested!!.password)
        assertEquals("rtsp://host/main", tested!!.url)
        assertEquals(ConnectionResult.Failed("Authentication failed"), form.state.value.connectionResult)
        assertFalse(form.state.value.testing)
        assertTrue(repository.cameras.value.isEmpty())
    }

    @Test
    fun editingTheEndpointDiscardsAnObsoleteTestResult() = runTest {
        val pending = CompletableDeferred<ConnectionResult>()
        val form = CameraFormViewModel(null, FakeCameraRepository(emptyList()), CameraConnectionTester { pending.await() })
        form.setUrl("rtsp://host/old")
        form.testConnection()
        runCurrent()
        form.setUrl("rtsp://host/new")
        pending.complete(ConnectionResult.Available)
        runCurrent()
        assertEquals(null, form.state.value.connectionResult)
        assertFalse(form.state.value.testing)
    }
}
