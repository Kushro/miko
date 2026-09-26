package eu.kanade.tachiyomi.source.enhancement.comix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** MIKO — the stored Comix urls → thread API inputs. */
class ComixUrlsTest {

    @Test
    fun `series page path from the extension's stored url`() {
        assertEquals("/title/emqg8-solo-leveling", seriesPagePathOf("/emqg8-solo-leveling"))
    }

    @Test
    fun `series page path from an absolute url with query, fragment and mirror host`() {
        assertEquals(
            "/title/emqg8-solo-leveling",
            seriesPagePathOf("https://comix.ws/title/emqg8-solo-leveling/?tab=chapters#top"),
        )
    }

    @Test
    fun `series page path from a chapter url keeps only the series segment`() {
        assertEquals("/title/emqg8-solo-leveling", seriesPagePathOf("title/emqg8-solo-leveling/2749754-chapter-200"))
    }

    @Test
    fun `series page path of a blank url is null`() {
        assertNull(seriesPagePathOf("   "))
        assertNull(seriesPagePathOf("/title/"))
    }

    @Test
    fun `chapter page path from the extension's stored url`() {
        assertEquals(
            "/title/emqg8-solo-leveling/2749754-chapter-200",
            chapterPagePathOf("title/emqg8-solo-leveling/2749754-chapter-200"),
        )
    }

    @Test
    fun `chapter page path from an absolute url with a trailing slash`() {
        assertEquals(
            "/title/emqg8-solo-leveling/2749754-chapter-200",
            chapterPagePathOf("https://comix.to/title/emqg8-solo-leveling/2749754-chapter-200/"),
        )
    }

    @Test
    fun `chapter page path without a chapter segment is null`() {
        assertNull(chapterPagePathOf("/emqg8-solo-leveling"))
    }

    @Test
    fun `hid, chapter id and number are read off the paths`() {
        assertEquals("emqg8", hidOf("/title/emqg8-solo-leveling"))
        assertEquals("emqg8", hidOf("/title/emqg8-solo-leveling/2749754-chapter-200"))
        assertEquals(2749754L, chapterIdOf("/title/emqg8-solo-leveling/2749754-chapter-200"))
        assertEquals("200", chapterNumberOf("/title/emqg8-solo-leveling/2749754-chapter-200"))
        assertEquals("200.5", chapterNumberOf("/title/emqg8-solo-leveling/2749755-chapter-200.5"))
        assertNull(chapterIdOf("/title/emqg8-solo-leveling/chapter-200"))
    }

    @Test
    fun `page identifiers follow the site's own format`() {
        assertEquals("manga32026", seriesPageIdentifier(32026))
        assertEquals("manga32026_chap200_vol0", chapterPageIdentifier(32026, "200", 0))
        assertEquals("manga32026_chap200_vol0", chapterPageIdentifier(32026, "200.0", 0))
        assertEquals("manga32026_chap200.5_vol2", chapterPageIdentifier(32026, "200.50", 2))
    }
}
