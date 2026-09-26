// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu.mapping

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Demographic
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.YEAR_UNKNOWN
import java.util.Locale

/*
 * Builds Mihon's FilterList out of the parser's capabilities/options and reads it back into a typed
 * MangaListFilter.
 *
 * Unlike the Futon bridge (which flattens every widget into synthetic tags because it has no typed
 * model on the other side), here the Kotatsu side *is* typed: every widget below carries the model
 * object it stands for, so reading the selection back needs no name matching.
 *
 * Widget titles are hardcoded English on purpose — the module has no per-source translations and
 * these strings are the same ones the extension ecosystem uses.
 */

internal const val NAME_ORDER = "Order"
internal const val NAME_GENRES = "Genres"
internal const val NAME_GENRE = "Genre"
internal const val NAME_STATUS = "Status"
internal const val NAME_CONTENT_RATING = "Content rating"
internal const val NAME_TYPE = "Type"
internal const val NAME_DEMOGRAPHIC = "Demographic"
internal const val NAME_LANGUAGE = "Language"
internal const val NAME_ORIGINAL_LANGUAGE = "Original language"
internal const val NAME_YEAR = "Year"
internal const val NAME_YEAR_FROM = "Year from"
internal const val NAME_YEAR_TO = "Year to"
internal const val NAME_AUTHOR = "Author"

private const val ANY = "Any"
private const val LOADING_HINT = "Loading filters… reopen this dialog"

// region Filter widgets

/** Order of the listing. Kotatsu encodes the direction in the enum, so the ascending flag is unused. */
class SortFilter(values: Array<String>, defaultIndex: Int) :
    Filter.Sort(NAME_ORDER, values, Filter.Sort.Selection(defaultIndex, false))

class TagTriState(val tag: MangaTag) : Filter.TriState(tag.title)

class TagsGroup(tags: List<TagTriState>) : Filter.Group<TagTriState>(NAME_GENRES, tags)

/** Used instead of [TagsGroup] when the parser only accepts a single tag. */
class TagSelect(values: Array<String>, val tags: List<MangaTag?>) : Filter.Select<String>(NAME_GENRE, values)

class StateCheckBox(val value: MangaState) : Filter.CheckBox(enumTitle(value.name))

class StatesGroup(items: List<StateCheckBox>) : Filter.Group<StateCheckBox>(NAME_STATUS, items)

class ContentRatingCheckBox(val value: ContentRating) : Filter.CheckBox(enumTitle(value.name))

class ContentRatingGroup(items: List<ContentRatingCheckBox>) :
    Filter.Group<ContentRatingCheckBox>(NAME_CONTENT_RATING, items)

class TypeCheckBox(val value: ContentType) : Filter.CheckBox(enumTitle(value.name))

class TypesGroup(items: List<TypeCheckBox>) : Filter.Group<TypeCheckBox>(NAME_TYPE, items)

class DemographicCheckBox(val value: Demographic) : Filter.CheckBox(enumTitle(value.name))

class DemographicsGroup(items: List<DemographicCheckBox>) :
    Filter.Group<DemographicCheckBox>(NAME_DEMOGRAPHIC, items)

/**
 * Reused for both locale fields of `MangaListFilter`; [name] tells them apart because Mihon's
 * filter dialog identifies widgets by their visible title anyway.
 */
class LocaleSelect(name: String, values: Array<String>, val locales: List<Locale?>) :
    Filter.Select<String>(name, values)

class YearText(name: String) : Filter.Text(name)

class AuthorText : Filter.Text(NAME_AUTHOR)

// endregion

/**
 * @param options `null` while `MangaParser.getFilterOptions()` (a network call) has not completed.
 * In that case only the sort widget is offered, plus a header telling the user to reopen the dialog.
 */
