package tachiyomi.domain.category.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroupLayer
import tachiyomi.domain.library.model.LibraryGroupSort
import tachiyomi.domain.library.model.LibraryGroupType
import tachiyomi.domain.library.model.LibraryGrouping
import tachiyomi.domain.library.model.LibrarySort

@Execution(ExecutionMode.CONCURRENT)
class CategoryLibrarySettingsTest {

    @Test
    fun `defaults mirror the global defaults`() {
        val settings = CategoryLibrarySettings.default
        settings.filters shouldBe LibraryFilterSettings.default
        settings.filters.isActive shouldBe false
        settings.sortMode shouldBe LibrarySort.default
        settings.displayMode shouldBe LibraryDisplayMode.default
        settings.libraryGrouping shouldBe LibraryGrouping.default
        settings.genreGroupMinSize shouldBe CategoryLibrarySettings.DEFAULT_GENRE_GROUP_MIN_SIZE
        // MIKO -->
        settings.titleSimilarityThreshold shouldBe 60
        settings.titleSimilarityThreshold shouldBe CategoryLibrarySettings.DEFAULT_TITLE_SIMILARITY_THRESHOLD
        // MIKO <--
    }

    @Test
    fun `json round trip keeps every field`() {
        val settings = CategoryLibrarySettings(
            filters = LibraryFilterSettings(
                unread = TriState.ENABLED_IS,
                lewd = TriState.ENABLED_NOT,
                tracking = mapOf(1L to TriState.ENABLED_IS, 5L to TriState.DISABLED),
                tags = true,
                includedTags = setOf("isekai"),
                excludedTags = setOf("dropped"),
            ),
            portraitColumns = 4,
            landscapeColumns = 7,
            genreGroupMinSize = 5,
            titleSimilarityThreshold = 85,
        )
            .withSort(LibrarySort(LibrarySort.Type.LastRead, LibrarySort.Direction.Descending))
            .withDisplay(LibraryDisplayMode.List)
            .withGrouping(
                LibraryGrouping(listOf(LibraryGroupLayer(LibraryGroupType.SOURCE, LibraryGroupSort.ITEM_COUNT, false))),
            )
        val decoded = CategoryLibrarySettings.fromJson(settings.toJson())
        decoded shouldBe settings
        decoded.sortMode shouldBe LibrarySort(LibrarySort.Type.LastRead, LibrarySort.Direction.Descending)
        decoded.displayMode shouldBe LibraryDisplayMode.List
        decoded.libraryGrouping.layers.single().type shouldBe LibraryGroupType.SOURCE
        decoded.filters.tracking(1L) shouldBe TriState.ENABLED_IS
        decoded.filters.tracking(99L) shouldBe TriState.DISABLED
        // MIKO -->
        decoded.titleSimilarityThreshold shouldBe 85
        // MIKO <--
    }

    // MIKO -->
    @Test
    fun `json without titleSimilarityThreshold deserializes to the default`() {
        val decoded = CategoryLibrarySettings.fromJson("""{"portraitColumns":3}""")
        decoded.titleSimilarityThreshold shouldBe 60
    }
    // MIKO <--

    @Test
    fun `unknown keys and garbage are tolerated`() {
        CategoryLibrarySettings.fromJson("""{"filters":{"unread":"ENABLED_IS","future":1},"extra":true}""")
            .filters.unread shouldBe TriState.ENABLED_IS
        CategoryLibrarySettings.fromJson("{oops") shouldBe CategoryLibrarySettings.default
        CategoryLibrarySettings.fromJson(null) shouldBe CategoryLibrarySettings.default
        // Unknown tri-state names degrade to DISABLED instead of throwing.
        CategoryLibrarySettings.fromJson("""{"filters":{"unread":"WHATEVER"}}""").filters.unread shouldBe TriState.DISABLED
    }

    @Test
    fun `filter keys toggle through the tri-state cycle`() {
        var filters = LibraryFilterSettings.default
        filters = filters.toggle(LibraryFilterKey.DOWNLOADED)
        filters[LibraryFilterKey.DOWNLOADED] shouldBe TriState.ENABLED_IS
        filters = filters.toggle(LibraryFilterKey.DOWNLOADED)
        filters[LibraryFilterKey.DOWNLOADED] shouldBe TriState.ENABLED_NOT
        filters = filters.toggle(LibraryFilterKey.DOWNLOADED)
        filters[LibraryFilterKey.DOWNLOADED] shouldBe TriState.DISABLED
        filters.isActive shouldBe false
        filters.toggleTracking(3L).isActive shouldBe true
        filters.copy(tags = true).isActive shouldBe false
        filters.copy(tags = true, includedTags = setOf("x")).isActive shouldBe true
    }
}
