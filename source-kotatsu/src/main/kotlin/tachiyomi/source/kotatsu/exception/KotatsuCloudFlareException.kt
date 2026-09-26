package tachiyomi.source.kotatsu.exception

import okio.IOException

/**
 * Raised when a response for a Kotatsu source is a CloudFlare interstitial that the host could not
 * clear on its own.
 *
 * Mihon's own `CloudflareInterceptor` already sits in [eu.kanade.tachiyomi.network.NetworkHelper.client]
 * and tries to solve challenges with a WebView, so this exception is *not* thrown by the default
 * interceptor chain. It exists for [tachiyomi.source.kotatsu.network.KotatsuCloudFlareInterceptor],
 * which is opt-in — see that class for the rationale.
 */
class KotatsuCloudFlareException(
    val url: String,
    val isBlocked: Boolean = false,
) : IOException(
    if (isBlocked) {
        "Blocked by CloudFlare: $url"
    } else {
        "Protected by CloudFlare, open the source in WebView to solve the challenge: $url"
    },
)
