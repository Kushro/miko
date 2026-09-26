package eu.kanade.tachiyomi.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the download scheduling restrictions (see `DownloadRestrictions.kt`).
 */
class DownloadRestrictionsTest {

    private fun hhmm(hours: Int, minutes: Int = 0) = hours * 60 + minutes

    // region off-peak window

    @Test
    fun `same-day window is start inclusive and end exclusive`() {
        val start = hhmm(8)
        val end = hhmm(12)

        assertTrue(isWithinWindow(hhmm(8), start, end))
        assertTrue(isWithinWindow(hhmm(9), start, end))
        assertTrue(isWithinWindow(hhmm(11, 59), start, end))

        assertFalse(isWithinWindow(hhmm(7, 59), start, end))
        assertFalse(isWithinWindow(hhmm(12), start, end))
        assertFalse(isWithinWindow(hhmm(13), start, end))
    }

    @Test
    fun `window crossing midnight wraps around`() {
        val start = hhmm(22)
        val end = hhmm(6)

        assertTrue(isWithinWindow(hhmm(22), start, end))
        assertTrue(isWithinWindow(hhmm(23), start, end))
        assertTrue(isWithinWindow(hhmm(1), start, end))
        assertTrue(isWithinWindow(hhmm(5, 59), start, end))

        assertFalse(isWithinWindow(hhmm(21, 59), start, end))
        assertFalse(isWithinWindow(hhmm(6), start, end))
        assertFalse(isWithinWindow(hhmm(12), start, end))
    }

    @Test
    fun `equal start and end means the whole day`() {
        assertTrue(isWithinWindow(hhmm(0), hhmm(3), hhmm(3)))
        assertTrue(isWithinWindow(hhmm(3), hhmm(3), hhmm(3)))
        assertTrue(isWithinWindow(hhmm(23, 59), hhmm(3), hhmm(3)))
    }

    // endregion

    // region distance to the window boundaries

    @Test
    fun `minutes until the window opens`() {
        val start = hhmm(10)
        val end = hhmm(11)

        assertEquals(60, minutesUntilWindowStart(hhmm(9), start, end))
        assertEquals(1380, minutesUntilWindowStart(hhmm(11), start, end))
    }

    @Test
    fun `minutes until the window opens is zero while inside it`() {
        assertEquals(0, minutesUntilWindowStart(hhmm(10), hhmm(10), hhmm(11)))
        assertEquals(0, minutesUntilWindowStart(hhmm(1), hhmm(22), hhmm(6)))
        assertEquals(0, minutesUntilWindowStart(hhmm(13), hhmm(3), hhmm(3)))
    }

    @Test
    fun `minutes until the window closes`() {
        assertEquals(60, minutesUntilWindowEnd(hhmm(10), hhmm(10), hhmm(11)))
        // 01:00 inside 22:00-06:00 leaves five hours
        assertEquals(300, minutesUntilWindowEnd(hhmm(1), hhmm(22), hhmm(6)))
        // Outside the window there is nothing to wait for
        assertEquals(0, minutesUntilWindowEnd(hhmm(12), hhmm(22), hhmm(6)))
        // A whole-day window never closes
        assertEquals(MINUTES_PER_DAY, minutesUntilWindowEnd(hhmm(13), hhmm(3), hhmm(3)))
    }

    // endregion

    @Test
    fun `minutes are formatted as HH mm`() {
        assertEquals("00:00", formatMinutes(0))
        assertEquals("08:05", formatMinutes(hhmm(8, 5)))
        assertEquals("23:59", formatMinutes(hhmm(23, 59)))
        assertEquals("00:00", formatMinutes(MINUTES_PER_DAY))
    }

    // region condition check

    private fun conditions(
        isOnline: Boolean = true,
        isWifi: Boolean = true,
        isCharging: Boolean = true,
        isOffPeak: Boolean = true,
    ) = DownloadConditions(
        isOnline = isOnline,
        isWifi = isWifi,
        isCharging = isCharging,
        isOffPeak = isOffPeak,
    )

    @Test
    fun `nothing blocks downloads when every condition is met`() {
        assertNull(
            checkDownloadConditions(
                conditions = conditions(),
                requireWifi = true,
                requireCharging = true,
                requireOffPeak = true,
                force = false,
            ),
        )
    }

    @Test
    fun `being offline blocks downloads even when forced`() {
        assertEquals(
            DownloadBlocker.NO_NETWORK,
            checkDownloadConditions(
                conditions = conditions(isOnline = false),
                requireWifi = false,
                requireCharging = false,
                requireOffPeak = false,
                force = true,
            ),
        )
    }

    @Test
    fun `the wifi restriction is never bypassed by force`() {
        assertEquals(
            DownloadBlocker.NO_WIFI,
            checkDownloadConditions(
                conditions = conditions(isWifi = false),
                requireWifi = true,
                requireCharging = false,
                requireOffPeak = false,
                force = true,
            ),
        )
    }

    @Test
    fun `not charging blocks automatic downloads only`() {
        assertEquals(
            DownloadBlocker.NOT_CHARGING,
            checkDownloadConditions(
                conditions = conditions(isCharging = false),
                requireWifi = false,
                requireCharging = true,
                requireOffPeak = false,
                force = false,
            ),
        )
        assertNull(
            checkDownloadConditions(
                conditions = conditions(isCharging = false),
                requireWifi = false,
                requireCharging = true,
                requireOffPeak = false,
                force = true,
            ),
        )
    }

    @Test
    fun `being outside the window blocks automatic downloads only`() {
        assertEquals(
            DownloadBlocker.OFF_PEAK,
            checkDownloadConditions(
                conditions = conditions(isOffPeak = false),
                requireWifi = false,
                requireCharging = false,
                requireOffPeak = true,
                force = false,
            ),
        )
        assertNull(
            checkDownloadConditions(
                conditions = conditions(isOffPeak = false),
                requireWifi = false,
                requireCharging = false,
                requireOffPeak = true,
                force = true,
            ),
        )
    }

    @Test
    fun `disabled restrictions do not block anything`() {
        assertNull(
            checkDownloadConditions(
                conditions = conditions(isWifi = false, isCharging = false, isOffPeak = false),
                requireWifi = false,
                requireCharging = false,
                requireOffPeak = false,
                force = false,
            ),
        )
    }

    @Test
    fun `charging is reported before the off-peak window`() {
        assertEquals(
            DownloadBlocker.NOT_CHARGING,
            checkDownloadConditions(
                conditions = conditions(isCharging = false, isOffPeak = false),
                requireWifi = false,
                requireCharging = true,
                requireOffPeak = true,
                force = false,
            ),
        )
    }

    // endregion
}
