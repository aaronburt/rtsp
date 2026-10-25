package uk.co.aaronburt.rtsp.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPreferencesTest {
    @Test
    fun clockIsOffByDefault() {
        assertFalse(InMemoryDisplayPreferences().showClock.value)
    }

    @Test
    fun clockCanBeToggled() {
        val prefs = InMemoryDisplayPreferences()
        prefs.setShowClock(true)
        assertTrue(prefs.showClock.value)
        prefs.setShowClock(false)
        assertFalse(prefs.showClock.value)
    }
}
