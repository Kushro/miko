// Ported from Kotatsu-Redo (GPL-3.0): core/network/CloudFlareInterceptor.kt
package tachiyomi.source.kotatsu.network

import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import tachiyomi.source.kotatsu.exception.KotatsuCloudFlareException

/**
 * Translates a CloudFlare interstitial into [KotatsuCloudFlareException].
 *
 * **Not installed in the default chain on purpose.** The client used by
 * [tachiyomi.source.kotatsu.KotatsuLoaderContext] is derived from Mihon's own
 * `NetworkHelper.client`, which already carries Mihon's `CloudflareInterceptor`: that one *solves*
 * the challenge with a WebView instead of merely reporting it, and it runs first. Stacking this one
 * on top would only replace Mihon's (localised, actionable) error with a second one for the rare
 * responses Mihon lets through, and it re-reads the body of every 403/503 to do so.
 *
 * Kept because it is the only piece that maps [CloudFlareHelper] detection to a typed error, which
 * is useful if a later cycle gives Kotatsu sources their own client instead of reusing Mihon's.
 */
class KotatsuCloudFlareInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return when (CloudFlareHelper.checkResponseForProtection(response)) {
            CloudFlareHelper.PROTECTION_BLOCKED -> {
                val url = response.request.url.toString()
                response.close()
                throw KotatsuCloudFlareException(url = url, isBlocked = true)
            }

            CloudFlareHelper.PROTECTION_CAPTCHA -> {
                val url = response.request.url.toString()
                response.close()
                throw KotatsuCloudFlareException(url = url, isBlocked = false)
            }

            else -> response
        }
    }
}
