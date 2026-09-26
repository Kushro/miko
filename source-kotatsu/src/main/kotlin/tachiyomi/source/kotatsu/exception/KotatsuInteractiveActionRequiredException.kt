package tachiyomi.source.kotatsu.exception

import okio.IOException
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Thrown when a Kotatsu parser asks the host to open a browser so the user can solve a captcha or
 * complete a non cookie-based login.
 *
 * Modelled after Kotatsu-Redo's `core/exceptions/InteractiveActionRequiredException` (GPL-3.0):
 * it extends [IOException] rather than plain `Exception` because parsers may raise it from inside an
 * OkHttp interceptor, where only [IOException] can travel safely up the call stack.
 */
class KotatsuInteractiveActionRequiredException(
    val source: MangaSource,
    val url: String,
) : IOException("Interactive action is required for ${source.name}: $url")
