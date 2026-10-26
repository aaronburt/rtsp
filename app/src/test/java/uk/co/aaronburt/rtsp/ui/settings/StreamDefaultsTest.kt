package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.FakeCameraRepository
import uk.co.aaronburt.rtsp.data.Transport
import uk.co.aaronburt.rtsp.ui.form.CameraFormViewModel

@RunWith(RobolectricTestRunner::class)
class StreamDefaultsTest {
    @Test fun preferencesSurviveReopeningAndOnlyNewFormsUseTheUpdatedValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = context.getSharedPreferences("stream-defaults-test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val preferences = SharedStreamDefaultsPreferences(storage)
        assertEquals(StreamDefaults(), preferences.defaults.value)
        val repository = FakeCameraRepository(listOf(Camera("saved", "Saved", "rtsp://h/main")))
        val alreadyOpen = CameraFormViewModel(null, repository, defaults = preferences.defaults.value)
        preferences.setTransport(Transport.Udp)
        preferences.setLatency(650)
        preferences.setStartMuted(false)
        val restored = SharedStreamDefaultsPreferences(storage)
        assertEquals(StreamDefaults(Transport.Udp, 650, false), restored.defaults.value)
        assertEquals(Transport.Tcp, alreadyOpen.state.value.transport)
        val newForm = CameraFormViewModel(null, repository, defaults = restored.defaults.value)
        assertEquals(Transport.Udp, newForm.state.value.transport)
        assertEquals(650, newForm.state.value.latencyMs)
        assertFalse(newForm.state.value.startMuted)
        val edit = CameraFormViewModel("saved", repository, defaults = restored.defaults.value)
        assertEquals(Transport.Tcp, edit.state.value.transport)
        assertEquals(200, edit.state.value.latencyMs)
        newForm.setName("New")
        newForm.setUrl("rtsp://new/main")
        newForm.setTransport(Transport.Auto)
        assertEquals(true, newForm.save())
        assertEquals(Transport.Auto, repository.cameras.value.last().transport)
        assertEquals(650, repository.cameras.value.last().latencyMs)
        assertEquals(StreamDefaults(Transport.Udp, 650, false), restored.defaults.value)
    }

    @Test fun unknownStoredTransportFallsBackAndLatencyStaysWithinTheSupportedRange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = context.getSharedPreferences("stream-defaults-invalid", Context.MODE_PRIVATE)
        storage.edit().clear().putString("transport", "future-value").putInt("latency_ms", 9000).commit()
        val preferences = SharedStreamDefaultsPreferences(storage)
        assertEquals(Transport.Tcp, preferences.defaults.value.transport)
        assertEquals(2000, preferences.defaults.value.latencyMs)
        preferences.setLatency(-500)
        assertEquals(0, SharedStreamDefaultsPreferences(storage).defaults.value.latencyMs)
    }
}
