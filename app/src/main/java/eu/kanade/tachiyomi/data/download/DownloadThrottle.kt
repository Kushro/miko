package eu.kanade.tachiyomi.data.download

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MIKO — process-wide token bucket that caps the aggregate download speed.
 *
 * A single instance is shared by every concurrent chapter/page download (registered as a singleton in
 * `AppModule`), so the configured limit is a global one instead of a per-worker one.
 *
 * The bucket holds at most one second worth of bytes (a burst right after an idle period is allowed) and
 * refills continuously with the elapsed time. The limit is read from [bytesPerSecond] on **every**
 * [acquire], so changing the preference takes effect on the next chunk without recreating anything; a
 * limit of `0` (or less) means "unlimited" and returns without even taking the lock.
 *
 * Internally everything is kept in *milli-bytes* (`bytes * 1000`) so the integer arithmetic carries the
 * remainder instead of silently dropping it on every chunk.
 *
 * [clock] has no default on purpose: `SystemClock` does not exist on the JVM test classpath, so the
 * Android call site (`AppModule`) passes `SystemClock::elapsedRealtime` (monotonic, keeps counting while
 * the device sleeps) and tests pass a fake clock. Both [clock] and [sleep] are expressed in milliseconds.
 */
class DownloadThrottle(
    private val bytesPerSecond: () -> Long,
    private val clock: () -> Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()

    /** Available budget in milli-bytes; may go negative, meaning debt already accounted for. */
    private var availableMilliBytes = 0L

    /** Timestamp of the last refill, `null` until the first [acquire] (the bucket then starts full). */
    private var lastRefillMs: Long? = null

    /**
     * Suspends the caller until [bytes] more bytes fit in the configured budget.
     *
     * Returns immediately when there is no limit, when [bytes] is not positive or while the bucket still
     * has budget left.
     */
    suspend fun acquire(bytes: Long) {
        if (bytes <= 0) return
        val limit = bytesPerSecond()
        if (limit <= 0) return

        val delayMs = mutex.withLock {
            val now = clock()
            val capacity = limit * 1000
            val last = lastRefillMs
            if (last == null) {
                // First use: start with a full bucket so a short download is not throttled at all.
                availableMilliBytes = capacity
            } else {
                val elapsed = (now - last).coerceAtLeast(0)
                availableMilliBytes = (availableMilliBytes + elapsed * limit).coerceAtMost(capacity)
            }
            lastRefillMs = now
            availableMilliBytes -= bytes * 1000
            if (availableMilliBytes < 0) -availableMilliBytes / limit else 0L
        }

        // The debt is deliberately left in the bucket: the time spent sleeping is what pays it back on
        // the next refill, and concurrent callers queue up behind each other instead of all waiting the
        // same amount.
        if (delayMs > 0) {
            sleep(delayMs)
        }
    }
}
