package eu.kanade.tachiyomi.util.bookmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import eu.kanade.tachiyomi.util.waifu2x.ImageEnhancementCache
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * MIKO — WEBP encoder for the persistent viewport captures of page bookmarks ("Moments", C20).
 *
 * Every blob written to `page_bookmark_previews` goes through here: the encoder enforces the
 * [MAX_PREVIEW_BYTES] hard cap so a row is always readable through Android's 2 MB CursorWindow,
 * and clamps dimensions under the WebP encoder limit (`Bitmap.compress(WEBP…)` silently returns
 * false above [ImageEnhancementCache.MAX_WEBP_DIMENSION] per side).
 */
object PageBookmarkPreviewCodec {

    /** Quality of the capture taken when bookmarking, and of manual backfills. */
    const val CAPTURE_WEBP_QUALITY = 90

    /** Fixed profile of the Moments bulk/on-demand recompression. */
    const val RECOMPRESS_WEBP_QUALITY = 75

    /**
     * Hard cap per blob. Android reads rows through a 2 MB CursorWindow; staying well under it
     * keeps every `page_bookmark_previews` row readable no matter what else the row carries.
     */
    const val MAX_PREVIEW_BYTES = 1_500_000

    /** Quality ladder walked by [compressBounded] before it resorts to downscaling. */
    private val QUALITY_LADDER = intArrayOf(90, 75, 60, 50)

    /**
     * Encodes [bitmap] to WEBP at [initialQuality], stepping down the quality ladder and then
     * downscaling by ×0.75 as many times as needed until the result fits [MAX_PREVIEW_BYTES].
     * Returns null when encoding fails or nothing fits. Never recycles [bitmap]; intermediate
     * downscaled copies are recycled internally.
     */
    fun compressBounded(bitmap: Bitmap, initialQuality: Int): ByteArray? {
        var current = clampDimensions(bitmap)
        try {
            // A viewport capture is at most screen-sized, so the ladder alone almost always
            // fits; the downscale loop only exists to make the byte cap unconditional.
            repeat(4) {
                for (quality in QUALITY_LADDER) {
                    if (quality > initialQuality) continue
                    val bytes = encode(current, quality) ?: return null
                    if (bytes.size <= MAX_PREVIEW_BYTES) return bytes
                }
                val scaled = Bitmap.createScaledBitmap(
                    current,
                    (current.width * 3 / 4).coerceAtLeast(1),
                    (current.height * 3 / 4).coerceAtLeast(1),
                    true,
                )
                if (scaled != current && current != bitmap) current.recycle()
                current = scaled
            }
            return null
        } finally {
            if (current != bitmap) current.recycle()
        }
    }

    /**
     * Re-encodes an existing capture at [quality], downscaling to [maxDimension] per side first
     * (the fixed recompression profile: q75 + screen resolution). Returns null when the result
     * would not be smaller than [bytes] — the caller reports "no gain" and keeps the original.
     * Throws on decode/encode failure (corrupt blob): the caller reports a real failure.
     */
    fun recompress(bytes: ByteArray, quality: Int, maxDimension: Int): ByteArray? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        check(width > 0 && height > 0) { "Not a decodable image (${bytes.size} bytes)" }

        var sampleSize = 1
        while (width / (sampleSize * 2) > maxDimension || height / (sampleSize * 2) > maxDimension) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)) {
            "Failed to decode capture (${bytes.size} bytes)"
        }
        try {
            val scaled = if (decoded.width > maxDimension || decoded.height > maxDimension) {
                val ratio = minOf(
                    maxDimension.toFloat() / decoded.width,
                    maxDimension.toFloat() / decoded.height,
                )
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                decoded
            }
            try {
                val encoded = checkNotNull(encode(scaled, quality)) { "WEBP encode failed" }
                return encoded.takeIf { it.size < bytes.size }
            } finally {
                if (scaled != decoded) scaled.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    /**
     * Decodes a page image bounded by [maxDimension] per side, subsampling with `inSampleSize`
     * so a tall webtoon/upscaled page (hundreds of MB decoded at full resolution) never gets
     * fully materialized in memory — the same guard [recompress] applies to stored blobs, here
     * for the backfill path. [openStream] must be reopenable: the bounds pass and the decode pass
     * each open a fresh stream. Returns null when the data is not a decodable image.
     */
    fun decodeBounded(openStream: () -> InputStream, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) > maxDimension ||
            bounds.outHeight / (sampleSize * 2) > maxDimension
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return openStream().use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** Downscales so both sides fit the WebP encoder limit; returns the input when it already fits. */
    private fun clampDimensions(bitmap: Bitmap): Bitmap {
        val limit = ImageEnhancementCache.MAX_WEBP_DIMENSION
        if (bitmap.width <= limit && bitmap.height <= limit) return bitmap
        val ratio = minOf(limit.toFloat() / bitmap.width, limit.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** compress() can return false without throwing (e.g. a side over the WebP limit) → null. */
    private fun encode(bitmap: Bitmap, quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
        }
        return if (ok) out.toByteArray() else null
    }
}
