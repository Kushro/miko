package eu.kanade.tachiyomi.ui.deeplink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the pure (Android-free) half of `MikoEntryLink.kt`.
 *
 * `android.net.Uri` does not exist on the JVM, so only [MikoEntryLink.toQueryParams] and
 * [MikoEntryLink.Companion.parseQuery] are exercised here; `toUri`/`parse`/`matches` are thin
 * wrappers over these.
 */
class MikoEntryLinkTest {

    private val full = MikoEntryLink(
        sourceId = 1234567890L,
        title = "Naruto",
        mangaUrl = "/manga/naruto?lang=es",
        sourceName = "MangaSource",
        lang = "es",
        versionId = 2,
        chapterUrl = "/manga/naruto/chapter-700",
        author = "Masashi Kishimoto",
        genres = listOf("Action", "Adventure"),
        status = 2L,
    )

    @Test
    fun `a fully populated link survives the round trip`() {
        assertEquals(full, MikoEntryLink.parseQuery(full.toQueryParams()))
    }

    @Test
    fun `a link with only the required fields survives the round trip`() {
        val minimal = MikoEntryLink(
            sourceId = 42L,
            title = "One Piece",
            mangaUrl = "/one-piece",
        )

        val parsed = MikoEntryLink.parseQuery(minimal.toQueryParams())

        assertEquals(minimal, parsed)
        assertNull(parsed!!.sourceName)
        assertNull(parsed.lang)
        assertNull(parsed.versionId)
        assertNull(parsed.chapterUrl)
        assertNull(parsed.author)
        assertNull(parsed.status)
        assertTrue(parsed.genres.isEmpty())
    }

    @Test
    fun `only the required params are always emitted`() {
        val params = MikoEntryLink(sourceId = 42L, title = "One Piece", mangaUrl = "/one-piece")
            .toQueryParams()

        assertEquals(setOf("s", "t", "u"), params.keys)
    }

    @Test
    fun `the manga url is not readable in the clear`() {
        val params = full.toQueryParams()

        assertFalse(params.getValue("u").contains("/manga/naruto"))
        assertEquals("/manga/naruto?lang=es", MikoEntryLink.parseQuery(params)?.mangaUrl)
    }

    @Test
    fun `a corrupt manga url is rejected`() {
        val params = full.toQueryParams() + ("u" to "not base64 at all!!")

        assertNull(MikoEntryLink.parseQuery(params))
    }

    @Test
    fun `an empty manga url is rejected`() {
        assertNull(MikoEntryLink.parseQuery(mapOf("s" to "42", "t" to "One Piece", "u" to "")))
    }

    @Test
    fun `each required param is really required`() {
        val params = full.toQueryParams()

        listOf("s", "t", "u").forEach { required ->
            assertNull(
                MikoEntryLink.parseQuery(params - required),
                "the link parsed without `$required`",
            )
        }
    }

    @Test
    fun `a non numeric source id is rejected`() {
        assertNull(MikoEntryLink.parseQuery(full.toQueryParams() + ("s" to "not-a-number")))
    }

    @Test
    fun `a blank title is rejected`() {
        assertNull(MikoEntryLink.parseQuery(full.toQueryParams() + ("t" to "   ")))
    }

    @Test
    fun `titles with spaces and non latin characters survive the round trip`() {
        val exotic = full.copy(title = "  ナルト —  Naruto: 疾風伝 & Co. (100%) ")

        assertEquals(exotic, MikoEntryLink.parseQuery(exotic.toQueryParams()))
    }

    @Test
    fun `a corrupt chapter url only drops the chapter`() {
        val parsed = MikoEntryLink.parseQuery(full.toQueryParams() + ("cu" to "@@@"))

        assertEquals(full.copy(chapterUrl = null), parsed)
    }

    @Test
    fun `only the first five genres travel`() {
        val many = full.copy(genres = listOf("A", "B", "C", "D", "E", "F", "G"))

        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            MikoEntryLink.parseQuery(many.toQueryParams())?.genres,
        )
    }

    @Test
    fun `blank genres are dropped and a genre carrying a comma is split`() {
        // Documented lossiness: genres are joined by commas on the wire, so an embedded comma
        // reads back as two genres. Everything else round trips.
        val commas = full.copy(genres = listOf("Action", "  ", "Slice of Life, Drama"))

        assertEquals(
            listOf("Action", "Slice of Life", "Drama"),
            MikoEntryLink.parseQuery(commas.toQueryParams())?.genres,
        )
    }

    @Test
    fun `an unknown status is not emitted`() {
        val unknown = full.copy(status = 0L)

        assertFalse(unknown.toQueryParams().containsKey("st"))
        assertNull(MikoEntryLink.parseQuery(unknown.toQueryParams())?.status)
    }

    @Test
    fun `an over long author is truncated`() {
        val longAuthor = full.copy(author = "a".repeat(200))

        val parsed = MikoEntryLink.parseQuery(longAuthor.toQueryParams())

        assertEquals(81, parsed?.author?.length ?: 0)
        assertTrue(parsed?.author?.endsWith("…") == true)
    }
}
