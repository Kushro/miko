package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.preference.minusAssign

class DeleteSourceCategory(private val preferences: SourcePreferences) {

    fun await(category: String) {
        // MIKO -->
        // Never leave the active preset pointing at a category that no longer exists.
        val activePreset = preferences.activeSourcePreset()
        if (activePreset.get() == category) {
            activePreset.set("")
        }
        // Same for the preset picked as a global-search filter: fall back to "All".
        val searchFilter = preferences.globalSearchPinnedState()
        if ((searchFilter.get() as? SourceFilter.Preset)?.name == category) {
            searchFilter.set(SourceFilter.All)
        }
        // MIKO <--
        preferences.sourcesTabSourcesInCategories().getAndSet { sourcesInCategories ->
            sourcesInCategories.filterNot { it.substringAfter("|") == category }.toSet()
        }
        preferences.sourcesTabCategories() -= category
    }
}
