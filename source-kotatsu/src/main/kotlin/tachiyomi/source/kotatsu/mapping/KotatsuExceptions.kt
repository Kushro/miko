// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu.mapping

import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ContentUnavailableException
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import tachiyomi.source.kotatsu.exception.KotatsuInteractiveActionRequiredException

/**
 * Translates the exceptions raised by the parsers library into messages Mihon's UI can show as-is.
 *
 * The app has no knowledge of the Kotatsu exception hierarchy: it just renders `throwable.message`
 * in the browse/reader error states, so anything that is not translated here would surface as a raw
 * class name. Unknown throwables are returned untouched on purpose — wrapping them would hide
 * `IOException` subtypes that the network layer and the page loader do react to.
 */
fun mapToMihon(e: Throwable): Throwable = when (e) {
    is CancellationException -> e
    is NotFoundException -> Exception("Not found: ${e.url}", e)
    is AuthRequiredException -> Exception("Login required in the source's website (open WebView)", e)
    is TooManyRequestExceptions -> {
        val retryInSeconds = e.getRetryDelay() / 1000L
        if (retryInSeconds > 0L) {
            Exception("Rate limited, retry in ${retryInSeconds}s", e)
        } else {
            Exception("Rate limited by the source, try again later", e)
        }
    }
    is KotatsuInteractiveActionRequiredException -> Exception("This source needs a browser action: open WebView", e)
    is ParseException -> Exception(e.message ?: "Parse error", e)
    is ContentUnavailableException -> Exception(e.message ?: "Content unavailable", e)
    else -> e
}

/**
 * Runs [block] and rethrows whatever it fails with through [mapToMihon].
 *
 * [CancellationException] is rethrown untouched: swallowing or wrapping it would leave the paging
 * and reader coroutines alive after their scope is gone.
 *
 * [block] is not declared `suspend` on purpose — the function is inline, so the lambda body is
 * inlined into the (suspending) call site and can call suspending code just like `runCatching` does.
 */
suspend inline fun <T> kotatsuCall(block: () -> T): T {
    try {
        return block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw mapToMihon(e)
    }
}
