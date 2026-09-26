package eu.kanade.tachiyomi.ui.browse.source

import tachiyomi.domain.source.model.Source

// MIKO -->
/**
 * Applies the active source preset to the flattened source list produced by
 * `GetEnabledSources.subscribe()`.
 *
 * A preset is a source category, and `GetEnabledSources` already emits one extra copy of every
 * source per category it belongs to (with [Source.category] set to that category name). The preset
 * filter is **exclusive**: when a preset is active only those category copies survive, so Browse
 * shows a single group and neither the language groups, the pinned group nor the "last used" entry
 * leak in. An empty [activePreset] means "no preset", and the list is returned untouched.
 */
fun filterByActivePreset(sources: List<Source>, activePreset: String): List<Source> {
    if (activePreset.isEmpty()) return sources
    return sources.filter { it.category == activePreset }
}

/**
 * Ids of the sources that belong to [preset], read from the raw `sources_tab_source_categories`
 * [membership] set (`SourcePreferences.sourcesTabSourcesInCategories()`).
 *
 * Every entry has the shape `"<sourceId>|<categoryName>"`. The separator is the **first** `'|'`:
 * a source id never contains one, so a preset name is free to contain them (`"12|a|b"` belongs to
 * the preset `"a|b"`). This matches how `RenameSourceCategory` and `DeleteSourceCategory` split the
 * very same entries, so the three stay consistent.
 *
 * Malformed entries — no separator at all, or a non-numeric id — are ignored instead of throwing.
 * An empty [preset] matches nothing (an empty preset name cannot be created).
 */
fun sourceIdsInPreset(membership: Set<String>, preset: String): Set<Long> {
    if (preset.isEmpty()) return emptySet()
    return membership.mapNotNullTo(mutableSetOf()) { entry ->
        val separator = entry.indexOf('|')
        when {
            separator == -1 -> null
            entry.substring(separator + 1) != preset -> null
            else -> entry.substring(0, separator).toLongOrNull()
        }
    }
}
// MIKO <--
