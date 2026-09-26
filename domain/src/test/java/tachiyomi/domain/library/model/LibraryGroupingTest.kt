package tachiyomi.domain.library.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class LibraryGroupingTest {

    @Test
    fun `empty serialized string decodes to default (no layers)`() {
        LibraryGrouping.deserialize("") shouldBe LibraryGrouping.default
        LibraryGrouping.default.layers shouldBe emptyList()
    }

    @Test
    fun `round-trips a single layer`() {
        val grouping = LibraryGrouping(
            listOf(LibraryGroupLayer(LibraryGroupType.SOURCE, LibraryGroupSort.ALPHABETICAL, ascending = false)),
        )
        val decoded = LibraryGrouping.deserialize(grouping.serialize())
        decoded shouldBe grouping
    }

    @Test
    fun `round-trips the date-based sort criteria`() {
        val grouping = LibraryGrouping(
            listOf(LibraryGroupLayer(LibraryGroupType.AUTHOR, LibraryGroupSort.LATEST_CHAPTER, ascending = false)),
        )
        LibraryGrouping.deserialize(grouping.serialize()) shouldBe grouping

        val grouping2 = LibraryGrouping(
            listOf(LibraryGroupLayer(LibraryGroupType.GENRE, LibraryGroupSort.DATE_ADDED, ascending = true)),
        )
        LibraryGrouping.deserialize(grouping2.serialize()) shouldBe grouping2
    }

    @Test
    fun `round-trips two layers preserving order`() {
        val grouping = LibraryGrouping(
            listOf(
                LibraryGroupLayer(LibraryGroupType.STATUS, LibraryGroupSort.NATURAL, ascending = true),
                LibraryGroupLayer(LibraryGroupType.SOURCE, LibraryGroupSort.ITEM_COUNT, ascending = false),
            ),
        )
        val decoded = LibraryGrouping.deserialize(grouping.serialize())
        decoded.layers.map { it.type } shouldBe listOf(LibraryGroupType.STATUS, LibraryGroupType.SOURCE)
        decoded shouldBe grouping
    }

    @Test
    fun `caps decoded layers at MAX_LAYERS`() {
        val serialized = LibraryGroupType.entries.joinToString("|") { "${it.name}:NATURAL:true" }
        val decoded = LibraryGrouping.deserialize(serialized)
        decoded.layers.size shouldBe LibraryGrouping.MAX_LAYERS
    }

    @Test
    fun `drops duplicate layer types keeping the first`() {
        val serialized = "SOURCE:NATURAL:true|SOURCE:ALPHABETICAL:false"
        val decoded = LibraryGrouping.deserialize(serialized)
        decoded.layers shouldBe listOf(LibraryGroupLayer(LibraryGroupType.SOURCE, LibraryGroupSort.NATURAL, true))
    }

    // MIKO -->
    @Test
    fun `round-trips the local rating layer`() {
        val grouping = LibraryGrouping(
            listOf(LibraryGroupLayer(LibraryGroupType.LOCAL_RATING, LibraryGroupSort.NATURAL, ascending = true)),
        )
        LibraryGrouping.deserialize(grouping.serialize()) shouldBe grouping
        LibraryGrouping.deserialize("LOCAL_RATING:NATURAL:true").layers.single().type shouldBe
            LibraryGroupType.LOCAL_RATING
    }

    @Test
    fun `round-trips the local tags layer`() {
        val grouping = LibraryGrouping(
            listOf(LibraryGroupLayer(LibraryGroupType.LOCAL_TAGS, LibraryGroupSort.ITEM_COUNT, ascending = false)),
        )
        LibraryGrouping.deserialize(grouping.serialize()) shouldBe grouping
        LibraryGrouping.deserialize("LOCAL_TAGS:ALPHABETICAL:true").layers.single() shouldBe
            LibraryGroupLayer(LibraryGroupType.LOCAL_TAGS, LibraryGroupSort.ALPHABETICAL, ascending = true)
    }

    @Test
    fun `local tags composes with another layer`() {
        val grouping = LibraryGrouping(
            listOf(
                LibraryGroupLayer(LibraryGroupType.LOCAL_TAGS, LibraryGroupSort.NATURAL, ascending = true),
                LibraryGroupLayer(LibraryGroupType.LOCAL_RATING, LibraryGroupSort.NATURAL, ascending = true),
            ),
        )
        LibraryGrouping.deserialize(grouping.serialize()) shouldBe grouping
    }

    @Test
    fun `round-trips the ruiji titles layer`() {
        val grouping = LibraryGrouping(
            listOf(LibraryGroupLayer(LibraryGroupType.RUIJI_TITLES, LibraryGroupSort.ITEM_COUNT, ascending = false)),
        )
        LibraryGrouping.deserialize(grouping.serialize()) shouldBe grouping
        LibraryGrouping.deserialize("RUIJI_TITLES:NATURAL:true").layers.single().type shouldBe
            LibraryGroupType.RUIJI_TITLES
    }
    // MIKO <--

    @Test
    fun `malformed entries are dropped instead of throwing`() {
        val decoded = LibraryGrouping.deserialize("not-a-valid-entry|SOURCE:NATURAL:true|GARBAGE:NATURAL:true")
        decoded.layers shouldBe listOf(LibraryGroupLayer(LibraryGroupType.SOURCE, LibraryGroupSort.NATURAL, true))
    }
}
