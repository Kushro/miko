package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.category.model.LibraryFilterSettings
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGrouping
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort

// MIKO -->

/**
 * The global library settings a category follows while it is **not** "special".
 *
 * Built from `LibraryPreferences` (plus the logged-in trackers) by the settings screen model; kept
 * as a value object so every resolution rule below stays pure and unit-testable.
 *
 * Sort is deliberately absent: a non-special category has always read its sort from its own
 * `Category.flags`, and that stays true (see [CategoryLibrarySettingsResolver.sortFor]).
 */
data class GlobalLibrarySettings(
    val filters: LibraryFilterSettings = LibraryFilterSettings.default,
    val display: LibraryDisplayMode = LibraryDisplayMode.default,
    /** `0` = auto, like `LibraryPreferences.portraitColumns()`. */
    val portraitColumns: Int = 0,
    val landscapeColumns: Int = 0,
    val grouping: LibraryGrouping = LibraryGrouping.default,
    val genreGroupMinSize: Int = CategoryLibrarySettings.DEFAULT_GENRE_GROUP_MIN_SIZE,
    val titleSimilarityThreshold: Int = CategoryLibrarySettings.DEFAULT_TITLE_SIMILARITY_THRESHOLD,
)

/** What the library pipeline and the settings dialog must actually use for one category. */
data class EffectiveLibrarySettings(
    val isSpecial: Boolean,
    val filters: LibraryFilterSettings,
    val sort: LibrarySort,
    val display: LibraryDisplayMode,
    val portraitColumns: Int,
    val landscapeColumns: Int,
    val grouping: LibraryGrouping,
    val genreGroupMinSize: Int,
    val titleSimilarityThreshold: Int,
) {

    fun columns(isLandscape: Boolean): Int = if (isLandscape) landscapeColumns else portraitColumns
}

/**
 * Decides, for one category, whether the global library settings or its own
 * [CategoryLibrarySettings] bundle apply ("Special" tab of the library settings dialog).
 *
 * Everything here is pure: the callers ([LibraryScreenModel], [LibrarySettingsScreenModel]) do the
 * preference/database reads and hand the values over.
 */
object CategoryLibrarySettingsResolver {

    /** The bundle of [categoryId], or `null` when that category follows the global settings. */
    fun settingsFor(categoryId: Long?, special: Map<Long, CategoryLibrarySettings>): CategoryLibrarySettings? {
        if (categoryId == null) return null
        return special[categoryId]
    }

    fun isSpecial(categoryId: Long?, special: Map<Long, CategoryLibrarySettings>): Boolean =
        settingsFor(categoryId, special) != null

    /**
     * A category can only be independent when it is a real, selectable one: the system
     * ("Uncategorized") category, the synthetic "ungrouped" page and the no-category case have
     * nothing stable to key a row on.
     */
    fun canBeSpecial(category: Category?, ungrouped: Boolean): Boolean =
        category != null && !category.isSystemCategory && !ungrouped

    /** Filters of [categoryId]: its own bundle, or the global ones. */
    fun filtersFor(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        globalFilters: LibraryFilterSettings,
    ): LibraryFilterSettings = settingsFor(categoryId, special)?.filters ?: globalFilters

    /** Sort of [category]: its own bundle, or — as always — its `Category.flags`. */
    fun sortFor(category: Category?, special: Map<Long, CategoryLibrarySettings>): LibrarySort =
        settingsFor(category?.id, special)?.sortMode ?: category.sort

    fun displayFor(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        globalDisplay: LibraryDisplayMode,
    ): LibraryDisplayMode = settingsFor(categoryId, special)?.displayMode ?: globalDisplay

    fun columnsFor(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        isLandscape: Boolean,
        globalColumns: Int,
    ): Int {
        val settings = settingsFor(categoryId, special) ?: return globalColumns
        return if (isLandscape) settings.landscapeColumns else settings.portraitColumns
    }

    fun groupingFor(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        globalGrouping: LibraryGrouping,
    ): LibraryGrouping = settingsFor(categoryId, special)?.libraryGrouping ?: globalGrouping

    fun genreGroupMinSizeFor(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        globalMinSize: Int,
    ): Int = settingsFor(categoryId, special)?.genreGroupMinSize ?: globalMinSize

    fun titleSimilarityThresholdFor(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        globalThreshold: Int,
    ): Int = settingsFor(categoryId, special)?.titleSimilarityThreshold ?: globalThreshold

    /**
     * The library toolbar's "filters are on" indicator: the global filters, **or** the filters of
     * the category currently shown when that one is special.
     */
    fun hasActiveFilters(
        categoryId: Long?,
        special: Map<Long, CategoryLibrarySettings>,
        globalActive: Boolean,
    ): Boolean = globalActive || settingsFor(categoryId, special)?.filters?.isActive == true

    /** Everything at once, for callers that need more than one value. */
    fun resolve(
        category: Category?,
        special: Map<Long, CategoryLibrarySettings>,
        global: GlobalLibrarySettings,
    ): EffectiveLibrarySettings {
        val settings = settingsFor(category?.id, special)
        return EffectiveLibrarySettings(
            isSpecial = settings != null,
            filters = settings?.filters ?: global.filters,
            sort = settings?.sortMode ?: category.sort,
            display = settings?.displayMode ?: global.display,
            portraitColumns = settings?.portraitColumns ?: global.portraitColumns,
            landscapeColumns = settings?.landscapeColumns ?: global.landscapeColumns,
            grouping = settings?.libraryGrouping ?: global.grouping,
            genreGroupMinSize = settings?.genreGroupMinSize ?: global.genreGroupMinSize,
            titleSimilarityThreshold = settings?.titleSimilarityThreshold ?: global.titleSimilarityThreshold,
        )
    }

    /**
     * The bundle written when a category is turned "special" (and when the user asks to copy the
     * global settings again): a snapshot of what that category shows right now, so switching the
     * toggle on changes nothing visible.
     */
    fun snapshot(category: Category?, global: GlobalLibrarySettings): CategoryLibrarySettings =
        CategoryLibrarySettings(
            filters = global.filters,
            portraitColumns = global.portraitColumns,
            landscapeColumns = global.landscapeColumns,
            genreGroupMinSize = global.genreGroupMinSize,
            titleSimilarityThreshold = global.titleSimilarityThreshold,
        )
            .withSort(category.sort)
            .withDisplay(global.display)
            .withGrouping(global.grouping)
}
// MIKO <--
