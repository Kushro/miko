// Ported from Kotatsu-Redo (GPL-3.0): core/network/RateLimitInterceptor.kt
package tachiyomi.source.kotatsu.network

import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * Turns a `429 Too Many Requests` into the exception Kotatsu parsers (and the adapter's error
 * mapper) expect, carrying the `Retry-After` delay when the server sends one.
 */
class KotatsuRateLimitInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HTTP_TOO_MANY_REQUESTS) {
            return response
        }
        val url = response.request.url.toString()
        val retryAfter = response.header(HEADER_RETRY_AFTER)?.parseRetryAfter() ?: 0L
        response.close()
        throw TooManyRequestExceptions(url = url, retryAfter = retryAfter)
    }

    /** `Retry-After` is either a delay in seconds or an RFC 1123 date. Returns milliseconds. */
    private fun String.parseRetryAfter(): Long? {
        trim().toLongOrNull()?.let { return TimeUnit.SECONDS.toMillis(it) }
        return try {
            val at = ZonedDateTime.parse(trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            (at - System.currentTimeMillis()).coerceAtLeast(0L)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HEADER_RETRY_AFTER = "Retry-After"
    }
}
