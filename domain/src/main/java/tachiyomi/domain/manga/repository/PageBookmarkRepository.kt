package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.PageBookmark
import tachiyomi.domain.manga.model.PageBookmarkWithRelations

/** MIKO — persistence of per-page bookmarks (`page_bookmarks`). */
interface PageBookmarkRepository {

    /** Every bookmark, newest first, joined with manga/chapter info. */
    fun subscribeAll(): Flow<List<PageBookmarkWithRelations>>

    fun subscribeByMangaId(mangaId: Long): Flow<List<PageBookmark>>

    fun subscribeByChapterId(chapterId: Long): Flow<List<PageBookmark>>

    suspend fun getByMangaId(mangaId: Long): List<PageBookmark>

    suspend fun get(chapterId: Long, pageIndex: Int): PageBookmark?

    /**
     * Inserts and returns the id, or null when that page is already bookmarked.
     *
     * @param previewWebp the viewport capture to persist atomically with the bookmark (C20), or
     * null when no capture could be taken — the row is simply not created and consumers fall back
     * to the best-effort thumbnail chain. Bytes must already be capped by PageBookmarkPreviewCodec.
     */
    suspend fun insert(
        mangaId: Long,
        chapterId: Long,
        pageIndex: Int,
        imageUrl: String?,
        note: String?,
        scrollFraction: Float? = null,
        focusFraction: Float? = null,
        previewWebp: ByteArray? = null,
    ): Long?

    suspend fun updateNote(id: Long, note: String?)

    /** Re-points the thumbnail key after the preview re-resolved the page against the source. */
    suspend fun updateImageUrl(id: Long, imageUrl: String?)

    suspend fun delete(id: Long)

    suspend fun delete(chapterId: Long, pageIndex: Int)

    suspend fun deleteByMangaId(mangaId: Long)

    suspend fun deleteAll()

    /** Replaces the bookmarks of a manga (backup restore); chapterId resolved by the caller. */
    suspend fun replaceAll(mangaId: Long, bookmarks: List<PageBookmark>)
}
