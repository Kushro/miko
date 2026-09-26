package eu.kanade.tachiyomi.ui.browse.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source

/**
 * MIKO — unit tests for the source preset filter (see `SourcePresetFilter.kt`).
 */
class SourcePresetFilterTest {

    private fun source(
        id: Long,
        name: String = "Source $id",
        lang: String = "en",
        category: String? = null,
        categories: Set<String> = emptySet(),
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
        categories = categories,
    )

    /**
     * Mirrors what `GetEnabledSources.subscribe()` emits: the plain copy, plus one copy per
     * category the source belongs to, plus a floating "last used" copy.
     */
    private val flattenedSources = listOf(
        source(1, categories = setOf("Manga")),
        source(1, category = "Manga", categories = setOf("Manga")),
        source(2, categories = setOf("Manga", "Webtoon"), pin = Pins.pinned),
        source(2, category = "Manga", categories = setOf("Manga", "Webtoon")),
        source(2, category = "Webtoon", categories = setOf("Manga", "Webtoon")),
        source(3),
        source(3, isUsedLast = true),
    )

    @Test
    fun `empty preset returns the list untouched`() {
        assertEquals(flattenedSources, filterByActivePreset(flattenedSources, ""))
    }

    @Test
    fun `active preset keeps only the copies of that category`() {
        val filtered = filterByActivePreset(flattenedSources, "Manga")

        assertEquals(listOf(1L, 2L), filtered.map { it.id })
        assertEquals(listOf("Manga", "Manga"), filtered.map { it.category })
    }

    @Test
    fun `active preset drops language, pinned and last used copies`() {
        val filtered = filterByActivePreset(flattenedSources, "Webtoon")

        assertEquals(1, filtered.size)
        assertEquals(2L, filtered.single().id)
        assertEquals("Webtoon", filtered.single().category)
        assertFalse(filtered.single().isUsedLast)
        assertFalse(Pin.Actual in filtered.single().pin)
    }

    @Test
    fun `unknown preset yields an empty list`() {
        assertEquals(emptyList<Source>(), filterByActivePreset(flattenedSources, "Nope"))
    }

    /**
     * `sourcesTabSourcesInCategories` entries, i.e. what the preference actually stores.
     */
    private val membership = setOf(
        "1|Manga",
        "2|Manga",
        "2|Webtoon",
        "3|Webtoon",
    )

    @Test
    fun `preset membership is read from the raw entries`() {
        assertEquals(setOf(1L, 2L), sourceIdsInPreset(membership, "Manga"))
        assertEquals(setOf(2L, 3L), sourceIdsInPreset(membership, "Webtoon"))
    }

    @Test
    fun `empty preset name matches nothing`() {
        assertEquals(emptySet<Long>(), sourceIdsInPreset(membership, ""))
    }

    @Test
    fun `unknown preset name matches nothing`() {
        assertEquals(emptySet<Long>(), sourceIdsInPreset(membership, "Nope"))
    }

    @Test
    fun `empty membership matches nothing`() {
        assertEquals(emptySet<Long>(), sourceIdsInPreset(emptySet(), "Manga"))
    }

    @Test
    fun `malformed entries are ignored`() {
        val malformed = setOf(
            "Manga", // no separator at all
            "|Manga", // no id
            "notAnId|Manga", // non-numeric id
            "4|", // empty category
            "5|Manga", // the only good one
        )

        assertEquals(setOf(5L), sourceIdsInPreset(malformed, "Manga"))
    }

    @Test
    fun `the separator is the first pipe, so a preset name may contain pipes`() {
        val piped = setOf(
            "7|Weird|Name",
            "8|Weird",
        )

        assertEquals(setOf(7L), sourceIdsInPreset(piped, "Weird|Name"))
        assertEquals(setOf(8L), sourceIdsInPreset(piped, "Weird"))
    }

    @Test
    fun `preset names are matched exactly, case included`() {
        assertEquals(emptySet<Long>(), sourceIdsInPreset(membership, "manga"))
        assertEquals(emptySet<Long>(), sourceIdsInPreset(membership, "Manga "))
    }
}
