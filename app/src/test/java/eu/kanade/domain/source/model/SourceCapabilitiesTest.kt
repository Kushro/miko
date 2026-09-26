package eu.kanade.domain.source.model

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.source.kotatsu.KotatsuCapabilities

/**
 * MIKO — unit tests for the static feature detection in `SourceCapabilities.kt`.
 *
 * `SourceCapabilities.of` itself is not covered: it needs an `Extension.Installed` (Android
 * `Drawable`) and an `EnhancedHttpSource` (OkHttp + Android), so only the three pure building
 * blocks it composes are tested here.
 */
class SourceCapabilitiesTest {

    // --- ofKotatsu -------------------------------------------------------------------------

    private fun caps(
        search: Boolean = false,
        searchWithFilters: Boolean = false,
        multipleTags: Boolean = false,
        tagExclusion: Boolean = false,
        year: Boolean = false,
        yearRange: Boolean = false,
        authorSearch: Boolean = false,
        originalLocale: Boolean = false,
        multiLanguage: Boolean = false,
        latest: Boolean = false,
        extraSortOrders: Boolean = false,
        login: Boolean = false,
        configurable: Boolean = false,
        alternativeDomains: Boolean = false,
        nsfw: Boolean = false,
    ) = KotatsuCapabilities(
        search = search,
        searchWithFilters = searchWithFilters,
        multipleTags = multipleTags,
        tagExclusion = tagExclusion,
        year = year,
        yearRange = yearRange,
        authorSearch = authorSearch,
        originalLocale = originalLocale,
        multiLanguage = multiLanguage,
        latest = latest,
        extraSortOrders = extraSortOrders,
        login = login,
        configurable = configurable,
        alternativeDomains = alternativeDomains,
        nsfw = nsfw,
    )

    @Test
    fun `every Kotatsu parser reports tags and related manga`() {
        assertEquals(
            setOf(SourceFeature.TAGS, SourceFeature.RELATED),
            SourceCapabilities.ofKotatsu(caps()),
        )
    }

    @Test
    fun `an all-capable parser maps to every statically detectable feature`() {
        val features = SourceCapabilities.ofKotatsu(
            caps(
                search = true,
                searchWithFilters = true,
                multipleTags = true,
                tagExclusion = true,
                year = true,
                yearRange = true,
                authorSearch = true,
                originalLocale = true,
                multiLanguage = true,
                latest = true,
                extraSortOrders = true,
                login = true,
                configurable = true,
                alternativeDomains = true,
                nsfw = true,
            ),
        )

        assertEquals(
            setOf(
                SourceFeature.LATEST,
                SourceFeature.SEARCH,
                SourceFeature.TAGS,
                SourceFeature.TAG_EXCLUSION,
                SourceFeature.YEAR,
                SourceFeature.AUTHOR_SEARCH,
                SourceFeature.MULTI_LANGUAGE,
                SourceFeature.LOGIN,
                SourceFeature.CONFIGURABLE,
                SourceFeature.ALTERNATIVE_DOMAINS,
                SourceFeature.NSFW,
                SourceFeature.RELATED,
            ),
            features,
        )
    }

    @Test
    fun `a year range alone is enough for YEAR, an original locale alone for MULTI_LANGUAGE`() {
        assertTrue(SourceFeature.YEAR in SourceCapabilities.ofKotatsu(caps(yearRange = true)))
        assertTrue(SourceFeature.MULTI_LANGUAGE in SourceCapabilities.ofKotatsu(caps(originalLocale = true)))
    }

    @Test
    fun `flags without a SourceFeature of their own add nothing`() {
        val features = SourceCapabilities.ofKotatsu(
            caps(searchWithFilters = true, multipleTags = true, extraSortOrders = true),
        )

        assertEquals(setOf(SourceFeature.TAGS, SourceFeature.RELATED), features)
    }

    // --- ofFilterList ----------------------------------------------------------------------

    private fun triState(name: String) = object : Filter.TriState(name) {}

    private fun checkBox(name: String) = object : Filter.CheckBox(name) {}

    private fun group(name: String, state: List<Filter<*>>) = object : Filter.Group<Filter<*>>(name, state) {}

    private fun text(name: String) = object : Filter.Text(name) {}

