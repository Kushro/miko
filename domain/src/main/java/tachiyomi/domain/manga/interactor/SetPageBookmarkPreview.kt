package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.PageBookmarkPreviewRepository

/** MIKO — write side of the persistent viewport captures of page bookmarks (C20). */
class SetPageBookmarkPreview(
    private val repository: PageBookmarkPreviewRepository,
) {

    /**
     * Inserts or replaces the capture of a bookmark. Callers must pass bytes already capped by
     * PageBookmarkPreviewCodec (<= 1.5 MB) so the row stays readable through a CursorWindow.
     */
    suspend fun upsert(bookmarkId: Long, preview: ByteArray) = repository.upsert(bookmarkId, preview)

    suspend fun delete(bookmarkId: Long) = repository.delete(bookmarkId)
}
