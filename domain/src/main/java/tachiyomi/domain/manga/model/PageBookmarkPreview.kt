package tachiyomi.domain.manga.model

/**
 * MIKO — aggregate storage usage of the persistent viewport captures (`page_bookmark_previews`,
 * C20): how many captures exist and how many bytes they occupy inside the database. Feeds the
 * stats card of the Moments screen reactively.
 */
data class PageBookmarkPreviewStats(
    val count: Long,
    val totalBytes: Long,
)

/**
 * MIKO — per-capture metadata (no blob): drives the bulk recompression loop of the Moments screen,
 * which iterates captures largest-first without ever materializing every blob in memory.
 */
data class PageBookmarkPreviewMeta(
    val bookmarkId: Long,
    val sizeBytes: Long,
    val updatedAt: Long,
)

/**
 * MIKO — Coil model for a persisted capture: the list thumbnail passes this instead of raw bytes
 * so the image pipeline can key the cache as `"pb-preview;bookmarkId;updatedAt"` (recompression
 * bumps `updatedAt` and invalidates the cached decode) and fetch the blob lazily per id.
 */
data class PageBookmarkPreviewCover(
    val bookmarkId: Long,
    val updatedAt: Long,
)