    private fun select(name: String) = object : Filter.Select<String>(name, arrayOf("a", "b")) {}

    @Test
    fun `a genre group of tri-states reports tags and tag exclusion`() {
        val features = SourceCapabilities.ofFilterList(
            FilterList(group("Genres", listOf(triState("Action"), triState("Comedy")))),
        )

        assertEquals(setOf(SourceFeature.TAGS, SourceFeature.TAG_EXCLUSION), features)
    }

    @Test
    fun `a genre group of check boxes reports tags only`() {
        val features = SourceCapabilities.ofFilterList(
            FilterList(group("Genre", listOf(checkBox("Action"), checkBox("Comedy")))),
        )

        assertEquals(setOf(SourceFeature.TAGS), features)
    }

    @Test
    fun `a small group with an unrelated name is not a tag selector`() {
        val features = SourceCapabilities.ofFilterList(
            FilterList(group("Status", listOf(checkBox("Ongoing"), checkBox("Completed")))),
        )

        assertEquals(emptySet<SourceFeature>(), features)
    }

    @Test
    fun `a big group is treated as a tag selector even with an unrelated name`() {
        val options = List(5) { checkBox("Option $it") }

        val features = SourceCapabilities.ofFilterList(FilterList(group("Status", options)))

        assertEquals(setOf(SourceFeature.TAGS), features)
    }

    @Test
    fun `text filters expose author and year search`() {
        assertEquals(setOf(SourceFeature.AUTHOR_SEARCH), SourceCapabilities.ofFilterList(FilterList(text("Author"))))
        assertEquals(setOf(SourceFeature.AUTHOR_SEARCH), SourceCapabilities.ofFilterList(FilterList(text("Artist"))))
        assertEquals(setOf(SourceFeature.YEAR), SourceCapabilities.ofFilterList(FilterList(text("Year"))))
        assertEquals(emptySet<SourceFeature>(), SourceCapabilities.ofFilterList(FilterList(text("Title"))))
    }

    @Test
    fun `a select filter only counts when its name looks like a tag`() {
        assertEquals(setOf(SourceFeature.TAGS), SourceCapabilities.ofFilterList(FilterList(select("Category"))))
        assertEquals(emptySet<SourceFeature>(), SourceCapabilities.ofFilterList(FilterList(select("Order by"))))
    }

    @Test
    fun `an empty filter list yields nothing`() {
        assertEquals(emptySet<SourceFeature>(), SourceCapabilities.ofFilterList(FilterList()))
    }

    // --- ofMarkers -------------------------------------------------------------------------

    @Test
    fun `a catalogue source is searchable, and latest only when it says so`() {
        assertEquals(
            setOf(SourceFeature.SEARCH),
            SourceCapabilities.ofMarkers(FakeCatalogueSource()),
        )
        assertEquals(
            setOf(SourceFeature.SEARCH, SourceFeature.LATEST),
            SourceCapabilities.ofMarkers(FakeCatalogueSource(supportsLatest = true)),
        )
    }

    @Test
    fun `the all and other pseudo-languages mean multi-language`() {
        assertTrue(
            SourceFeature.MULTI_LANGUAGE in SourceCapabilities.ofMarkers(FakeCatalogueSource(lang = "all")),
        )
        assertTrue(
            SourceFeature.MULTI_LANGUAGE in SourceCapabilities.ofMarkers(FakeCatalogueSource(lang = "other")),
        )
        assertFalse(
            SourceFeature.MULTI_LANGUAGE in SourceCapabilities.ofMarkers(FakeCatalogueSource(lang = "en")),
        )
    }

    @Test
    fun `related manga support is picked up from the source flag`() {
        assertTrue(
            SourceFeature.RELATED in
                SourceCapabilities.ofMarkers(FakeCatalogueSource(supportsRelatedMangas = true)),
        )
        assertFalse(SourceFeature.RELATED in SourceCapabilities.ofMarkers(FakeCatalogueSource()))
    }

    /** Minimal online-ish source: enough for the marker-interface pass, no network anywhere. */
    private class FakeCatalogueSource(
        override val id: Long = 1L,
        override val name: String = "Fake",
        override val lang: String = "en",
        override val supportsLatest: Boolean = false,
        override val supportsRelatedMangas: Boolean = false,
    ) : CatalogueSource
}
