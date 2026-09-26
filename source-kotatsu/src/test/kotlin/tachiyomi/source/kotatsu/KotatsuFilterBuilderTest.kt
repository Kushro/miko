// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(InternalParsersApi::class)

package tachiyomi.source.kotatsu

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import tachiyomi.source.kotatsu.mapping.SortFilter
import tachiyomi.source.kotatsu.mapping.StatesGroup
import tachiyomi.source.kotatsu.mapping.TagSelect
import tachiyomi.source.kotatsu.mapping.TagsGroup
import tachiyomi.source.kotatsu.mapping.buildFilterList
import tachiyomi.source.kotatsu.mapping.latestSortOrder
import tachiyomi.source.kotatsu.mapping.popularSortOrder
import tachiyomi.source.kotatsu.mapping.toMangaListFilter

@Execution(ExecutionMode.CONCURRENT)
class KotatsuFilterBuilderTest {

    @Test
    fun `only a placeholder and the sort filter are offered while options load`() {
        val parser = FakeMangaParser()

        val filters = buildFilterList(parser, null)

        filters.size shouldBe 2
        filters[0].shouldBeInstanceOf<Filter.Header>()
        filters[1].shouldBeInstanceOf<SortFilter>()
    }

    @Test
    fun `the sort filter defaults to UPDATED and lists every available order`() {
        val parser = FakeMangaParser()

        val sort = buildFilterList(parser, parser.filterOptions).filterIsInstance<SortFilter>().single()

        sort.values.toList() shouldBe listOf("Updated", "Popularity")
        sort.state?.index shouldBe 0
    }

    @Test
    fun `selected tags states and order round trip into the parser filter`() {
        val parser = FakeMangaParser()
        val filters = buildFilterList(parser, parser.filterOptions)
        val tags = filters.filterIsInstance<TagsGroup>().single()
        tags.state[0].state = Filter.TriState.STATE_INCLUDE
        tags.state[2].state = Filter.TriState.STATE_EXCLUDE
        val states = filters.filterIsInstance<StatesGroup>().single()
        states.state.single { it.value == MangaState.FINISHED }.state = true
        filters.filterIsInstance<SortFilter>().single().state = Filter.Sort.Selection(1, false)

        val (order, filter) = toMangaListFilter(parser, "", filters, parser.filterOptions)

        order shouldBe SortOrder.POPULARITY
        filter.tags.map { it.key } shouldBe listOf("action")
        filter.tagsExclude.map { it.key } shouldBe listOf("drama")
        filter.states shouldBe setOf(MangaState.FINISHED)
        filter.query shouldBe null
    }

    @Test
    fun `excluded tags are dropped when the parser cannot exclude`() {
        val parser = FakeMangaParser(
            filterCapabilities = MangaListFilterCapabilities(
                isMultipleTagsSupported = true,
                isSearchSupported = true,
                isSearchWithFiltersSupported = true,
            ),
        )
        val filters = buildFilterList(parser, parser.filterOptions)
        val tags = filters.filterIsInstance<TagsGroup>().single()
        tags.state[0].state = Filter.TriState.STATE_INCLUDE
        tags.state[2].state = Filter.TriState.STATE_EXCLUDE

        val (_, filter) = toMangaListFilter(parser, "", filters, parser.filterOptions)

        filter.tags.map { it.key } shouldBe listOf("action")
        filter.tagsExclude.isEmpty() shouldBe true
    }

    @Test
    fun `a single tag select is offered when the parser accepts only one tag`() {
        val parser = FakeMangaParser(
            filterCapabilities = MangaListFilterCapabilities(
                isSearchSupported = true,
                isSearchWithFiltersSupported = true,
            ),
        )
        val filters = buildFilterList(parser, parser.filterOptions)
        val tagSelect = filters.filterIsInstance<TagSelect>().single()
        // Index 0 is the "Any" entry, so index 2 is the second real tag.
        tagSelect.state = 2

        val (_, filter) = toMangaListFilter(parser, "", filters, parser.filterOptions)

        filters.filterIsInstance<TagsGroup>().isEmpty() shouldBe true
        filter.tags.map { it.key } shouldBe listOf("comedy")
    }

    @Test
    fun `the query wins over the filters when the parser cannot combine them`() {
        val parser = FakeMangaParser(
            filterCapabilities = MangaListFilterCapabilities(
                isMultipleTagsSupported = true,
                isTagsExclusionSupported = true,
                isSearchSupported = true,
            ),
        )
        val filters = buildFilterList(parser, parser.filterOptions)
        filters.filterIsInstance<TagsGroup>().single().state[0].state = Filter.TriState.STATE_INCLUDE

        val (_, filter) = toMangaListFilter(parser, " naruto ", filters, parser.filterOptions)

        filter.query shouldBe "naruto"
        filter.tags.isEmpty() shouldBe true
    }

    @Test
    fun `the query is dropped when the parser cannot search`() {
        val parser = FakeMangaParser(filterCapabilities = MangaListFilterCapabilities())

        val (_, filter) = toMangaListFilter(parser, "naruto", FilterList(), parser.filterOptions)

        filter.query shouldBe null
    }

    @Test
    fun `the search falls back to RELEVANCE when no order is selected`() {
        val parser = FakeMangaParser(
            availableSortOrders = setOf(SortOrder.RELEVANCE, SortOrder.ALPHABETICAL),
        )

        val (order, _) = toMangaListFilter(parser, "naruto", FilterList(), parser.filterOptions)

        order shouldBe SortOrder.RELEVANCE
    }

    @Test
    fun `popular and latest pick their preferred order with a fallback`() {
        popularSortOrder(FakeMangaParser()) shouldBe SortOrder.POPULARITY
        latestSortOrder(FakeMangaParser()) shouldBe SortOrder.UPDATED

        val limited = FakeMangaParser(availableSortOrders = setOf(SortOrder.ALPHABETICAL))
        popularSortOrder(limited) shouldBe SortOrder.ALPHABETICAL
        latestSortOrder(limited) shouldBe SortOrder.ALPHABETICAL
    }
}
