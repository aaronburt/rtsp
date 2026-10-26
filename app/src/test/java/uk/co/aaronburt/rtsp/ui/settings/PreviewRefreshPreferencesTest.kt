package uk.co.aaronburt.rtsp.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewRefreshPreferencesTest {
    @Test fun previewRefreshRequiresOptInAndPersistsBothChoices() {
        assertFalse(InMemoryDisplayPreferences().refreshPreviews.value)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = context.getSharedPreferences("preview-test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val preferences = SharedDisplayPreferences(storage)
        assertFalse(preferences.refreshPreviews.value)
        preferences.setRefreshPreviews(true)
        assertTrue(SharedDisplayPreferences(storage).refreshPreviews.value)
        preferences.setRefreshPreviews(false)
        assertFalse(SharedDisplayPreferences(storage).refreshPreviews.value)
    }
}
