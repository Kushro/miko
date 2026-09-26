package eu.kanade.domain.recommendation.providers

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

// MIKO -->

/**
 * Pure matching/scoring logic behind [TaguOsekkaiProvider], split out so it is unit-testable
 * against a synthetic [FilterList] without a real [eu.kanade.tachiyomi.source.Source].
 *
 * A seed tag "matches" a source's filters when, after [normalize], it equals the name of a
 * `Filter.CheckBox`/`Filter.TriState` child of a `Filter.Group<*>`, or one of a `Filter.Select<*>`'s
 * `values[i].toString()` — generic over name alone, so it covers both Mihon extension filter lists
 * and Kotatsu's typed `TagsGroup`/`TagSelect` (`KotatsuFilterBuilder.kt`) without depending on
 * either module's concrete widget classes.
 */
internal object TagFilterMatcher {

    /** Lowercase, letters/digits only, so punctuation/casing differences don't break a match. */
    fun normalize(tag: String): String = tag.lowercase().filter { it.isLetterOrDigit() }

    /** Whether [filters] has an entry matching [normalizedTag] (already [normalize]d). */
    fun matches(filters: FilterList, normalizedTag: String): Boolean = find(filters, normalizedTag) != null

    /**
     * Selects the entry of [filters] matching [normalizedTag] in place (checkbox → checked,
     * tri-state → include, select → that index) and returns whether one was found. No-op otherwise.
     */
    fun mark(filters: FilterList, normalizedTag: String): Boolean {
        val select = find(filters, normalizedTag) ?: return false
        select()
        return true
    }

    /**
     * The share of [seedTags] (already normalized) present in [genre] (a comma-separated
     * `SManga.genre`), squared. `0.0` when [genre] is null/blank/has no overlap, so results with no
     * genre info keep the source's original order under a stable sort.
     */
    fun score(genre: String?, seedTags: Set<String>): Double {
        if (seedTags.isEmpty()) return 0.0
        val resultTags = genre.orEmpty().split(",")
            .mapNotNullTo(mutableSetOf()) { normalize(it).takeIf(String::isNotEmpty) }
        if (resultTags.isEmpty()) return 0.0
        val fraction = resultTags.count { it in seedTags }.toDouble() / seedTags.size
        return fraction * fraction
    }

    /** Finds the matching entry and returns a closure that selects it, or null. */
    private fun find(filters: FilterList, normalizedTag: String): (() -> Unit)? {
        for (filter in filters) {
            when (filter) {
                is Filter.Group<*> -> {
                    val child = filter.state.firstOrNull {
                        (it is Filter.CheckBox && normalize(it.name) == normalizedTag) ||
                            (it is Filter.TriState && normalize(it.name) == normalizedTag)
                    }
                    when (child) {
                        is Filter.CheckBox -> return { child.state = true }
                        is Filter.TriState -> return { child.state = Filter.TriState.STATE_INCLUDE }
                        else -> Unit
                    }
                }
                is Filter.Select<*> -> {
                    val index = filter.values.indexOfFirst { normalize(it.toString()) == normalizedTag }
                    if (index >= 0) return { filter.state = index }
                }
                else -> Unit
            }
        }
        return null
    }
}

// MIKO <--
