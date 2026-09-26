package eu.kanade.tachiyomi.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the library local-tag filter (see `LibraryTagFilter.kt`).
 */
class LibraryTagFilterTest {

    private val tags = listOf("Shounen", "author:kubo", "finished")

    @Test
    fun `nothing picked lets every entry through`() {
        assertTrue(matchesTagFilter(tags, emptySet(), emptySet()))
        assertTrue(matchesTagFilter(emptyList(), emptySet(), emptySet()))
    }

    @Test
    fun `an included tag the entry has passes`() {
        assertTrue(matchesTagFilter(tags, setOf("shounen"), emptySet()))
    }

    @Test
    fun `every included tag must be present`() {
        assertTrue(matchesTagFilter(tags, setOf("shounen", "finished"), emptySet()))
        assertFalse(matchesTagFilter(tags, setOf("shounen", "dropped"), emptySet()))
    }

    @Test
    fun `any excluded tag hides the entry`() {
        assertFalse(matchesTagFilter(tags, emptySet(), setOf("finished")))
        assertFalse(matchesTagFilter(tags, setOf("shounen"), setOf("finished")))
        assertTrue(matchesTagFilter(tags, setOf("shounen"), setOf("dropped")))
    }

    @Test
    fun `an entry with no tags only passes when nothing is included`() {
        assertTrue(matchesTagFilter(emptyList(), emptySet(), setOf("dropped")))
        assertFalse(matchesTagFilter(emptyList(), setOf("shounen"), emptySet()))
        assertFalse(matchesTagFilter(emptyList(), setOf("shounen"), setOf("dropped")))
    }

    @Test
    fun `blank tags do not count as tags`() {
        assertFalse(matchesTagFilter(listOf("", "   "), setOf("shounen"), emptySet()))
        assertTrue(matchesTagFilter(listOf("", "   "), emptySet(), setOf("dropped")))
    }

    @Test
    fun `matching ignores case and surrounding whitespace on both sides`() {
        assertTrue(matchesTagFilter(listOf(" ShOuNeN "), setOf("shounen"), emptySet()))
        assertTrue(matchesTagFilter(listOf("shounen"), setOf(" SHOUNEN "), emptySet()))
        assertFalse(matchesTagFilter(listOf(" ShOuNeN "), emptySet(), setOf("SHOUNEN")))
    }

    @Test
    fun `namespaced tags are matched by their full display name`() {
        assertTrue(matchesTagFilter(tags, setOf("author:kubo"), emptySet()))
        // A bare half of a namespaced tag is not the same tag: that is what search is for.
        assertFalse(matchesTagFilter(tags, setOf("kubo"), emptySet()))
    }

    @Test
    fun `normalizeLibraryTag is the canonical stored form`() {
        assertEquals("author:kubo", normalizeLibraryTag("  Author:Kubo  "))
        assertEquals("", normalizeLibraryTag("   "))
    }
}
