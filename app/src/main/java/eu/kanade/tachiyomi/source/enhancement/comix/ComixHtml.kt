package eu.kanade.tachiyomi.source.enhancement.comix

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Calendar

/*
 * MIKO — text-level helpers for the Comix enhancement: the comment HTML → the lightweight markup
 * every comments UI in Miko renders, the site's relative dates, and the bits of a comix.to page we
 * need when the thread lookup by URL is not enough. Pure and network-free (`ComixHtmlTest`).
 */

const val COMIX_SITE_URL = "https://comix.to"

/** A comment body converted from HTML: markup text plus the images that were embedded in it. */
data class ComixParsedContent(
    val text: String,
    val imageUrls: List<String>,
)

private val WHITESPACE = Regex("""\s+""")
private val SPACES = Regex("""[ \t]{2,}""")
private val BLANK_LINES = Regex("""\n{3,}""")

/**
 * `contentHtml` → markup. Line breaks come only from `<br>`/block elements (literal newlines in the
 * source are HTML whitespace); `<b>`/`<strong>` → `**…**`, `<i>`/`<em>` → `*…*`,
 * `<s>`/`<del>`/`<strike>` → `~~…~~`, `<span class="spoil">` → `||…||`, `<img>` is pulled out into
 * [ComixParsedContent.imageUrls], `<a>` keeps its text, entities are decoded, everything else is
 * unwrapped.
 */
fun commentHtmlToMarkup(html: String?): ComixParsedContent {
    if (html.isNullOrBlank()) return ComixParsedContent("", emptyList())
    val body = Jsoup.parseBodyFragment(html).body()
    val out = StringBuilder()
    val images = LinkedHashSet<String>()

    fun render(node: Node) {
        when (node) {
            is TextNode -> out.append(node.wholeText.replace(WHITESPACE, " "))
            is Element -> when (node.normalName()) {
                "br" -> out.append('\n')
                "img" -> node.absUrl("src").ifBlank { node.attr("src") }.takeIf { it.isNotBlank() }?.let(images::add)
                "b", "strong" -> wrap(out, "**", node, ::render)
                "i", "em" -> wrap(out, "*", node, ::render)
                "s", "del", "strike" -> wrap(out, "~~", node, ::render)
                "span" -> if (node.hasClass("spoil") || node.hasClass("spoiler")) {
                    wrap(out, "||", node, ::render)
                } else {
                    node.childNodes().forEach(::render)
                }
                "p", "div", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    node.childNodes().forEach(::render)
                    out.append('\n')
                }
                else -> node.childNodes().forEach(::render)
            }
        }
    }
    body.childNodes().forEach(::render)

    val text = out.toString()
        .lines()
        .joinToString("\n") { it.trim().replace(SPACES, " ") }
        .replace(BLANK_LINES, "\n\n")
        .trim()
    return ComixParsedContent(text, images.map(::absoluteSiteUrl))
}

/** Emits `<marker>…<marker>` around the rendered children, skipping the markers when they are empty. */
private inline fun wrap(out: StringBuilder, marker: String, node: Element, render: (Node) -> Unit) {
    val start = out.length
    node.childNodes().forEach(render)
    val inner = out.substring(start)
    if (inner.isBlank()) return
    out.setLength(start)
    // Markup delimiters must hug the content: "** x **" would not be recognised.
    val leading = inner.takeWhile { it.isWhitespace() }
    val trailing = inner.takeLastWhile { it.isWhitespace() }
    out.append(leading).append(marker).append(inner.trim()).append(marker).append(trailing)
}

/** `/images/avatars/1/1874.webp` → `https://comix.to/images/avatars/1/1874.webp`; absolute urls pass through. */
fun absoluteSiteUrl(url: String): String = when {
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
    url.startsWith("//") -> "https:$url"
    url.startsWith("/") -> COMIX_SITE_URL + url
    else -> "$COMIX_SITE_URL/$url"
}

private val RELATIVE_DATE = Regex(
    """^(\d+)\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|""" +
        """w|wk|wks|week|weeks|mo|mos|month|months|y|yr|yrs|year|years)\s*ago$""",
)

/**
 * `"9mos ago"`, `"2w ago"`, `"just now"` → approximate epoch millis relative to [now]; 0 (unknown)
 * for anything else. The site never sends an absolute timestamp for comments.
 */
fun parseComixRelativeDate(label: String?, now: Long = System.currentTimeMillis()): Long {
    val value = label?.trim()?.lowercase() ?: return 0L
    if (value.isEmpty()) return 0L
    if (value == "just now" || value == "now") return now
    val match = RELATIVE_DATE.find(value) ?: return 0L
    val amount = match.groupValues[1].toIntOrNull() ?: return 0L
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    when (match.groupValues[2]) {
        "s", "sec", "secs", "second", "seconds" -> calendar.add(Calendar.SECOND, -amount)
        "m", "min", "mins", "minute", "minutes" -> calendar.add(Calendar.MINUTE, -amount)
        "h", "hr", "hrs", "hour", "hours" -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
        "d", "day", "days" -> calendar.add(Calendar.DAY_OF_YEAR, -amount)
        "w", "wk", "wks", "week", "weeks" -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
        "mo", "mos", "month", "months" -> calendar.add(Calendar.MONTH, -amount)
        "y", "yr", "yrs", "year", "years" -> calendar.add(Calendar.YEAR, -amount)
    }
    return calendar.timeInMillis
}

// --- page scraping fallbacks -----------------------------------------------------------------

private val THREAD_ID_IN_PAGE = Regex("""(?:threadId|thread_id|data-thread-id)["'\s:=]+(\d+)""")

/** A thread id if the page HTML happens to carry one (`"threadId":985`), else null. */
fun findThreadIdInPage(html: String): Long? = THREAD_ID_IN_PAGE.find(html)?.groupValues?.get(1)?.toLongOrNull()

/** The server-side `<script id="initial-data">` JSON of a comix.to page, or null. */
fun extractInitialData(html: String, json: Json): JsonElement? {
    val raw = Jsoup.parse(html).selectFirst("script#initial-data")?.data()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { json.parseToJsonElement(raw) }.getOrNull()
}

/** The numeric id (`32026`) of the manga whose `hid` is [hid], searched anywhere in [root]. */
fun findMangaId(root: JsonElement, hid: String): Long? =
    root.findObject { obj -> obj.string("hid") == hid && obj.long("id") != null }?.long("id")

/** `number` (as printed) and `volume` of the chapter with numeric id [chapterId], searched anywhere in [root]. */
fun findChapterNumberAndVolume(root: JsonElement, chapterId: Long): Pair<String, Int>? {
    val chapter = root.findObject { obj -> obj.long("id") == chapterId && obj.containsKey("number") } ?: return null
    val number = (chapter["number"] as? JsonPrimitive)?.let { it.contentOrNull ?: it.doubleOrNull?.toString() }
        ?: return null
    val volume = (chapter["volume"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
    return normaliseNumber(number) to volume
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

/** Depth-first search for the first object matching [predicate]. */
private fun JsonElement.findObject(predicate: (JsonObject) -> Boolean): JsonObject? {
    val stack = ArrayDeque<JsonElement>().apply { add(this@findObject) }
    while (stack.isNotEmpty()) {
        when (val element = stack.removeLast()) {
            is JsonObject -> {
                if (predicate(element)) return element
                element.values.forEach { if (it is JsonObject || it is JsonArray) stack.add(it) }
            }
            is JsonArray -> element.forEach { if (it is JsonObject || it is JsonArray) stack.add(it) }
            else -> Unit
        }
    }
    return null
}
