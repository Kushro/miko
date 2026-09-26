package tachiyomi.source.kotatsu

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.IOException
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.map
import tachiyomi.source.kotatsu.config.KotatsuSourceConfig
import tachiyomi.source.kotatsu.exception.KotatsuInteractiveActionRequiredException
import tachiyomi.source.kotatsu.image.KotatsuBitmap
import tachiyomi.source.kotatsu.network.KotatsuHeadersInterceptor
import tachiyomi.source.kotatsu.network.KotatsuRateLimitInterceptor
import tachiyomi.source.kotatsu.webview.WebViewJsExecutor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Bitmap as AndroidBitmap

/**
 * Host side of the Kotatsu parsers library: everything a parser can ask of the app.
 *
 * Networking deliberately builds on Mihon's own client ([NetworkHelper.client]) so Kotatsu sources
 * share the app's cookie jar, cache, user agent handling and CloudFlare solving with installed
 * extensions. Only the two pieces Mihon has no equivalent for are added on top: the `429` mapper
 * and the per-parser header/interceptor dispatcher.
 *
 * `interceptWebViewRequests` and `captureWebViewUrls` are intentionally **not** overridden; they
 * keep the library default, which throws `UnsupportedOperationException`. Only a handful of parsers
 * rely on capturing WebView traffic and implementing it properly needs a full request-interception
 * WebView client. Those sources fail with that error until a later cycle adds it.
 */
class KotatsuLoaderContext(
    private val app: Application,
    private val network: NetworkHelper,
) : MangaLoaderContext() {

    /**
     * Resolves the parser that owns a request, for [KotatsuHeadersInterceptor].
     *
     * Set by [KotatsuSourceRegistry] on construction: the registry needs this context to create its
     * sources, so the dependency can only be closed afterwards.
     */
    @Volatile
    var parserResolver: ((MangaSource) -> MangaParser?)? = null

    private val configs = ConcurrentHashMap<String, KotatsuSourceConfig>()

    private val webViewJsExecutor by lazy { WebViewJsExecutor(app) }

    override val httpClient: OkHttpClient by lazy {
        network.client.newBuilder()
            .addInterceptor(KotatsuRateLimitInterceptor())
            .addInterceptor(KotatsuHeadersInterceptor { source -> parserResolver?.invoke(source) })
            .build()
    }

    override val cookieJar: CookieJar
        get() = network.cookieJar

    /**
     * Per-source settings live in the same `SharedPreferences` file Mihon's per-source settings
     * screen writes to, so the parser and the UI never drift apart.
     */
    override fun getConfig(source: MangaSource): MangaSourceConfig = configs.getOrPut(source.name) {
        KotatsuSourceConfig(
            prefs = app.getSharedPreferences(
                KotatsuSourceIds.preferenceKeyOf(source.name),
                Context.MODE_PRIVATE,
            ),
            defaultUserAgent = ::getDefaultUserAgent,
        )
    }

    override fun getDefaultUserAgent(): String = network.defaultUserAgentProvider()

    @Deprecated("Provide a base url")
    override suspend fun evaluateJs(script: String): String? = evaluateJs("", script, DEFAULT_JS_TIMEOUT)

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? =
        webViewJsExecutor.evaluate(baseUrl, script, timeout)

    override fun encodeBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    // Decoding stays on DEFAULT: it accepts padded and line-wrapped input alike, which is what
    // parsers get from the sites they scrape.
    override fun decodeBase64(data: String): ByteArray = Base64.decode(data, Base64.DEFAULT)

    override fun getPreferredLocales(): List<Locale> {
        val locales = LocaleListCompat.getDefault()
        return (0 until locales.size()).mapNotNull { locales[it] }
    }

    override fun requestBrowserAction(parser: MangaParser, url: String): Nothing =
        throw KotatsuInteractiveActionRequiredException(parser.source, url)

    override fun createBitmap(width: Int, height: Int): Bitmap = KotatsuBitmap.create(width, height)

    /**
     * Decodes the image, hands it to the parser's descrambling lambda and re-encodes the result.
     *
     * The bitmap is decoded as mutable so descramblers that draw into the source image in place do
     * not force a defensive copy. The output keeps the original format when it was JPEG and falls
     * back to lossless PNG otherwise, so re-encoding never adds a second generation of artifacts to
     * an already lossless page.
     */
    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response =
        response.map { body ->
            val isJpeg = body.contentType()?.subtype?.lowercase().orEmpty() in JPEG_SUBTYPES
            val options = BitmapFactory.Options().apply { inMutable = true }
            val decoded = body.byteStream().use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("Cannot decode the image to descramble")
            val source = KotatsuBitmap.create(decoded)
            try {
                val result = redraw(source) as KotatsuBitmap
                try {
                    val buffer = Buffer()
                    if (isJpeg) {
                        result.compressTo(buffer.outputStream(), AndroidBitmap.CompressFormat.JPEG, JPEG_QUALITY)
                        buffer.asResponseBody(MIME_JPEG.toMediaType())
                    } else {
                        result.compressTo(buffer.outputStream(), AndroidBitmap.CompressFormat.PNG, PNG_QUALITY)
                        buffer.asResponseBody(MIME_PNG.toMediaType())
                    }
                } finally {
                    result.close()
                }
            } finally {
                // Idempotent: `redraw` is allowed to return the very same instance it was given.
                source.close()
            }
        }

    private companion object {
        const val DEFAULT_JS_TIMEOUT = 30_000L
        const val JPEG_QUALITY = 95
        const val PNG_QUALITY = 100
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
        val JPEG_SUBTYPES = setOf("jpeg", "jpg")
    }
}
