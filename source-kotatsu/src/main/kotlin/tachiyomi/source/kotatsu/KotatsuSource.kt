package tachiyomi.source.kotatsu

import eu.kanade.tachiyomi.source.model.FilterList
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * Marker implemented by every Mihon source that is backed by a Kotatsu parser.
 *
 * The app layer uses it to tell parser-backed sources apart from installed extensions (icon,
 * NSFW filtering, source mode) without depending on the adapter implementation itself.
 */
interface KotatsuSource {

    /** The parser enum entry this source wraps. */
    val parserSource: MangaParserSource

    /**
     * Whether the whole source is adult-only. Kotatsu only exposes a content type per source, not a
     * rating per manga, so this collapses to `parserSource.contentType == ContentType.HENTAI`.
     */
    val isNsfw: Boolean

    // MIKO -->
    /**
     * Static capabilities of the underlying parser (see [KotatsuCapabilities]). Instantiates the
     * parser on first access — call it off the main thread.
     */
    val capabilities: KotatsuCapabilities

    /**
     * Name of the parser enum entry, e.g. `MANGADEX`.
     *
     * Exposed as a plain [String] because the parsers library is an `implementation` dependency of
     * this module: [MangaParserSource] itself is not on the app module's compile classpath, so the
     * app can identify a parser only through this property (source info dialog, enhancement
     * matchers).
     */
    val parserName: String
        get() = parserSource.name

    /**
     * Rating the site reported for [mangaUrl] (relative `SManga.url`), normalised to `0f..1f`, or
     * null when the parser gave none or the manga was never listed/detailed in this process. Kotatsu
     * ratings are dropped when mapping to `SManga`, so this is a best-effort in-memory side channel.
     */
    fun getRemoteRating(mangaUrl: String): Float? = null

    /**
     * The same list `getFilterList()` returns, but **after** the parser's filter options (tags,
     * states, …) have been loaded — `getFilterList()` cannot suspend, so its first call hands back
     * a stub while the options load in the background. Use this from code that needs the real tag
     * filters right away (C14 Tagu Osekkai). A failed load yields the sort-only list.
     */
    suspend fun awaitFilterList(): FilterList
    // MIKO <--
}
