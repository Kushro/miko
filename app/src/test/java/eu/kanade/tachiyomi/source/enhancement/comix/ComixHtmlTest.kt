package eu.kanade.tachiyomi.source.enhancement.comix

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/** MIKO — comix.to comment HTML → Miko markup, relative dates, page scraping fallbacks. */
class ComixHtmlTest {

    @Test
    fun `line breaks come from br, literal newlines are whitespace, entities are decoded`() {
        val html = "10/10 <br/>\nNo need to try. <br/>\n <br/>\nIt&#039;s &quot;peak&quot; &amp; legendary."
        val parsed = commentHtmlToMarkup(html)
        assertEquals("10/10\nNo need to try.\n\nIt's \"peak\" & legendary.", parsed.text)
        assertEquals(emptyList<String>(), parsed.imageUrls)
    }

    @Test
    fun `inline formatting becomes markup and spoilers become fences`() {
        val html = "<b>Solo Leveling</b> is <i>fine</i>, <s>bad</s> and <span class=\"spoil\">he wins</span> ok"
        assertEquals("**Solo Leveling** is *fine*, ~~bad~~ and ||he wins|| ok", commentHtmlToMarkup(html).text)
    }

    @Test
    fun `markers hug the content even when the tag has inner spaces`() {
        assertEquals("a **b** c", commentHtmlToMarkup("a <b> b </b> c").text)
    }

    @Test
    fun `empty tags do not leave empty markers`() {
        assertEquals("a c", commentHtmlToMarkup("a <b> </b> c").text)
    }

    @Test
    fun `rich images are pulled out of the text and kept in order`() {
        val html = "Look <br/>\n<img src=\"https://i.postimg.cc/a.jpg\" alt=\"\" class=\"rich-img\"/> and " +
            "<img src=\"/images/cmm/78/x.webp\"/>"
        val parsed = commentHtmlToMarkup(html)
        assertEquals("Look\nand", parsed.text)
        assertEquals(listOf("https://i.postimg.cc/a.jpg", "https://comix.to/images/cmm/78/x.webp"), parsed.imageUrls)
    }

    @Test
    fun `links keep their text and unknown tags are unwrapped`() {
        val html = "<p>Read <a href=\"https://x\">the rules</a> <em>now</em></p><div>bye</div>"
        assertEquals("Read the rules *now*\nbye", commentHtmlToMarkup(html).text)
    }

    @Test
    fun `blank html is empty`() {
        assertEquals("", commentHtmlToMarkup(null).text)
        assertEquals("", commentHtmlToMarkup("  ").text)
    }

    @Test
    fun `site relative urls become absolute`() {
        assertEquals("https://comix.to/images/avatars/1/1874.webp?t=1", absoluteSiteUrl("/images/avatars/1/1874.webp?t=1"))
        assertEquals("https://cdn/x.png", absoluteSiteUrl("https://cdn/x.png"))
        assertEquals("https://cdn/x.png", absoluteSiteUrl("//cdn/x.png"))
    }

    @Test
    fun `relative dates are subtracted from now`() {
        val now = 1_786_990_000_000L
        assertEquals(now - TimeUnit.DAYS.toMillis(14), parseComixRelativeDate("2w ago", now))
        assertEquals(now - TimeUnit.HOURS.toMillis(3), parseComixRelativeDate("3h ago", now))
        assertEquals(now, parseComixRelativeDate("just now", now))
        assertTrue(parseComixRelativeDate("9mos ago", now) < now - TimeUnit.DAYS.toMillis(240))
        assertTrue(parseComixRelativeDate("1mo ago", now) < now - TimeUnit.DAYS.toMillis(27))
        assertTrue(parseComixRelativeDate("2y ago", now) < now - TimeUnit.DAYS.toMillis(700))
        assertEquals(0L, parseComixRelativeDate("yesterday-ish", now))
        assertEquals(0L, parseComixRelativeDate(null, now))
    }

    @Test
    fun `thread id is found in page html when present`() {
        assertEquals(985L, findThreadIdInPage("<div data-thread-id=\"985\"></div>"))
        assertEquals(777L, findThreadIdInPage("{\"threadId\":777,\"x\":1}"))
        assertNull(findThreadIdInPage("<html>nothing</html>"))
    }

    @Test
    fun `numeric ids are found anywhere in the initial data`() {
        val json = Json { ignoreUnknownKeys = true }
        val html = """
            <html><body><script id="initial-data" type="application/json">
            {"queries":{"a":{"result":{"hid":"emqg8","id":32026,"title":"Solo Leveling",
              "chapters":[{"id":2749754,"number":200,"volume":null},{"id":1,"number":"200.5","volume":3}]}}}}
            </script></body></html>
        """.trimIndent()
        val data = extractInitialData(html, json)!!
        assertEquals(32026L, findMangaId(data, "emqg8"))
        assertNull(findMangaId(data, "zzzz"))
        assertEquals("200" to 0, findChapterNumberAndVolume(data, 2749754))
        assertEquals("200.5" to 3, findChapterNumberAndVolume(data, 1))
        assertNull(findChapterNumberAndVolume(data, 42))
        assertNull(extractInitialData("<html></html>", json))
    }
}
