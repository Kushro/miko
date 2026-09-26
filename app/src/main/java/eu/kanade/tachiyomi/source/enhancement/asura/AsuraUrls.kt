package eu.kanade.tachiyomi.source.enhancement.asura

/*
 * MIKO — pure URL helpers for the Asura Scans enhancement.
 *
 * The Keiyoushi extension stores `SManga.url` in one of several shapes depending on when the entry
 * was added (`/series/<slug>` today, `/comics/<slug>-xxxxxxxx` from a deep link, legacy
 * `/manga/(\d+-)?<slug>`), and `SChapter.url` as `/series/<slug>/chapter/<n>`. Everything here is
 * side-effect free and network-free so it can be unit tested (`AsuraUrlsTest`).
 */

/** Random 8-char suffix the site appends to public series URLs (`…-b60d532c`). */
private val RANDOM_SUFFIX_REGEX = Regex("""-[a-z0-9]{8}$""")

/** Numeric id prefix of the legacy `/manga/123-<slug>` format. */
private val LEGACY_ID_PREFIX_REGEX = Regex("""^\d+-""")

/** Plain chapter numbers, as the site writes them: `197`, `197.5`. */
private val CHAPTER_NUMBER_REGEX = Regex("""^\d+(\.\d+)?$""")

/** Path segments that introduce a series slug, in the formats the extension may have stored. */
private val SERIES_SEGMENTS = setOf("series", "comics", "manga")

/**
 * The series slug carried by [mangaUrl], or null when none can be found.
 *
 * Accepts relative paths and absolute URLs, with or without query/fragment. The slug is returned
 * **verbatim**: a slug that legitimately ends in eight alphanumerics is not touched here — only
 * [stripRandomSuffix] removes a suffix, and only as a retry when the API rejects the slug.
 */
fun seriesSlugOf(mangaUrl: String): String? {
    val segments = pathSegmentsOf(mangaUrl)
    if (segments.isEmpty()) return null

    // Prefer the segment right after series/comics/manga, so a chapter URL still yields the series.
    val containerIndex = segments.indexOfLast { it in SERIES_SEGMENTS }
    val raw = when {
        containerIndex >= 0 && containerIndex < segments.lastIndex -> segments[containerIndex + 1]
        else -> segments.last()
    }

    val slug = if (containerIndex >= 0 && segments[containerIndex] == "manga") {
        LEGACY_ID_PREFIX_REGEX.replace(raw, "")
    } else {
        raw
    }

    return slug.takeIf { it.isNotEmpty() && it !in SERIES_SEGMENTS }
}

/** Drops the site's random `-[a-z0-9]{8}` suffix, or returns [slug] unchanged when there is none. */
fun stripRandomSuffix(slug: String): String = RANDOM_SUFFIX_REGEX.replace(slug, "")

/**
 * The chapter number encoded in [chapterUrl] (`/series/<slug>/chapter/197.5` → `197.5f`), or null
 * when the URL has no `/chapter/<n>` part or `<n>` is not a plain number.
 */
fun chapterNumberOf(chapterUrl: String): Float? {
    val path = pathOf(chapterUrl)
    if (!path.contains("/chapter/")) return null
    val raw = path.substringAfterLast("/chapter/").substringBefore('/').trim()
    if (!CHAPTER_NUMBER_REGEX.matches(raw)) return null
    return raw.toFloatOrNull()
}

/** [url] reduced to its path, always starting with `/`, without scheme/host/query/fragment. */
private fun pathOf(url: String): String {
    val withoutFragment = url.substringBefore('#').substringBefore('?')
    val withoutHost = if (withoutFragment.contains("://")) {
        withoutFragment.substringAfter("://").substringAfter('/', "")
    } else {
        withoutFragment
    }
    return "/" + withoutHost.trim('/')
}

private fun pathSegmentsOf(url: String): List<String> =
    pathOf(url).split('/').filter { it.isNotEmpty() }
