package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.tachiyomi.source.Source

class GlobalSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : SearchScreenModel(State(searchQuery = initialQuery)) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }

        // KMK -->
        shouldPinnedSourcesHidden()
        // KMK <--
    }

    override fun getEnabledSources(): List<Source> {
        // MIKO -->
        val filter = state.value.sourceFilter
        return super.getEnabledSources().filter { source ->
            when (filter) {
                SourceFilter.PinnedOnly -> "${source.id}" in pinnedSources
                SourceFilter.All -> true
                // Already applied by the base implementation, which swaps the active preset for it.
                is SourceFilter.Preset -> true
            }
        }
        // MIKO <--
    }
}
