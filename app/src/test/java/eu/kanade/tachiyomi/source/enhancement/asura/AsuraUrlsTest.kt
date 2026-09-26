package eu.kanade.tachiyomi.source.enhancement.asura

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the pure URL helpers of the Asura Scans enhancement. No network involved.
 */
class AsuraUrlsTest {

    // --- seriesSlugOf ----------------------------------------------------------------------

    @Test
    fun `series slug from the current url format`() {
        assertEquals("absolute-sword-sense", seriesSlugOf("/series/absolute-sword-sense"))
    }

    @Test
    fun `series slug from the current url format with a trailing slash`() {
        assertEquals("absolute-sword-sense", seriesSlugOf("/series/absolute-sword-sense/"))
    }

    @Test
    fun `series slug keeps the random suffix of a comics url`() {
        assertEquals(
            "absolute-sword-sense-b60d532c",
            seriesSlugOf("/comics/absolute-sword-sense-b60d532c"),
        )
    }

    @Test
    fun `series slug from an absolute url with query and fragment`() {
        assertEquals(
            "absolute-sword-sense-b60d532c",
            seriesSlugOf("https://asurascans.com/comics/absolute-sword-sense-b60d532c?ref=home#top"),
        )
    }

    @Test
    fun `series slug from the legacy manga format drops the numeric id`() {
        assertEquals("absolute-sword-sense", seriesSlugOf("/manga/1947-absolute-sword-sense"))
    }

    @Test
    fun `series slug from the legacy manga format without a numeric id`() {
        assertEquals("absolute-sword-sense", seriesSlugOf("/manga/absolute-sword-sense"))
    }

    @Test
    fun `series slug never strips a legitimate eight character tail`() {
        // "12345678" looks exactly like a random suffix, but only stripRandomSuffix may remove it.
        assertEquals("nano-machine-12345678", seriesSlugOf("/series/nano-machine-12345678"))
    }

    @Test
    fun `series slug from a chapter url falls back to the series segment`() {
        assertEquals(
            "absolute-sword-sense",
            seriesSlugOf("/series/absolute-sword-sense/chapter/197"),
        )
    }

    @Test
    fun `series slug of a url without a slug is null`() {
        assertNull(seriesSlugOf(""))
        assertNull(seriesSlugOf("/"))
        assertNull(seriesSlugOf("/series/"))
        assertNull(seriesSlugOf("https://asurascans.com"))
    }

    // --- stripRandomSuffix -----------------------------------------------------------------

    @Test
    fun `strip random suffix removes an eight character tail`() {
        assertEquals("absolute-sword-sense", stripRandomSuffix("absolute-sword-sense-b60d532c"))
    }

    @Test
    fun `strip random suffix leaves a slug without one alone`() {
        assertEquals("absolute-sword-sense", stripRandomSuffix("absolute-sword-sense"))
    }

    @Test
    fun `strip random suffix only matches exactly eight lowercase alphanumerics`() {
        assertEquals("nano-machine-1234567", stripRandomSuffix("nano-machine-1234567"))
        assertEquals("nano-machine-123456789", stripRandomSuffix("nano-machine-123456789"))
        assertEquals("nano-machine-B60D532C", stripRandomSuffix("nano-machine-B60D532C"))
    }

    @Test
    fun `strip random suffix on a slug that is only a suffix`() {
        assertEquals("nano-machine-12345678", stripRandomSuffix("nano-machine-12345678-b60d532c"))
    }

    // --- chapterNumberOf -------------------------------------------------------------------

    @Test
    fun `chapter number of a whole number`() {
        assertEquals(197f, chapterNumberOf("/series/absolute-sword-sense/chapter/197"))
    }

    @Test
    fun `chapter number of a decimal number`() {
        assertEquals(197.5f, chapterNumberOf("/series/absolute-sword-sense/chapter/197.5"))
    }

    @Test
    fun `chapter number of an absolute url with a random suffix`() {
        assertEquals(
            197f,
            chapterNumberOf("https://asurascans.com/comics/absolute-sword-sense-b60d532c/chapter/197"),
        )
    }

    @Test
    fun `chapter number tolerates a trailing slash query and fragment`() {
        assertEquals(197f, chapterNumberOf("/series/absolute-sword-sense/chapter/197/"))
        assertEquals(197f, chapterNumberOf("/series/absolute-sword-sense/chapter/197?x=1#top"))
    }

    @Test
    fun `chapter number of a url without a chapter is null`() {
        assertNull(chapterNumberOf("/series/absolute-sword-sense"))
    }

    @Test
    fun `chapter number of a non numeric chapter is null`() {
        assertNull(chapterNumberOf("/series/absolute-sword-sense/chapter/prologue"))
        assertNull(chapterNumberOf("/series/absolute-sword-sense/chapter/"))
    }
}
