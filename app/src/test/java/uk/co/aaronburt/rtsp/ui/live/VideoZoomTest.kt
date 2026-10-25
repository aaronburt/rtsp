package uk.co.aaronburt.rtsp.ui.live

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoZoomTest {
    @Test
    fun pinchKeepsThePointUnderTheFingersInPlace() {
        val before = VideoZoomTransform(2f, 20f, -10f)
        val after = before.changed(1.5f, 0f, 0f, 100f, 50f)
        assertEquals((100f - before.x) / before.scale, (100f - after.x) / after.scale, 0.001f)
        assertEquals((50f - before.y) / before.scale, (50f - after.y) / after.scale, 0.001f)
    }

    @Test
    fun zoomIsLimitedToFourTimesAndNeverBelowOne() {
        assertEquals(4f, VideoZoomTransform().changed(20f, 0f, 0f, 0f, 0f).scale, 0f)
        assertEquals(1f, VideoZoomTransform(2f).changed(0.1f, 0f, 0f, 0f, 0f).scale, 0f)
    }

    @Test
    fun panningStopsAtTheEdgesOfTheZoomedImage() {
        val result = VideoZoomTransform(2f, 900f, -900f).constrained(400f, 200f, 400f, 200f)
        assertEquals(200f, result.x, 0f)
        assertEquals(-100f, result.y, 0f)
    }

    @Test
    fun letterboxedVideoStaysCentredUntilItFillsTheViewport() {
        val result = VideoZoomTransform(2f, 900f, 900f).constrained(400f, 400f, 400f, 100f)
        assertEquals(200f, result.x, 0f)
        assertEquals(0f, result.y, 0f)
    }

    @Test
    fun zoomingBackOutRestoresTheWholeImage() {
        val result = VideoZoomTransform(3f, 100f, -100f)
            .changed(0.1f, 20f, -20f, 50f, 50f)
            .constrained(400f, 200f, 400f, 200f)
        assertEquals(VideoZoomTransform(), result)
    }

    @Test
    fun resizingReclampsTheOffsetToTheNewVideoBounds() {
        val state = VideoZoomState(VideoZoomTransform(2f, 200f, 100f))
        state.resize(400, 200, 400, 200)
        state.resize(100, 100, 100, 50)
        assertEquals(VideoZoomTransform(2f, 50f, 0f), state.transform)
        state.reset()
        assertEquals(VideoZoomTransform(), state.transform)
    }
}
