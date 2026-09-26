package eu.kanade.tachiyomi.ui.deeplink

import android.net.Uri
import java.util.Base64

/**
 * MIKO — Miko-native deep link to a manga entry.
 *
 * Ported from Taison's `TaisonEntryLink` (Apache-2.0, same licence as the Mihon base). Miko
 * owns no domain, so only the custom-scheme form exists — `miko://entry?<params>`. There is no
 * App Link / `autoVerify` filter and no web landing page, hence no `pu` (public URL) parameter
 * either.
 *
 * The point of the link is to resolve an entry **without** requiring the source to implement
 * `ResolvableSource` — none of the built-in Kotatsu parsers do — and to render a faithful preview
 * without a network round trip.
 *
 * Wire format (`miko://entry?s=…&t=…&u=…`):
 * - `s`  source id (required, decimal).
 * - `t`  title (required, URL-encoded UTF-8).
 * - `u`  raw `SManga.url` (required, URL-safe Base64 without padding).
 * - `n`  source name, `l` source lang, `a` author (truncated to 80 chars), `g` first 5 genres
 *   joined by `", "` — all URL-encoded UTF-8.
 * - `v`  source version id, `st` status — decimal.
 * - `cu` raw `SChapter.url` (URL-safe Base64 without padding).
 *
 * `u`/`cu` carry the raw source-relative identifiers — the values `getMangaUpdate`,
 * `getChapterList` and `NetworkToLocalManga` expect — and not the public https URLs, because
 * `HttpSource.getMangaUrl()` is free to canonicalize and does not reliably round-trip back to
 * `SManga.url`.
 *
 * Two lossy normalizations are applied by the sender on purpose, to keep the link chat-safe:
 * the author is truncated to 80 characters, and only the first 5 genres survive. Genres are joined
 * by commas, so a genre that itself contains a comma is split in two on the receiving side.
 *
 * All field logic lives in the pure [toQueryParams] / [Companion.parseQuery] pair over a plain
 * `Map<String, String>`, so it is unit-testable on the JVM; [toUri] / [Companion.parse] are thin
 * `android.net.Uri` wrappers.
 */
data class MikoEntryLink(
    val sourceId: Long,
    val title: String,
    val mangaUrl: String,
    val sourceName: String? = null,
    val lang: String? = null,
    val versionId: Int? = null,
    val chapterUrl: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
    val status: Long? = null,
) {

    fun toUri(): Uri {
        val builder = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
        toQueryParams().forEach { (name, value) -> builder.appendQueryParameter(name, value) }
        return builder.build()
    }

    internal fun toQueryParams(): Map<String, String> = buildMap {
        put(PARAM_SOURCE_ID, sourceId.toString())
        put(PARAM_TITLE, title)
        put(PARAM_URL, encodeB64(mangaUrl))
        sourceName?.takeIf { it.isNotBlank() }?.let { put(PARAM_SOURCE_NAME, it) }
        lang?.takeIf { it.isNotBlank() }?.let { put(PARAM_LANG, it) }
        versionId?.let { put(PARAM_VERSION_ID, it.toString()) }
        chapterUrl?.takeIf { it.isNotEmpty() }?.let { put(PARAM_CHAPTER_URL, encodeB64(it)) }
        author?.takeIf { it.isNotBlank() }?.let { put(PARAM_AUTHOR, truncate(it, AUTHOR_MAX)) }
        joinGenres(genres)?.let { put(PARAM_GENRES, it) }
        status?.takeIf { it != 0L }?.let { put(PARAM_STATUS, it.toString()) }
    }

    companion object {
        const val SCHEME = "miko"
        const val HOST = "entry"

        private const val PARAM_SOURCE_ID = "s"
        private const val PARAM_TITLE = "t"
        private const val PARAM_URL = "u"
        private const val PARAM_SOURCE_NAME = "n"
        private const val PARAM_LANG = "l"
        private const val PARAM_VERSION_ID = "v"
        private const val PARAM_CHAPTER_URL = "cu"
        private const val PARAM_AUTHOR = "a"
        private const val PARAM_GENRES = "g"
        private const val PARAM_STATUS = "st"

        private const val AUTHOR_MAX = 80
        private const val GENRES_MAX = 5

        fun matches(uri: Uri): Boolean {
            return uri.scheme.equals(SCHEME, ignoreCase = true) && uri.host.equals(HOST, ignoreCase = true)
        }

        fun parse(uri: Uri): MikoEntryLink? {
            if (!matches(uri)) return null
            val params = runCatching {
                uri.queryParameterNames
                    .mapNotNull { name -> uri.getQueryParameter(name)?.let { name to it } }
                    .toMap()
            }.getOrNull() ?: return null
            return parseQuery(params)
        }

        internal fun parseQuery(params: Map<String, String>): MikoEntryLink? {
            val sourceId = params[PARAM_SOURCE_ID]?.toLongOrNull() ?: return null
            val title = params[PARAM_TITLE]?.takeIf { it.isNotBlank() } ?: return null
            val mangaUrl = params[PARAM_URL]?.let(::decodeB64)?.takeIf { it.isNotEmpty() } ?: return null
            return MikoEntryLink(
                sourceId = sourceId,
                title = title,
                mangaUrl = mangaUrl,
                sourceName = params[PARAM_SOURCE_NAME]?.takeIf { it.isNotBlank() },
                lang = params[PARAM_LANG]?.takeIf { it.isNotBlank() },
                versionId = params[PARAM_VERSION_ID]?.toIntOrNull(),
                chapterUrl = params[PARAM_CHAPTER_URL]?.let(::decodeB64)?.takeIf { it.isNotEmpty() },
                author = params[PARAM_AUTHOR]?.takeIf { it.isNotBlank() },
                genres = params[PARAM_GENRES]?.let(::splitGenres).orEmpty(),
                status = params[PARAM_STATUS]?.toLongOrNull(),
            )
        }

        private fun encodeB64(value: String): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        }

        private fun decodeB64(value: String): String? {
            return try {
                String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun truncate(value: String, max: Int): String {
            return if (value.length <= max) value else value.take(max).trimEnd() + "…"
        }

        private fun joinGenres(genres: List<String>): String? {
            return genres
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(GENRES_MAX)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        }

        private fun splitGenres(raw: String): List<String> {
            return raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
