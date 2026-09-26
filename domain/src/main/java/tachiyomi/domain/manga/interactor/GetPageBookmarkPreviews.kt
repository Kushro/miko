package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.PageBookmarkPreviewMeta
import tachiyomi.domain.manga.model.PageBookmarkPreviewStats
import tachiyomi.domain.manga.repository.PageBookmarkPreviewRepository

/** MIKO — read side of the persistent viewport captures of page bookmarks (C20). */
class GetPageBookmarkPreviews(
    private val repository: PageBookmarkPreviewRepository,
) {

    /** The capture of one bookmark, or null when it has none (Coil fetcher, preview dialog). */
    suspend fun await(bookmarkId: Long): ByteArray? = repository.get(bookmarkId)

    /** Reactive count + total bytes for the Moments stats card. */
    fun subscribeStats(): Flow<PageBookmarkPreviewStats> = repository.subscribeStats()

    /** Metadata of every capture, largest first, blob-free (bulk recompression loop). */
    suspend fun metas(): List<PageBookmarkPreviewMeta> = repository.getMetas()

    /** bookmarkId -> capture bytes of one manga's bookmarks (backup creation). */
    suspend fun byMangaId(mangaId: Long): Map<Long, ByteArray> = repository.getByMangaId(mangaId)
}
