package eu.kanade.tachiyomi.source.enhancement.comix

/*
 * MIKO — pure helpers that turn what the Keiyoushi Comix extension stores into what comix.to's
 * thread API expects. Network-free; unit tested in `ComixUrlsTest`.
 *
 * The extension stores `SManga.url` as `/<hid>-<slug>` (the part after `/title`) and
 * `SChapter.url` as `title/<hid>-<slug>/<chapterId>-chapter-<n>` (no leading slash), but older
 * entries and deep links can be absolute (`https://comix.to/title/…`, mirror `comix.ws`) or carry a
 * query/fragment, so everything is normalised to a site-relative page path first.
 */

private const val TITLE_PREFIX = "/title/"
private val SCHEME_HOST = Regex("^[a-z][a-z0-9+.-]*://[^/]+", RegexOption.IGNORE_CASE)
private val CHAPTER_SEGMENT = Regex("""^(\d+)-chapter-([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
private val LEADING_ID = Regex("""^(\d+)-""")

/** `/title/<hid>-<slug>` for a stored manga url, or null when nothing usable is in there. */
fun seriesPagePathOf(mangaUrl: String): String? {
    val path = normalisePath(mangaUrl) ?: return null
    // Whatever comes after /title/, keep only the series segment.
    val series = path.removePrefix(TITLE_PREFIX).substringBefore('/').takeIf { it.isNotBlank() } ?: return null
    return TITLE_PREFIX + series
}

/** `/title/<hid>-<slug>/<chapterId>-chapter-<n>` for a stored chapter url, or null. */
fun chapterPagePathOf(chapterUrl: String): String? {
    val path = normalisePath(chapterUrl) ?: return null
    val rest = path.removePrefix(TITLE_PREFIX)
    val series = rest.substringBefore('/').takeIf { it.isNotBlank() } ?: return null
    val chapter = rest.substringAfter('/', "").substringBefore('/').takeIf { it.isNotBlank() } ?: return null
    return TITLE_PREFIX + series + "/" + chapter
}

/** The series hash id (`emqg8`) of a `/title/<hid>-<slug>[/…]` path. */
fun hidOf(pagePath: String): String? =
    pagePath.removePrefix(TITLE_PREFIX).substringBefore('/').substringBefore('-').takeIf { it.isNotBlank() }

/** The numeric chapter id (`2749754`) of a `/title/<hid>-<slug>/<id>-chapter-<n>` path. */
fun chapterIdOf(chapterPagePath: String): Long? =
    LEADING_ID.find(chapterPagePath.substringAfterLast('/'))?.groupValues?.get(1)?.toLongOrNull()

/** The chapter number as the site prints it (`"200"`, `"200.5"`) of a chapter page path, or null. */
fun chapterNumberOf(chapterPagePath: String): String? =
    CHAPTER_SEGMENT.find(chapterPagePath.substringAfterLast('/'))?.groupValues?.get(2)

/** `manga32026` — the thread `page_identifier` of a series. */
fun seriesPageIdentifier(mangaId: Long): String = "manga$mangaId"

/** `manga32026_chap200_vol0` — the thread `page_identifier` of a chapter. */
fun chapterPageIdentifier(mangaId: Long, chapterNumber: String, volume: Int): String =
    "manga${mangaId}_chap${normaliseNumber(chapterNumber)}_vol$volume"

/** `"200.0"` → `"200"`, `"200.50"` → `"200.5"`; anything unparseable is kept verbatim. */
internal fun normaliseNumber(number: String): String {
    val value = number.trim().toDoubleOrNull() ?: return number.trim()
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')
}

/**
 * Strips scheme+host, query and fragment, forces a leading slash and the `/title/` prefix. Returns
 * null for blank input.
 */
private fun normalisePath(url: String): String? {
    var path = url.trim()
    if (path.isEmpty()) return null
    path = path.replace(SCHEME_HOST, "")
    path = path.substringBefore('#').substringBefore('?')
    if (!path.startsWith("/")) path = "/$path"
    if (!path.startsWith(TITLE_PREFIX)) path = TITLE_PREFIX.dropLast(1) + path
    return path.trimEnd('/').takeIf { it.length > TITLE_PREFIX.length }
}
