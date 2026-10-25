package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPreferencesInstrumentedTest {
    @Test
    fun pinchZoomRequiresOptInAndTheChoiceSurvivesReopeningPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "zoom-test-${UUID.randomUUID()}"
        val storage = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            val preferences = SharedDisplayPreferences(storage)
            assertFalse(preferences.pinchToZoom.value)
            preferences.setPinchToZoom(true)
            assertTrue(SharedDisplayPreferences(storage).pinchToZoom.value)
            preferences.setPinchToZoom(false)
            assertFalse(SharedDisplayPreferences(storage).pinchToZoom.value)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
