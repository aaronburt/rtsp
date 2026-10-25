package uk.co.aaronburt.rtsp.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectPolicyTest {
    private val policy = ReconnectPolicy(baseDelayMs = 1_000, maxDelayMs = 30_000, maxAttempts = 10)

    @Test
    fun delayDoublesPerAttempt() {
        assertEquals(1_000L, policy.delayFor(1))
        assertEquals(2_000L, policy.delayFor(2))
        assertEquals(4_000L, policy.delayFor(3))
        assertEquals(8_000L, policy.delayFor(4))
        assertEquals(16_000L, policy.delayFor(5))
    }

    @Test
    fun delayIsCapped() {
        assertEquals(30_000L, policy.delayFor(6))
        assertEquals(30_000L, policy.delayFor(10))
    }

    @Test
    fun delayDoesNotOverflow() {
        assertEquals(30_000L, policy.delayFor(100))
        assertEquals(30_000L, policy.delayFor(Int.MAX_VALUE))
    }
}
