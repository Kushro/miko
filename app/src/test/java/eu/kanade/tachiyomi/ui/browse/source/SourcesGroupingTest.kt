package eu.kanade.tachiyomi.ui.browse.source

import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import eu.kanade.domain.source.model.SourcesGroupMode
import eu.kanade.domain.source.model.SourcesSortMode
import eu.kanade.presentation.browse.SourceHeaderLabel
import eu.kanade.presentation.browse.SourceUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source

/**
 * MIKO — unit tests for the Sources tab grouping/sorting (see `SourcesGrouping.kt`).
 */
class SourcesGroupingTest {

    private fun source(
        id: Long,
        name: String = "Source $id",
        lang: String = "en",
        category: String? = null,
        pin: Pins = Pins.unpinned,
        isUsedLast: Boolean = false,
    ) = Source(
        id = id,
        lang = lang,
        name = name,
        supportsLatest = true,
        isStub = false,
        pin = pin,
        isUsedLast = isUsedLast,
        category = category,
        categories = emptySet(),
    )

    private fun List<SourceUiModel>.headers() = filterIsInstance<SourceUiModel.Header>()

    private fun List<SourceUiModel>.itemIds() = filterIsInstance<SourceUiModel.Item>().map { it.source.id }

    private fun group(
        sources: List<Pair<Source, SourceKind>>,
        features: Map<Long, Set<SourceFeature>> = emptyMap(),
        groupMode: SourcesGroupMode = SourcesGroupMode.KIND,
        sortMode: SourcesSortMode = SourcesSortMode.NAME,
        descending: Boolean = false,
        collapsedKeys: Set<String> = emptySet(),
    ) = groupAndSortSources(sources, features, groupMode, sortMode, descending, collapsedKeys)

    @Test
    fun `KIND groups in enum order`() {
        val result = group(
            listOf(
                source(1, name = "Ext") to SourceKind.EXTENSION,
                source(2, name = "Local") to SourceKind.LOCAL,
                source(3, name = "Parser") to SourceKind.BUILT_IN,
            ),
        )

        assertEquals(
            listOf(SourceKind.LOCAL, SourceKind.BUILT_IN, SourceKind.EXTENSION),
            result.headers().map { (it.label as SourceHeaderLabel.Kind).kind },
        )
        assertEquals(listOf(2L, 3L, 1L), result.itemIds())
        assertTrue(result.headers().all { it.count == 1 })
    }

    @Test
    fun `KIND_AND_LANGUAGE nests language inside the kind`() {
        val result = group(
            listOf(
                source(1, name = "A", lang = "es") to SourceKind.BUILT_IN,
                source(2, name = "B", lang = "en") to SourceKind.BUILT_IN,
                source(3, name = "C", lang = "en") to SourceKind.EXTENSION,
            ),
            groupMode = SourcesGroupMode.KIND_AND_LANGUAGE,
        )

        val labels = result.headers().map { it.label as SourceHeaderLabel.KindAndLanguage }
        assertEquals(
            listOf(
                SourceKind.BUILT_IN to "en",
                SourceKind.BUILT_IN to "es",
                SourceKind.EXTENSION to "en",
            ),
            labels.map { it.kind to it.code },
        )
        assertEquals(listOf(2L, 1L, 3L), result.itemIds())
    }

    @Test
    fun `LANGUAGE keeps the upstream grouping, sources without language last`() {
        val result = group(
            listOf(
                source(1, name = "A", lang = "") to SourceKind.EXTENSION,
                source(2, name = "B", lang = "es") to SourceKind.BUILT_IN,
                source(3, name = "C", lang = "en") to SourceKind.EXTENSION,
            ),
            groupMode = SourcesGroupMode.LANGUAGE,
        )

        assertEquals(
            listOf("en", "es", ""),
            result.headers().map { (it.label as SourceHeaderLabel.Language).code },
        )
        assertEquals(listOf(3L, 2L, 1L), result.itemIds())
    }

    @Test
    fun `NONE produces a flat list without headers`() {
        val result = group(
            listOf(
                source(2, name = "B") to SourceKind.EXTENSION,
                source(1, name = "A") to SourceKind.BUILT_IN,
            ),
            groupMode = SourcesGroupMode.NONE,
        )

        assertTrue(result.headers().isEmpty())
        assertEquals(listOf(1L, 2L), result.itemIds())
    }

    @Test
    fun `pinned, last used and preset categories always come first`() {
        val result = group(
            listOf(
                source(1, name = "Plain") to SourceKind.BUILT_IN,
                source(2, name = "Pinned", pin = Pins.pinned) to SourceKind.EXTENSION,
                source(3, name = "LastUsed", isUsedLast = true) to SourceKind.EXTENSION,
                source(4, name = "Preset", category = "Manga") to SourceKind.EXTENSION,
            ),
        )

        assertEquals(
            listOf(
                SourceHeaderLabel.LastUsed,
                SourceHeaderLabel.Pinned,
                SourceHeaderLabel.Category("Manga"),
                SourceHeaderLabel.Kind(SourceKind.BUILT_IN),
            ),
            result.headers().map { it.label },
        )
        assertEquals(listOf(3L, 2L, 4L, 1L), result.itemIds())
    }

    @Test
    fun `sorting by features puts the undetected ones last, whatever the direction`() {
        val sources = listOf(
            source(1, name = "None") to SourceKind.BUILT_IN,
            source(2, name = "Two") to SourceKind.BUILT_IN,
            source(3, name = "One") to SourceKind.BUILT_IN,
        )
        val features = mapOf(
            2L to setOf(SourceFeature.TAGS, SourceFeature.LATEST),
            3L to setOf(SourceFeature.TAGS),
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            group(sources, features = features, sortMode = SourcesSortMode.FEATURES).itemIds(),
        )
        assertEquals(
            listOf(3L, 2L, 1L),
            group(
                sources,
                features = features,
                sortMode = SourcesSortMode.FEATURES,
                descending = true,
            ).itemIds(),
        )
    }

    @Test
    fun `a collapsed group keeps its header and drops its items`() {
        val sources = listOf(
            source(1, name = "A") to SourceKind.BUILT_IN,
            source(2, name = "B") to SourceKind.EXTENSION,
        )
        val collapsedKey = group(sources).headers().first().key

        val result = group(sources, collapsedKeys = setOf(collapsedKey))

        assertEquals(2, result.headers().size)
        assertTrue(result.headers().first().collapsed)
        assertEquals(1, result.headers().first().count)
        assertEquals(listOf(2L), result.itemIds())
    }

    @Test
    fun `descending name sort reverses the items of every group`() {
        val result = group(
            listOf(
                source(1, name = "A") to SourceKind.BUILT_IN,
                source(2, name = "C") to SourceKind.BUILT_IN,
                source(3, name = "B") to SourceKind.BUILT_IN,
            ),
            descending = true,
        )

        assertEquals(listOf(2L, 3L, 1L), result.itemIds())
    }

    @Test
    fun `kind counts ignore the duplicated copies of a source`() {
        val counts = sourceKindCounts(
            listOf(
                source(1) to SourceKind.BUILT_IN,
                source(1, isUsedLast = true) to SourceKind.BUILT_IN,
                source(1, category = "Manga") to SourceKind.BUILT_IN,
                source(2) to SourceKind.EXTENSION,
            ),
        )

        assertEquals(mapOf(SourceKind.BUILT_IN to 1, SourceKind.EXTENSION to 1), counts)
    }
}
