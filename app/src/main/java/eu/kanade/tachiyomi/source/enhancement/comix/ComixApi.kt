package eu.kanade.tachiyomi.source.enhancement.comix

import eu.kanade.domain.source.enhancement.CommentsSort
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.decodeFromJsonResponse
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
 * MIKO — read-only client for the comment threads of comix.to.
 *
 * Everything lives under `https://comix.to/api/v1/threads`: `lookup` maps a page to its thread id,
 * `{id}/comments` lists a thread (top-level comments with a preview of their replies) or, with
 * `parent_id`, the remaining replies of one comment. Pagination is cursor-based (`cursor` echoed
 * by the previous page). None of it is request-signed, unlike `/api/v1/manga`, but the whole host
 * sits behind Cloudflare: the client is derived from the app's, so Mihon's `CloudflareInterceptor`
 * clears the challenge (once) and the shared cookie jar keeps `cf_clearance` for the extension and
 * for us alike. Rate limited to 2 requests / 2 s.
 *
 * The site's page HTML is fetched only as a fallback ([getPageHtml]) when a thread cannot be
 * looked up by URL alone.
 */
class ComixApi {

    private val json: Json by injectLazy()

    private val client by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .rateLimit(2, 2.seconds)
            .build()
    }

    private val apiHeaders by lazy {
        Headers.Builder()
            .add("Accept", "application/json")
            .add("Referer", "$COMIX_SITE_URL/")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("User-Agent", Injekt.get<NetworkHelper>().defaultUserAgentProvider())
            .build()
    }

    private val pageHeaders by lazy {
        Headers.Builder()
            .add("Accept", "text/html,application/xhtml+xml")
            .add("Referer", "$COMIX_SITE_URL/")
            .add("User-Agent", Injekt.get<NetworkHelper>().defaultUserAgentProvider())
            .build()
    }

    /**
     * The thread behind [pagePath] (`/title/emqg8-solo-leveling[/2749754-chapter-200]`), optionally
     * disambiguated by [pageIdentifier] (`manga32026`, `manga32026_chap200_vol0`). Null when the
     * site answers 4xx (unknown page / missing identifier); other failures propagate.
     */
    suspend fun lookupThread(pagePath: String, pageIdentifier: String? = null): ComixThreadDto? {
        val url = "$API_URL/threads/lookup".toHttpUrl().newBuilder().apply {
            if (pageIdentifier != null) addQueryParameter("page_identifier", pageIdentifier)
            addQueryParameter("page_url", pagePath)
        }.build()
        return try {
            get(url).result?.thread
        } catch (e: HttpException) {
            if (e.code in CLIENT_ERRORS) null else throw e
        }
    }

    /**
     * One page of a thread: top-level comments (with [parentId] null) or the replies of [parentId]
     * that were not shipped inline. [cursor] is the value the previous page returned; null for the
     * first page.
     */
    suspend fun getComments(
        threadId: Long,
        sort: CommentsSort,
        cursor: String? = null,
        parentId: Long? = null,
    ): ComixThreadResultDto {
        val url = "$API_URL/threads/$threadId/comments".toHttpUrl().newBuilder().apply {
            if (parentId != null) addQueryParameter("parent_id", parentId.toString())
            if (parentId != null || !cursor.isNullOrBlank()) addQueryParameter("cursor", cursor.orEmpty())
            addQueryParameter("sort", sort.apiValue)
        }.build()
        return get(url).result ?: ComixThreadResultDto()
    }

    /** Raw HTML of a comix.to page, for the id/thread scraping fallbacks. */
    suspend fun getPageHtml(pagePath: String): String = withIOContext {
        client.newCall(GET(COMIX_SITE_URL + pagePath, pageHeaders, CACHE_CONTROL)).awaitSuccess()
            .use { it.body.string() }
    }

    private suspend fun get(url: HttpUrl): ComixThreadResponseDto = withIOContext {
        client.newCall(GET(url, apiHeaders, CACHE_CONTROL)).awaitSuccess().use { response ->
            with(json) { decodeFromJsonResponse(ComixThreadResponseDto.serializer(), response) }
        }
    }

    companion object {
        /** The single instance every Comix enhancement shares. */
        val instance: ComixApi by lazy { ComixApi() }

        private const val API_URL = "$COMIX_SITE_URL/api/v1"

        /** Statuses that mean "no such thread / bad lookup", not "the site is down". */
        private val CLIENT_ERRORS = setOf(400, 404, 422)

        /** Comment threads change constantly; never serve them from OkHttp's disk cache. */
        private val CACHE_CONTROL = CacheControl.FORCE_NETWORK

        private val CommentsSort.apiValue: String
            get() = when (this) {
                CommentsSort.TOP -> "best"
                CommentsSort.NEWEST -> "newest"
            }
    }
}
