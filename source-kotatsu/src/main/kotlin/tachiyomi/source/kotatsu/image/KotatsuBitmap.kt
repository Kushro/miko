// Ported from Kotatsu-Redo (GPL-3.0): core/parser/BitmapWrapper.kt
package tachiyomi.source.kotatsu.image

import android.graphics.Canvas
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import java.io.OutputStream
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Rect as AndroidRect

/**
 * Adapter between the parsers library's platform-agnostic [Bitmap] and [android.graphics.Bitmap].
 *
 * Parsers that descramble images receive instances of this class and may hand back either the same
 * instance or a fresh one obtained from `MangaLoaderContext.createBitmap`, so callers must be able
 * to [close] both without assuming they are distinct — [close] is idempotent because
 * `AndroidBitmap.recycle()` is.
 */
internal class KotatsuBitmap private constructor(
    private val androidBitmap: AndroidBitmap,
) : Bitmap, AutoCloseable {

    // Only allocated for bitmaps that are actually drawn into.
    private val canvas by lazy { Canvas(androidBitmap) }

    override val width: Int
        get() = androidBitmap.width

    override val height: Int
        get() = androidBitmap.height

    override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
        val source = (sourceBitmap as KotatsuBitmap).androidBitmap
        canvas.drawBitmap(source, src.toAndroidRect(), dst.toAndroidRect(), null)
    }

    fun compressTo(output: OutputStream, format: AndroidBitmap.CompressFormat, quality: Int) {
        androidBitmap.compress(format, quality, output)
    }

    override fun close() {
        androidBitmap.recycle()
    }

    companion object {

        fun create(width: Int, height: Int): KotatsuBitmap = KotatsuBitmap(
            AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888),
        )

        fun create(bitmap: AndroidBitmap): KotatsuBitmap = KotatsuBitmap(
            if (bitmap.isMutable) {
                bitmap
            } else {
                // `copy` is @Nullable: it returns null when the target config cannot hold the pixels.
                requireNotNull(bitmap.copy(AndroidBitmap.Config.ARGB_8888, true)) {
                    "Unable to obtain a mutable copy of the decoded image"
                }
            },
        )

        private fun Rect.toAndroidRect() = AndroidRect(left, top, right, bottom)
    }
}
