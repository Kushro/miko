package eu.kanade.tachiyomi.data.download

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the global download speed limiter (see `DownloadThrottle.kt`).
 *
 * The clock and the sleep function are injected: the fake sleep records how long it was asked to wait and
 * advances the fake clock by the same amount, which is exactly what a real `delay` would do.
 */
class DownloadThrottleTest {

    private var now = 0L
    private var limit = 0L
    private val sleeps = mutableListOf<Long>()

    private val throttle = DownloadThrottle(
        bytesPerSecond = { limit },
        clock = { now },
        sleep = { ms ->
            sleeps += ms
            now += ms
        },
    )

    @Test
    fun `no limit never waits`() = runTest {
        limit = 0

        repeat(10) { throttle.acquire(1024L * 1024) }

        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `a burst up to one second of budget does not wait`() = runTest {
        limit = 1024

        throttle.acquire(512)
        throttle.acquire(512)

        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `going over the budget waits the proportional time`() = runTest {
        limit = 1000

        // Empties the one second bucket, still without waiting.
        throttle.acquire(1000)
        throttle.acquire(500)

        assertEquals(listOf(500L), sleeps)
    }

    @Test
    fun `the time spent waiting refills the bucket exactly`() = runTest {
        limit = 1000

        throttle.acquire(1000)
        repeat(4) { throttle.acquire(500) }

        // Every chunk after the initial burst costs the same 500 ms and nothing more.
        assertEquals(listOf(500L, 500L, 500L, 500L), sleeps)
        assertEquals(2000L, now)
    }

    @Test
    fun `a limit change is honoured on the next acquire`() = runTest {
        limit = 1000
        throttle.acquire(1000)
        assertTrue(sleeps.isEmpty())

        // Halving the limit doubles the wait for the very same amount of bytes (500 ms at 1000 B/s).
        limit = 500
        throttle.acquire(500)

        assertEquals(listOf(1000L), sleeps)
    }

    @Test
    fun `setting the limit to zero stops the waiting`() = runTest {
        limit = 1000
        throttle.acquire(1000)
        throttle.acquire(500)
        assertEquals(1, sleeps.size)

        limit = 0
        repeat(5) { throttle.acquire(8192) }

        assertEquals(1, sleeps.size)
    }
}
