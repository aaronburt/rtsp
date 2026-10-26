package uk.co.aaronburt.rtsp.ui.cameras

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewRefreshSchedulerTest {
    @Test fun refreshWaitsFifteenSecondsAfterCaptureAndStopsOnCancellation() = runTest {
        val scheduler = PreviewRefreshScheduler()
        val starts = mutableListOf<Long>()
        val job = backgroundScope.launch {
            scheduler.run { starts += testScheduler.currentTime }
        }
        runCurrent()
        assertEquals(listOf(0L), starts)
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(1, starts.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 15_000L), starts)
        job.cancel()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, starts.size)
    }

    @Test fun slowCamerasNeverOverlapAndAtMostTwoConnectionsRunTogether() = runTest {
        val scheduler = PreviewRefreshScheduler()
        var active = 0
        var maximum = 0
        var completed = 0
        repeat(5) {
            backgroundScope.launch {
                scheduler.run {
                    active++
                    maximum = maxOf(maximum, active)
                    try {
                        delay(20_000)
                        completed++
                    } finally {
                        active--
                    }
                }
            }
        }
        runCurrent()
        assertEquals(2, active)
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(2, maximum)
        assertEquals(6, completed)
    }
}
