// Ported from Kotatsu-Redo (GPL-3.0): core/network/webview/WebViewExecutor.kt
package tachiyomi.source.kotatsu.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.resume

/**
 * Minimal headless JavaScript evaluator for `MangaLoaderContext.evaluateJs`.
 *
 * Only the `evaluateJs` half of the reference executor is ported: request interception and captcha
 * auto-solving are out of scope for this cycle. The script runs in a throwaway `WebView` whose
 * origin is [baseUrl] (via `loadDataWithBaseURL` on an empty document, so no page is actually
 * fetched — parsers use this to get same-origin access, not to scrape rendered HTML).
 *
 * Everything happens on the main thread because `WebView` requires it, and a [Mutex] serialises
 * calls so concurrent parsers never share one instance mid-evaluation.
 */
internal class WebViewJsExecutor(private val context: Context) {

    private val mutex = Mutex()

    /**
     * @param baseUrl origin for the script, or empty to run it in `about:blank`.
     * @param timeout hard limit in milliseconds for the whole evaluation.
     * @return the JSON-encoded result, or `null` on timeout, on `null` result, or when no WebView
     * is available on the device.
     */
    suspend fun evaluate(baseUrl: String, script: String, timeout: Long): String? = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val webView = createWebView() ?: return@withContext null
            try {
                withTimeoutOrNull(timeout) {
                    if (baseUrl.isNotEmpty()) {
                        awaitBaseDocument(webView, baseUrl)
                    }
                    evaluateJavascript(webView, script)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Cannot evaluate JS in the context of \"$baseUrl\"" }
                null
            } finally {
                release(webView)
            }
        }
    }

    @MainThread
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView? = try {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }
    } catch (e: Exception) {
        // WebView may be missing, disabled or updating on this device.
        logcat(LogPriority.WARN, e) { "WebView is not available" }
        null
    }

    /** Loads an empty document so the script runs with [baseUrl] as its origin. */
    @MainThread
    private suspend fun awaitBaseDocument(webView: WebView, baseUrl: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            webView.loadDataWithBaseURL(baseUrl, EMPTY_DOCUMENT, MIME_TYPE_HTML, ENCODING, null)
        }
    }

    @MainThread
    private suspend fun evaluateJavascript(webView: WebView, script: String): String? =
        suspendCancellableCoroutine<String?> { cont ->
            webView.evaluateJavascript(script) { result ->
                if (cont.isActive) cont.resume(result?.takeUnless { it == "null" })
            }
        }

    @MainThread
    private fun release(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
        }
        // Destroying a WebView from inside one of its own callbacks crashes, and the continuation
        // above resumes exactly there, so the teardown is posted to the next main-loop message.
        Handler(Looper.getMainLooper()).post {
            runCatching { webView.destroy() }
        }
    }

    private companion object {
        const val EMPTY_DOCUMENT = "<html></html>"
        const val MIME_TYPE_HTML = "text/html"
        const val ENCODING = "UTF-8"
    }
}
