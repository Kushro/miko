package tachiyomi.domain.favorites.service

import tachiyomi.core.common.preference.PreferenceStore

/**
 * MIKO — preferences of the Moments screen (the tab holding page bookmarks and chapter bookmarks,
 * internally still "favorites"). Created in C20 for the toolbar eye; both tabs read the same set.
 */
class FavoritesPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * Source ids (as strings) hidden from both Moments tabs by the toolbar eye (C20). One set
     * covers Pages and Chapters; filtering is applied client-side when displaying, so hiding a
     * source also hides its future bookmarks retroactively. Independent from Browse's
     * `hidden_catalogues` and from the History set.
     */
    fun hiddenSources() = preferenceStore.getStringSet(
        "pref_favorites_hidden_sources",
        emptySet(),
    )
}
