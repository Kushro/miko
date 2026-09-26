package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.PageBookmarkPreviewMeta
import tachiyomi.domain.manga.model.PageBookmarkPreviewStats
import tachiyomi.domain.manga.repository.PageBookmarkPreviewRepository

/**
 * MIKO — SQLDelight implementation of [PageBookmarkPreviewRepository] on top of
 * `page_bookmark_previews` (C20). Blobs arrive pre-capped at 1.5 MB (PageBookmarkPreviewCodec),
 * so single-row reads always fit Android's 2 MB CursorWindow.
 */
class PageBookmarkPreviewRepositoryImpl(
    private val handler: DatabaseHandler,
) : PageBookmarkPreviewRepository {

    override suspend fun get(bookmarkId: Long): ByteArray? {
        return handler.awaitOneOrNull { page_bookmark_previewsQueries.get(bookmarkId) }
    }

    override suspend fun upsert(bookmarkId: Long, preview: ByteArray) {
        handler.await {
            page_bookmark_previewsQueries.upsert(
                id = bookmarkId,
                preview = preview,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun delete(bookmarkId: Long) {
        handler.await { page_bookmark_previewsQueries.delete(bookmarkId) }
    }

    override fun subscribeStats(): Flow<PageBookmarkPreviewStats> {
        return handler.subscribeToOne {
            page_bookmark_previewsQueries.getStats { count, totalBytes ->
                PageBookmarkPreviewStats(count = count, totalBytes = totalBytes)
            }
        }
    }

    override suspend fun getMetas(): List<PageBookmarkPreviewMeta> {
        return handler.awaitList {
            page_bookmark_previewsQueries.getMetas { bookmarkId, sizeBytes, updatedAt ->
                PageBookmarkPreviewMeta(bookmarkId = bookmarkId, sizeBytes = sizeBytes, updatedAt = updatedAt)
            }
        }
    }

    override suspend fun getByMangaId(mangaId: Long): Map<Long, ByteArray> {
        return handler.awaitList {
            page_bookmark_previewsQueries.getByMangaId(mangaId) { bookmarkId, preview ->
                bookmarkId to preview
            }
        }.toMap()
    }
}
