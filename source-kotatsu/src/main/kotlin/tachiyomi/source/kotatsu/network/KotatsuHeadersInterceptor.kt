// Ported from Kotatsu-Redo (GPL-3.0): core/network/CommonHeadersInterceptor.kt + core/network/CommonHeaders.kt
package tachiyomi.source.kotatsu.network

import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.mergeWith
import tachiyomi.core.common.util.system.logcat
import java.net.IDN

/**
 * Single entry point through which every request made on behalf of a Kotatsu parser is decorated.
 *
 * Kotatsu's design is that a parser *is* an OkHttp [Interceptor]: it signs requests, refreshes
 * tokens and rewrites image urls from inside `intercept`. Registering ~1200 interceptors is not an
 * option, so — exactly like the reference host does — one interceptor resolves the parser for the
 * request and calls its `intercept` at the end of the chain.
 *
 * The parser is resolved from either the `MangaSource` request tag or the [HEADER_SOURCE] header,
 * which is stripped before the request leaves. The header exists because Mihon builds most requests
 * through `HttpSource.headers`, where a tag cannot be attached.
 */
class KotatsuHeadersInterceptor(
    private val parserResolver: (MangaSource) -> MangaParser?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val source: MangaSource? = request.tag(MangaSource::class.java)
            ?: request.header(HEADER_SOURCE)?.let(::NamedMangaSource)
        val parser = source?.let { runCatching { parserResolver(it) }.getOrNull() }

        val headersBuilder = request.headers.newBuilder().removeAll(HEADER_SOURCE)
        if (parser != null) {
            // The parser's own headers never override what the caller already set explicitly.
            runCatching { parser.getRequestHeaders() }
                .onSuccess { headersBuilder.mergeWith(it, replaceExisting = false) }
                .onFailure { error ->
                    logcat(LogPriority.WARN, error) { "Cannot read the request headers of ${parser.source.name}" }
                }
            if (headersBuilder[HEADER_REFERER] == null) {
                // `domain` reads the source config, which can be missing for a freshly added source.
                runCatching { parser.domain }.onSuccess { domain ->
                    headersBuilder.trySet(HEADER_REFERER, "https://${domain.toAsciiDomain()}/")
                }
            }
        }

        val newRequest = request.newBuilder().headers(headersBuilder.build()).build()
        return if (parser == null) {
            chain.proceed(newRequest)
        } else {
            parser.interceptSafe(ProxyChain(chain, newRequest))
        }
    }

    private fun Headers.Builder.trySet(name: String, value: String) {
        try {
            set(name, value)
        } catch (e: IllegalArgumentException) {
            logcat(LogPriority.WARN, e) { "Cannot set header $name" }
        }
    }

    private fun String.toAsciiDomain(): String = try {
        IDN.toASCII(this)
    } catch (_: IllegalArgumentException) {
        this
    }

    /** Only an [IOException] can safely cross an OkHttp interceptor boundary. */
    private fun Interceptor.interceptSafe(chain: Interceptor.Chain): Response = try {
        intercept(chain)
    } catch (e: IOException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IOException("Error in the parser interceptor: ${e.message}", e)
    }

    /**
     * Minimal [MangaSource] used to look a parser up by name when the request carries the
     * [HEADER_SOURCE] header instead of a tag. Only [name] is ever read by the resolver.
     */
    private data class NamedMangaSource(override val name: String) : MangaSource

    private class ProxyChain(
        private val delegate: Interceptor.Chain,
        private val request: Request,
    ) : Interceptor.Chain by delegate {

        override fun request(): Request = request
    }

    companion object {

        /** Carries a `MangaParserSource.name` on requests that cannot carry a tag. */
        const val HEADER_SOURCE = "X-Kotatsu-Source"

        private const val HEADER_REFERER = "Referer"
    }
}
