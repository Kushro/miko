package eu.kanade.tachiyomi.source.enhancement.asura

import eu.kanade.domain.source.enhancement.CommentsSort
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.decodeFromJsonResponse
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds

/**
 * MIKO — read-only client for Asura Scans' public JSON API, backing the site-rating and comments
 * enhancements (see [AsuraScansRatingEnhancement], [AsuraScansCommentsEnhancement]).
 *
 * No auth and no special headers beyond `Referer` + the app's User-Agent; Cloudflare sits in front
 * of the site but does not challenge plain GETs. Requests are rate limited to 2 per 2 s on a client
 * derived from the app's, so the enhancement can never starve the extension itself.
 *
 * Two in-memory caches keep the extra round-trips down: the series lookup (needed for its numeric
 * id and its rating) is cached in a small LRU for the whole session, and the chapter list — which
 * grows — for ten minutes. Both are keyed by slug; [invalidateChapters] drops one entry so a brand
 * new chapter can be resolved right away.
 *
 * Everything runs on the IO dispatcher. Network failures propagate (as `IOException` from OkHttp);
 * a "not found" answer is reported as `null`/empty instead, see [getSeries] and [getChapters].
 */
class AsuraScansApi {

    private val json: Json by injectLazy()

    private val client by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .rateLimit(2, 2.seconds)
            .build()
    }

    private val headers by lazy {
        Headers.Builder()
            .add("Referer", "$SITE_URL/")
            .add("User-Agent", Injekt.get<NetworkHelper>().defaultUserAgentProvider())
            .build()
    }

    /** Slug (as requested and as resolved) → series. Guarded by its own monitor. */
    private val seriesCache = object : LinkedHashMap<String, AsuraSeriesDto>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AsuraSeriesDto>?): Boolean =
            size > SERIES_CACHE_SIZE
    }

    /** Slug → chapter list with the time it was fetched. Bounded LRU, guarded by its own monitor. */
    private val chaptersCache = object : LinkedHashMap<String, CachedChapters>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedChapters>?): Boolean =
            size > CHAPTERS_CACHE_SIZE
    }

    /**
     * The series behind [slug], or null when Asura Scans does not know it.
     *
     * The slug may carry the site's random suffix; the API usually accepts it either way, and when
     * it does not the call is retried once with the suffix removed.
     */
    suspend fun getSeries(slug: String): AsuraSeriesDto? {
        cachedSeries(slug)?.let { return it }

        val series = getWithSlugFallback(slug, AsuraSeriesResponseDto.serializer()) {
            "$API_URL/series/$it".toHttpUrl()
        }?.series ?: return null

        synchronized(seriesCache) {
            seriesCache[slug] = series
            series.slug?.takeIf { it.isNotBlank() }?.let { seriesCache[it] = series }
        }
        return series
    }

    /**
     * Every chapter of [slug], newest first, or an empty list when the series is unknown. Cached
     * for [CHAPTERS_CACHE_TTL_MS] ms; empty answers are not cached.
     */
    suspend fun getChapters(slug: String): List<AsuraChapterDto> {
        cachedChapters(slug)?.let { return it }

        val chapters = getWithSlugFallback(slug, AsuraChaptersResponseDto.serializer()) {
            "$API_URL/series/$it/chapters".toHttpUrl()
        }?.data.orEmpty()

        if (chapters.isNotEmpty()) {
            synchronized(chaptersCache) {
                chaptersCache[slug] = CachedChapters(System.currentTimeMillis(), chapters)
            }
        }
        return chapters
    }

    /** Forgets the cached chapter list of [slug], so the next [getChapters] hits the network. */
    fun invalidateChapters(slug: String) {
        synchronized(chaptersCache) { chaptersCache.remove(slug) }
    }

    /** Whether the next [getChapters] for [slug] would be served from the (unexpired) cache. */
    fun hasCachedChapters(slug: String): Boolean = cachedChapters(slug) != null

    /** Comments of a series. [seriesId] is the internal numeric id from [getSeries], not the slug. */
    suspend fun getSeriesComments(seriesId: Long, page: Int, sort: CommentsSort): AsuraCommentsPageDto =
        get(commentsUrl("$API_URL/series/$seriesId/comments", page, sort), AsuraCommentsPageDto.serializer())

    /** Comments of a chapter. [chapterId] is the internal numeric id, not the chapter number. */
    suspend fun getChapterComments(chapterId: Long, page: Int, sort: CommentsSort): AsuraCommentsPageDto =
        get(commentsUrl("$API_URL/chapters/$chapterId/comments", page, sort), AsuraCommentsPageDto.serializer())

    /** Full reply list of a comment, for the rare thread that does not ship them inline. */
    suspend fun getReplies(commentId: Long): List<AsuraCommentDto> =
        get("$API_URL/comments/$commentId/replies".toHttpUrl(), ListSerializer(AsuraCommentDto.serializer()))

    // --- internals -----------------------------------------------------------------------------

    private fun cachedSeries(slug: String): AsuraSeriesDto? = synchronized(seriesCache) { seriesCache[slug] }

    private fun cachedChapters(slug: String): List<AsuraChapterDto>? = synchronized(chaptersCache) {
        val cached = chaptersCache[slug] ?: return@synchronized null
        if (System.currentTimeMillis() - cached.fetchedAt > CHAPTERS_CACHE_TTL_MS) {
            chaptersCache.remove(slug)
            null
        } else {
            cached.chapters
        }
    }

    /**
     * Runs [url] built from [slug] and, when the site answers **404**, retries once with the random
     * suffix stripped. Returns null when both attempts are 404; any other HTTP status, network error
     * or malformed payload propagates, so a transient 5xx/429/403 is never reported as "not found".
     */
    private suspend fun <T> getWithSlugFallback(
        slug: String,
        deserializer: DeserializationStrategy<T>,
        url: (String) -> HttpUrl,
    ): T? {
        try {
            return get(url(slug), deserializer)
        } catch (e: HttpException) {
            if (e.code != HTTP_NOT_FOUND) throw e
            // Falls through to the retry below.
        }

        val fallbackSlug = stripRandomSuffix(slug)
        if (fallbackSlug == slug || fallbackSlug.isEmpty()) return null

        return try {
            get(url(fallbackSlug), deserializer)
        } catch (e: HttpException) {
            if (e.code != HTTP_NOT_FOUND) throw e
            null
        }
    }

    private suspend fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): T = withIOContext {
        client.newCall(GET(url, headers, CACHE_CONTROL)).awaitSuccess().use { response ->
            with(json) { decodeFromJsonResponse(deserializer, response) }
        }
    }

    private fun commentsUrl(base: String, page: Int, sort: CommentsSort): HttpUrl =
        base.toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort.apiValue)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", (((page - 1).coerceAtLeast(0)) * PAGE_SIZE).toString())
            .build()

    private class CachedChapters(val fetchedAt: Long, val chapters: List<AsuraChapterDto>)

    companion object {
        /** The single instance every Asura enhancement shares, so they share the caches too. */
        val instance: AsuraScansApi by lazy { AsuraScansApi() }

        const val SITE_URL = "https://asurascans.com"
        private const val API_URL = "https://api.asurascans.com/api"

        /** The site's own page size; the provider contract lets us pick it. */
        const val PAGE_SIZE = 20

        private const val SERIES_CACHE_SIZE = 64
        private const val CHAPTERS_CACHE_SIZE = 32
        private const val CHAPTERS_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val HTTP_NOT_FOUND = 404

        /**
         * Comment threads must not be served from OkHttp's disk cache — a pull-to-refresh has to
         * show new comments — and the series/chapter answers have their own in-memory caches above.
         */
        private val CACHE_CONTROL = CacheControl.FORCE_NETWORK

        private val CommentsSort.apiValue: String
            get() = when (this) {
                CommentsSort.TOP -> "top"
                CommentsSort.NEWEST -> "newest"
            }
    }
}
