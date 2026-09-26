package eu.kanade.tachiyomi.ui.reader

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.resume

/**
 * MIKO — one shared handler is enough: [PixelCopy]'s callback always lands on the main thread
 * regardless, this just avoids allocating a new [Handler] per capture.
 */
private val pixelCopyHandler = Handler(Looper.getMainLooper())

/**
 * MIKO — captures the reader viewport exactly as it looks on screen right now (zoom, pan, upscale
 * and reading filters included, menu chrome excluded) for a persistent page-bookmark preview
 * (C20 "Momentos").
 *
 * [PixelCopy] reads real rendered pixels straight off [activity]'s window, which is the only
 * capture path that survives `ReaderPageImageView`'s hardware bitmaps — a plain `View.draw` onto a
 * software canvas crashes against those (D1 of the C20 contract). [obstructions] are the reader bar
 * containers currently on screen (top bar, bottom cluster, vertical seekbar), already in window
 * coordinates; each one that hugs a window edge trims the copied rect from that side so menu chrome
 * never ends up in the capture (D2) — an empty list (menu hidden) leaves the full page rect intact.
 *
 * Returns null whenever a capture can't be trusted: the window isn't attached, [page] isn't laid
 * out in [viewer] (not the current page, still loading, or off both the pager/recycler viewport),
 * the resulting rect is empty, or the copy itself fails. The caller treats null exactly like a
 * capture that was never attempted — the bookmark is still created, just without a preview (D3).
 */
suspend fun capturePageVisible(
    activity: ReaderActivity,
    viewer: Viewer,
    page: ReaderPage,
    obstructions: List<Rect>,
): Bitmap? {
    val window = activity.window
    val decorView = window.decorView
    if (!decorView.isAttachedToWindow) return null

    val pageRect = when (viewer) {
        is WebtoonViewer -> viewer.getPageVisibleRect(page)
        is PagerViewer -> viewer.getPageVisibleRect(page)
        else -> null
    } ?: return null

    val windowRect = Rect(0, 0, decorView.width, decorView.height)
    val srcRect = trimObstructions(pageRect, windowRect, obstructions, edgeMargin(activity))
    if (srcRect.isEmpty) return null

    val bitmap = Bitmap.createBitmap(srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                window,
                srcRect,
                bitmap,
                { result ->
                    if (continuation.isActive) {
                        if (result == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap)
                        } else {
                            activity.logcat(LogPriority.ERROR) {
                                "PageCapture: PixelCopy failed, result=$result"
                            }
                            continuation.resume(null)
                        }
                    }
                },
                pixelCopyHandler,
            )
        } catch (e: IllegalArgumentException) {
            activity.logcat(LogPriority.ERROR, e) {
                "PageCapture: PixelCopy.request rejected srcRect=$srcRect"
            }
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

/**
 * A generous slack (rather than requiring an exact touch) around each window edge: the bar
 * containers report their Compose layout bounds, which can sit a hair inside the true edge once
 * insets/padding are applied — D2 only cares which edge a rect belongs to, not exact contact.
 */
private fun edgeMargin(activity: ReaderActivity): Int =
    (24 * activity.resources.displayMetrics.density).toInt()

/**
 * Shrinks [rect] by whichever [obstructions] hug a [windowRect] edge (D2): the reader bar chrome
 * never has to be located precisely, only which side of the window it occupies. A rect that hugs no
 * edge is left alone — [obstructions] is meant to be exactly the bar containers ReaderAppBars
 * reports, and a non-edge-anchored rect there would be a signal something else changed upstream,
 * not something this function should guess about.
 */
private fun trimObstructions(rect: Rect, windowRect: Rect, obstructions: List<Rect>, edgeMargin: Int): Rect {
    val trimmed = Rect(rect)
    for (obstruction in obstructions) {
        when {
            obstruction.top <= windowRect.top + edgeMargin ->
                trimmed.top = maxOf(trimmed.top, obstruction.bottom)
            obstruction.bottom >= windowRect.bottom - edgeMargin ->
                trimmed.bottom = minOf(trimmed.bottom, obstruction.top)
            obstruction.left <= windowRect.left + edgeMargin ->
                trimmed.left = maxOf(trimmed.left, obstruction.right)
            obstruction.right >= windowRect.right - edgeMargin ->
                trimmed.right = minOf(trimmed.right, obstruction.left)
        }
    }
    return trimmed
}
