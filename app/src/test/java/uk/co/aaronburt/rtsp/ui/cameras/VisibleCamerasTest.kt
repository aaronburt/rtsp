package uk.co.aaronburt.rtsp.ui.cameras

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.aaronburt.rtsp.data.sampleCameras

class VisibleCamerasTest {
    private val cameras = sampleCameras()

    @Test
    fun putsOfflineCamerasFirst() {
        val result = visibleCameras(cameras, CameraFilter.All, "")
        assertEquals("back-gate", result.first().id)
        assertEquals(cameras.size, result.size)
    }

    @Test
    fun filtersOffline() {
        val result = visibleCameras(cameras, CameraFilter.Offline, "")
        assertEquals(listOf("back-gate"), result.map { it.id })
    }

    @Test
    fun filtersFavourites() {
        val result = visibleCameras(cameras, CameraFilter.Favourites, "")
        assertEquals(setOf("front-door", "driveway"), result.map { it.id }.toSet())
    }

    @Test
    fun searchesByNameIgnoringCase() {
        val result = visibleCameras(cameras, CameraFilter.All, "  GAR ")
        assertEquals(setOf("garden", "garage"), result.map { it.id }.toSet())
    }

    @Test
    fun combinesFilterAndSearch() {
        val result = visibleCameras(cameras, CameraFilter.Favourites, "door")
        assertEquals(listOf("front-door"), result.map { it.id })
    }
}