fun buildFilterList(parser: MangaParser, options: MangaListFilterOptions?): FilterList {
    val sortOrders = parser.availableSortOrders.toList()
    val sortFilter = SortFilter(
        values = sortOrders.map { enumTitle(it.name) }.toTypedArray(),
        defaultIndex = sortOrders.indexOf(SortOrder.UPDATED).coerceAtLeast(0),
    )
    if (options == null) {
        return FilterList(Filter.Header(LOADING_HINT), sortFilter)
    }

    val capabilities = parser.filterCapabilities
    val filters = mutableListOf<Filter<*>>(sortFilter)

    if (options.availableTags.isNotEmpty()) {
        val tags = options.availableTags.sortedBy { it.title }
        filters += if (capabilities.isMultipleTagsSupported) {
            TagsGroup(tags.map { TagTriState(it) })
        } else {
            TagSelect(
                values = (listOf(ANY) + tags.map { it.title }).toTypedArray(),
                tags = listOf<MangaTag?>(null) + tags,
            )
        }
    }
    if (options.availableStates.isNotEmpty()) {
        filters += StatesGroup(options.availableStates.map(::StateCheckBox))
    }
    if (options.availableContentRating.isNotEmpty()) {
        filters += ContentRatingGroup(options.availableContentRating.map(::ContentRatingCheckBox))
    }
    if (options.availableContentTypes.isNotEmpty()) {
        filters += TypesGroup(options.availableContentTypes.map(::TypeCheckBox))
    }
    if (options.availableDemographics.isNotEmpty()) {
        filters += DemographicsGroup(options.availableDemographics.map(::DemographicCheckBox))
    }
    if (options.availableLocales.isNotEmpty()) {
        val locales = options.availableLocales.sortedBy { it.displayName }
        val values = (listOf(ANY) + locales.map { it.displayName }).toTypedArray()
        val references = listOf<Locale?>(null) + locales
        filters += LocaleSelect(NAME_LANGUAGE, values, references)
        if (capabilities.isOriginalLocaleSupported) {
            filters += LocaleSelect(NAME_ORIGINAL_LANGUAGE, values, references)
        }
    }
    if (capabilities.isYearSupported) {
        filters += YearText(NAME_YEAR)
    }
    if (capabilities.isYearRangeSupported) {
        filters += YearText(NAME_YEAR_FROM)
        filters += YearText(NAME_YEAR_TO)
    }
    if (capabilities.isAuthorSearchSupported) {
        filters += AuthorText()
    }
    return FilterList(filters)
}

/**
 * Reads [filters] back into the pair the parsers library takes.
 *
 * Anything the parser declares unsupported is dropped rather than sent, because parsers raise
 * `IllegalArgumentException` (see `MangaParserEnv#oneOrThrowIfMany`) instead of ignoring it.
 *
 * @param options kept for symmetry with [buildFilterList]; the widgets already carry their model
 * objects, so no lookup is needed.
 */
