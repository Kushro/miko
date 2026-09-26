package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(REMOVE_EXCLUDE_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapterCategoriesExclude() =
        preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    fun parallelSourceLimit() = preferenceStore.getInt("download_parallel_source_limit", 5)

    fun parallelPageLimit() = preferenceStore.getInt("download_parallel_page_limit", 5)

    // SY -->
    fun includeChapterUrlHash() = preferenceStore.getBoolean("download_include_chapter_url_hash", false)
    // SY <--

    // KMK -->
    fun downloadCacheRenewInterval() = preferenceStore.getInt("download_cache_renew_interval", 1)
    // KMK <--

    // MIKO -->
    /** Only run automatic downloads while the device is charging (manual "Start" bypasses it). */
    fun downloadOnlyWhenCharging() = preferenceStore.getBoolean("download_only_when_charging", false)

    /** Restrict automatic downloads to the off-peak window below (manual "Start" bypasses it). */
    fun downloadOffPeakEnabled() = preferenceStore.getBoolean("download_off_peak_enabled", false)

    /** Off-peak window start, in minutes since local midnight (0..1439). Start is inclusive. */
    fun downloadOffPeakStartMinutes() = preferenceStore.getInt("download_off_peak_start_minutes", 0)

    /**
     * Off-peak window end, in minutes since local midnight (0..1439). End is exclusive; an end lower
     * than the start means the window crosses midnight, and start == end means the whole day.
     */
    fun downloadOffPeakEndMinutes() = preferenceStore.getInt("download_off_peak_end_minutes", 6 * 60)

    /** Global download speed limit in KiB/s shared by every concurrent download; 0 = unlimited. */
    fun downloadSpeedLimitKbps() = preferenceStore.getInt("download_speed_limit_kbps", 0)

    /** Storage quota for downloaded chapters in MiB; 0 = no quota. Oldest chapters are purged past it. */
    fun downloadStorageQuotaMb() = preferenceStore.getInt("download_storage_quota_mb", 0)
    // MIKO <--

    companion object {
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
