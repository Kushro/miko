package tachiyomi.domain.history.service

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.history.model.HistorySort
import tachiyomi.domain.updates.service.USE_PANORAMA_COVER_PREF

class HistoryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun filterUnfinishedManga() = preferenceStore.getEnum(
        "pref_filter_history_unfinished_manga",
        TriState.DISABLED,
    )

    fun filterUnfinishedChapter() = preferenceStore.getEnum(
        "pref_filter_history_unfinished_chapter",
        TriState.DISABLED,
    )

    fun filterNonLibraryManga() = preferenceStore.getEnum(
        "pref_filter_history_non_library_manga",
        TriState.DISABLED,
    )

    fun usePanoramaCover() = preferenceStore.getBoolean(
        USE_PANORAMA_COVER_PREF,
        false,
    )

    // MIKO -->
    /**
     * When enabled, History only lists entries of the category the library is currently showing
     * (see `LibraryPreferences.activeCategoryId`).
     */
    fun scopeToLibraryCategory() = preferenceStore.getBoolean(
        "pref_history_scope_library_category",
        false,
    )

    /** Order of the History list. [HistorySort.LAST_READ] is the upstream behaviour. */
    fun historySort() = preferenceStore.getEnum(
        "pref_history_sort",
        HistorySort.LAST_READ,
    )

    /** Direction of [historySort]; defaults to `true` so the most recent entry comes first. */
    fun historySortDescending() = preferenceStore.getBoolean(
        "pref_history_sort_descending",
        true,
    )

    /**
     * When enabled, History hides the entries whose last read chapter is already finished (it is
     * applied as `unfinishedChapter = true` on the query, see `HistoryScreenModel`).
     */
    fun historyHideRead() = preferenceStore.getBoolean(
        "pref_history_hide_read",
        false,
    )

    /**
     * Source ids (as strings) hidden from the History list by the toolbar eye (C20). Filtering is
     * applied client-side when displaying, so hiding a source also hides its future entries
     * retroactively. Independent from Browse's `hidden_catalogues` and from the Moments set.
     */
    fun hiddenSources() = preferenceStore.getStringSet(
        "pref_history_hidden_sources",
        emptySet(),
    )
    // MIKO <--
}