@Suppress("UNUSED_PARAMETER")
fun toMangaListFilter(
    parser: MangaParser,
    query: String,
    filters: FilterList,
    options: MangaListFilterOptions?,
): Pair<SortOrder, MangaListFilter> {
    val sortOrders = parser.availableSortOrders.toList()
    val capabilities = parser.filterCapabilities
    val order = filters.filterIsInstance<SortFilter>().firstOrNull()
        ?.state?.index
        ?.let { sortOrders.getOrNull(it) }
        ?: defaultSortOrder(sortOrders, query)

    val searchQuery = query.trim().takeIf { it.isNotEmpty() && capabilities.isSearchSupported }
    if (searchQuery != null && !capabilities.isSearchWithFiltersSupported) {
        // The parser cannot combine both; the query is what the user typed last, so it wins.
        return order to MangaListFilter(query = searchQuery)
    }

    var tags = emptySet<MangaTag>()
    var tagsExclude = emptySet<MangaTag>()
    var states = emptySet<MangaState>()
    var contentRating = emptySet<ContentRating>()
    var types = emptySet<ContentType>()
    var demographics = emptySet<Demographic>()
    var locale: Locale? = null
    var originalLocale: Locale? = null
    var year = YEAR_UNKNOWN
    var yearFrom = YEAR_UNKNOWN
    var yearTo = YEAR_UNKNOWN
    var author: String? = null

    for (filter in filters) {
        when (filter) {
            is TagsGroup -> {
                tags = filter.state.filter { it.isIncluded() }.map { it.tag }.toSet()
                tagsExclude = filter.state.filter { it.isExcluded() }.map { it.tag }.toSet()
            }
            is TagSelect -> tags = setOfNotNull(filter.tags.getOrNull(filter.state))
            is StatesGroup -> states = filter.state.filter { it.state }.map { it.value }.toSet()
            is ContentRatingGroup -> contentRating = filter.state.filter { it.state }.map { it.value }.toSet()
            is TypesGroup -> types = filter.state.filter { it.state }.map { it.value }.toSet()
            is DemographicsGroup -> demographics = filter.state.filter { it.state }.map { it.value }.toSet()
            is LocaleSelect -> {
                val selected = filter.locales.getOrNull(filter.state)
                if (filter.name == NAME_ORIGINAL_LANGUAGE) {
                    originalLocale = selected
                } else {
                    locale = selected
                }
            }
            is YearText -> filter.state.trim().toIntOrNull()?.let { value ->
                when (filter.name) {
                    NAME_YEAR_FROM -> yearFrom = value
                    NAME_YEAR_TO -> yearTo = value
                    else -> year = value
                }
            }
            is AuthorText -> author = filter.state.trim().takeIf { it.isNotEmpty() }
            else -> Unit
        }
    }

    if (!capabilities.isMultipleTagsSupported && tags.size > 1) {
        tags = setOf(tags.first())
    }
    if (!capabilities.isTagsExclusionSupported) {
        tagsExclude = emptySet()
    }
    if (!capabilities.isOriginalLocaleSupported) {
        originalLocale = null
    }
    if (!capabilities.isYearSupported) {
        year = YEAR_UNKNOWN
    }
    if (!capabilities.isYearRangeSupported) {
        yearFrom = YEAR_UNKNOWN
        yearTo = YEAR_UNKNOWN
    }
    if (!capabilities.isAuthorSearchSupported) {
        author = null
    }

    return order to MangaListFilter(
        query = searchQuery,
        tags = tags,
        tagsExclude = tagsExclude,
        locale = locale,
        originalLocale = originalLocale,
        states = states,
        contentRating = contentRating,
        types = types,
        demographics = demographics,
        year = year,
        yearFrom = yearFrom,
        yearTo = yearTo,
        author = author,
    )
}

/** `POPULARITY` when available, otherwise whatever the parser lists first. */
fun popularSortOrder(parser: MangaParser): SortOrder {
    val orders = parser.availableSortOrders
    return if (SortOrder.POPULARITY in orders) {
        SortOrder.POPULARITY
    } else {
        orders.firstOrNull() ?: SortOrder.UPDATED
    }
}

/** `UPDATED` when available, otherwise whatever the parser lists first. */
fun latestSortOrder(parser: MangaParser): SortOrder {
    val orders = parser.availableSortOrders
    return if (SortOrder.UPDATED in orders) {
        SortOrder.UPDATED
    } else {
        orders.firstOrNull() ?: SortOrder.UPDATED
    }
}

private fun defaultSortOrder(sortOrders: List<SortOrder>, query: String): SortOrder = when {
    query.isNotBlank() && SortOrder.RELEVANCE in sortOrders -> SortOrder.RELEVANCE
    else -> sortOrders.firstOrNull() ?: SortOrder.UPDATED
}

/** `POPULARITY_TODAY` reads as `Popularity today`. */
private fun enumTitle(name: String): String = name
    .lowercase(Locale.ROOT)
    .replace('_', ' ')
    .replaceFirstChar { it.uppercase(Locale.ROOT) }
