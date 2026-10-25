package uk.co.aaronburt.rtsp.player

data class ReconnectPolicy(
    val baseDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    val maxAttempts: Int = 10,
) {
    fun delayFor(attempt: Int): Long {
        if (attempt <= 1) return baseDelayMs.coerceAtMost(maxDelayMs)
        val shift = (attempt - 1).coerceAtMost(30)
        val delay = baseDelayMs * (1L shl shift)
        return if (delay <= 0L || delay > maxDelayMs) maxDelayMs else delay
    }
}
