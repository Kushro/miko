package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.category.model.LibraryFilterSettings
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroupLayer
import tachiyomi.domain.library.model.LibraryGroupType
import tachiyomi.domain.library.model.LibraryGrouping
import tachiyomi.domain.library.model.LibrarySort

/**
 * MIKO — unit tests of the "Special" category resolution (see `CategoryLibrarySettingsResolver.kt`).
 */
class CategoryLibrarySettingsResolverTest {

    private val categorySort = LibrarySort(LibrarySort.Type.LastRead, LibrarySort.Direction.Descending)
    private val bundleSort = LibrarySort(LibrarySort.Type.DateAdded, LibrarySort.Direction.Ascending)

    private fun category(id: Long = 3L, sort: LibrarySort = categorySort) = Category(
        id = id,
        name = "Category $id",
        order = 0,
        flags = sort.flag,
        hidden = false,
    )

    private val global = GlobalLibrarySettings(
        filters = LibraryFilterSettings(unread = TriState.ENABLED_IS),
        display = LibraryDisplayMode.List,
        portraitColumns = 2,
        landscapeColumns = 5,
        grouping = LibraryGrouping(listOf(LibraryGroupLayer(LibraryGroupType.SOURCE))),
        genreGroupMinSize = 4,
        titleSimilarityThreshold = 70,
    )

    private val bundle = CategoryLibrarySettings(
        filters = LibraryFilterSettings(downloaded = TriState.ENABLED_NOT),
        portraitColumns = 7,
        landscapeColumns = 9,
        genreGroupMinSize = 1,
        titleSimilarityThreshold = 85,
    )
        .withSort(bundleSort)
        .withDisplay(LibraryDisplayMode.CoverOnlyGrid)
        .withGrouping(LibraryGrouping(listOf(LibraryGroupLayer(LibraryGroupType.GENRE))))

    private val special = mapOf(3L to bundle)

    @Test
    fun `a category without a row is not special`() {
        CategoryLibrarySettingsResolver.isSpecial(9L, special) shouldBe false
        CategoryLibrarySettingsResolver.isSpecial(null, special) shouldBe false
        CategoryLibrarySettingsResolver.isSpecial(3L, special) shouldBe true
        CategoryLibrarySettingsResolver.settingsFor(null, special) shouldBe null
        CategoryLibrarySettingsResolver.settingsFor(3L, special) shouldBe bundle
    }

    @Test
    fun `only a real category can be made special`() {
        CategoryLibrarySettingsResolver.canBeSpecial(category(), ungrouped = false) shouldBe true
        // The system ("Uncategorized") category, the ungrouped pseudo-category and "no category".
        CategoryLibrarySettingsResolver.canBeSpecial(category(id = 0L), ungrouped = false) shouldBe false
        CategoryLibrarySettingsResolver.canBeSpecial(category(), ungrouped = true) shouldBe false
        CategoryLibrarySettingsResolver.canBeSpecial(null, ungrouped = false) shouldBe false
    }

    @Test
    fun `a non special category keeps every global value and its own flags sort`() {
        CategoryLibrarySettingsResolver.filtersFor(9L, special, global.filters) shouldBe global.filters
        CategoryLibrarySettingsResolver.sortFor(category(id = 9L), special) shouldBe categorySort
        CategoryLibrarySettingsResolver.displayFor(9L, special, global.display) shouldBe LibraryDisplayMode.List
        CategoryLibrarySettingsResolver.columnsFor(9L, special, isLandscape = false, globalColumns = 2) shouldBe 2
        CategoryLibrarySettingsResolver.columnsFor(9L, special, isLandscape = true, globalColumns = 5) shouldBe 5
        CategoryLibrarySettingsResolver.groupingFor(9L, special, global.grouping) shouldBe global.grouping
        CategoryLibrarySettingsResolver.genreGroupMinSizeFor(9L, special, 4) shouldBe 4
        CategoryLibrarySettingsResolver.titleSimilarityThresholdFor(9L, special, 70) shouldBe 70
    }

