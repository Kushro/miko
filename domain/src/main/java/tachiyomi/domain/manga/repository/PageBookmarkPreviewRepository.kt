package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.PageBookmarkPreviewMeta
import tachiyomi.domain.manga.model.PageBookmarkPreviewStats

/**
 * MIKO — persistence of the viewport captures of page bookmarks (`page_bookmark_previews`, C20).
 *
 * Blobs are written pre-capped at 1.5 MB (PageBookmarkPreviewCodec) so a row always fits Android's
 * 2 MB CursorWindow; rows die with their bookmark via ON DELETE CASCADE.
 */
interface PageBookmarkPreviewRepository {

    /** The capture of one bookmark, or null when it has none. */
    suspend fun get(bookmarkId: Long): ByteArray?

    /** Inserts or replaces the capture of a bookmark, stamping `updated_at` = now. */
    suspend fun upsert(bookmarkId: Long, preview: ByteArray)

    suspend fun delete(bookmarkId: Long)

    /** Reactive count + total bytes for the Moments stats card. */
    fun subscribeStats(): Flow<PageBookmarkPreviewStats>

    /** Metadata of every capture, largest first, without loading any blob. */
    suspend fun getMetas(): List<PageBookmarkPreviewMeta>

    /** bookmarkId -> capture bytes of one manga's bookmarks (backup creation). */
    suspend fun getByMangaId(mangaId: Long): Map<Long, ByteArray>
}
