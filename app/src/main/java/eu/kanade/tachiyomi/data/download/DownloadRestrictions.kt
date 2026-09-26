package eu.kanade.tachiyomi.data.download

import java.time.LocalTime

/**
 * MIKO — pure helpers backing the download scheduling restrictions (only-while-charging and the
 * off-peak window).
 *
 * Everything that can be decided without touching Android lives here so [DownloadJob] stays small
 * and the behaviour can be unit tested on the JVM (see `DownloadRestrictionsTest`).
 *
 * Window semantics (ported from Futon): minutes since local midnight, start **inclusive**, end
 * **exclusive**, the window crosses midnight when `end < start`, and `start == end` means the whole
 * day.
 *
 * This constant is the length of a day in minutes, used to wrap every window computation.
 */
const val MINUTES_PER_DAY: Int = 24 * 60

/** Reasons why the downloader is not allowed to run right now. `null` means "go ahead". */
enum class DownloadBlocker {
    NO_NETWORK,
    NO_WIFI,
    NOT_CHARGING,
    OFF_PEAK,
}

/** Snapshot of the device state the restrictions are evaluated against. */
data class DownloadConditions(
    val isOnline: Boolean,
    val isWifi: Boolean,
    val isCharging: Boolean,
    val isOffPeak: Boolean,
)

/**
 * Returns the blocker preventing downloads from running, or `null` when they may run.
 *
 * [force] is set by manual starts (the queue screen FAB, "Start now", the notification action) and
 * bypasses the charging and off-peak restrictions **only**: the Wi-Fi/network requirement behaves
 * exactly like in Mihon and is never bypassed.
 */
fun checkDownloadConditions(
    conditions: DownloadConditions,
    requireWifi: Boolean,
    requireCharging: Boolean,
    requireOffPeak: Boolean,
    force: Boolean,
): DownloadBlocker? {
    if (!conditions.isOnline) return DownloadBlocker.NO_NETWORK
    if (requireWifi && !conditions.isWifi) return DownloadBlocker.NO_WIFI
    if (force) return null
    if (requireCharging && !conditions.isCharging) return DownloadBlocker.NOT_CHARGING
    if (requireOffPeak && !conditions.isOffPeak) return DownloadBlocker.OFF_PEAK
    return null
}

/** Whether [nowMinutes] falls inside the `[startMinutes, endMinutes)` window. */
fun isWithinWindow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
    val now = wrapMinutes(nowMinutes)
    val start = wrapMinutes(startMinutes)
    val end = wrapMinutes(endMinutes)
    return when {
        start == end -> true
        start < end -> now >= start && now < end
        else -> now >= start || now < end
    }
}

/**
 * Minutes left until the window opens again, or `0` when [nowMinutes] is already inside it.
 * Standing exactly on the start is "inside", so this never returns a full day.
 */
fun minutesUntilWindowStart(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Int {
    if (isWithinWindow(nowMinutes, startMinutes, endMinutes)) return 0
    val diff = wrapMinutes(startMinutes) - wrapMinutes(nowMinutes)
    return if (diff > 0) diff else diff + MINUTES_PER_DAY
}

/**
 * Minutes left until the window closes, or `0` when [nowMinutes] is outside it. A whole-day window
 * (`start == end`) never closes, so it reports a full day.
 */
fun minutesUntilWindowEnd(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Int {
    if (!isWithinWindow(nowMinutes, startMinutes, endMinutes)) return 0
    if (wrapMinutes(startMinutes) == wrapMinutes(endMinutes)) return MINUTES_PER_DAY
    val diff = wrapMinutes(endMinutes) - wrapMinutes(nowMinutes)
    return if (diff > 0) diff else diff + MINUTES_PER_DAY
}

/** Formats minutes since midnight as `HH:mm`, for notification texts and settings subtitles. */
fun formatMinutes(minutes: Int): String {
    val wrapped = wrapMinutes(minutes)
    return "%02d:%02d".format(wrapped / 60, wrapped % 60)
}

/** Local wall-clock time as minutes since midnight. */
fun nowMinutesOfDay(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

private fun wrapMinutes(minutes: Int): Int {
    val wrapped = minutes % MINUTES_PER_DAY
    return if (wrapped < 0) wrapped + MINUTES_PER_DAY else wrapped
}