    @Test
    fun `a special category reads everything from its bundle`() {
        CategoryLibrarySettingsResolver.filtersFor(3L, special, global.filters) shouldBe bundle.filters
        CategoryLibrarySettingsResolver.sortFor(category(), special) shouldBe bundleSort
        CategoryLibrarySettingsResolver.displayFor(3L, special, global.display) shouldBe
            LibraryDisplayMode.CoverOnlyGrid
        CategoryLibrarySettingsResolver.columnsFor(3L, special, isLandscape = false, globalColumns = 2) shouldBe 7
        CategoryLibrarySettingsResolver.columnsFor(3L, special, isLandscape = true, globalColumns = 5) shouldBe 9
        CategoryLibrarySettingsResolver.groupingFor(3L, special, global.grouping).layers.single().type shouldBe
            LibraryGroupType.GENRE
        CategoryLibrarySettingsResolver.genreGroupMinSizeFor(3L, special, 4) shouldBe 1
        CategoryLibrarySettingsResolver.titleSimilarityThresholdFor(3L, special, 70) shouldBe 85
    }

    @Test
    fun `active filters are the global ones or the shown category's own ones`() {
        // Nothing global, nothing in the bundle.
        CategoryLibrarySettingsResolver.hasActiveFilters(3L, mapOf(3L to CategoryLibrarySettings.default), false) shouldBe
            false
        // Nothing global, but the shown special category filters.
        CategoryLibrarySettingsResolver.hasActiveFilters(3L, special, false) shouldBe true
        // A special category never hides the fact that the global filters are on elsewhere.
        CategoryLibrarySettingsResolver.hasActiveFilters(3L, mapOf(3L to CategoryLibrarySettings.default), true) shouldBe
            true
        CategoryLibrarySettingsResolver.hasActiveFilters(null, special, false) shouldBe false
    }

    @Test
    fun `resolve mixes the bundle and the globals per category`() {
        val effective = CategoryLibrarySettingsResolver.resolve(category(), special, global)
        effective.isSpecial shouldBe true
        effective.filters shouldBe bundle.filters
        effective.sort shouldBe bundleSort
        effective.display shouldBe LibraryDisplayMode.CoverOnlyGrid
        effective.columns(isLandscape = true) shouldBe 9
        effective.columns(isLandscape = false) shouldBe 7
        effective.genreGroupMinSize shouldBe 1
        effective.titleSimilarityThreshold shouldBe 85

        val plain = CategoryLibrarySettingsResolver.resolve(category(id = 9L), special, global)
        plain.isSpecial shouldBe false
        plain.filters shouldBe global.filters
        plain.sort shouldBe categorySort
        plain.display shouldBe LibraryDisplayMode.List
        plain.columns(isLandscape = true) shouldBe 5
        plain.columns(isLandscape = false) shouldBe 2
        plain.grouping shouldBe global.grouping
        plain.genreGroupMinSize shouldBe 4
        plain.titleSimilarityThreshold shouldBe 70
    }

    @Test
    fun `the snapshot copies the globals plus the category's own flags sort`() {
        val snapshot = CategoryLibrarySettingsResolver.snapshot(category(), global)
        snapshot.filters shouldBe global.filters
        snapshot.sortMode shouldBe categorySort
        snapshot.displayMode shouldBe global.display
        snapshot.portraitColumns shouldBe 2
        snapshot.landscapeColumns shouldBe 5
        snapshot.libraryGrouping shouldBe global.grouping
        snapshot.genreGroupMinSize shouldBe 4
        snapshot.titleSimilarityThreshold shouldBe 70
    }

    @Test
    fun `a freshly snapshotted category resolves exactly like it did before`() {
        val plain = CategoryLibrarySettingsResolver.resolve(category(), emptyMap(), global)
        val snapshot = CategoryLibrarySettingsResolver.snapshot(category(), global)
        val afterToggle = CategoryLibrarySettingsResolver.resolve(category(), mapOf(3L to snapshot), global)

        afterToggle shouldBe plain.copy(isSpecial = true)
    }

    @Test
    fun `defaults survive a json round trip through the snapshot`() {
        val snapshot = CategoryLibrarySettingsResolver.snapshot(category(), global)
        CategoryLibrarySettings.fromJson(snapshot.toJson()) shouldBe snapshot
    }
}
