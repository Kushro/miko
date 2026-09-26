package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.PageBookmark
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
import tachiyomi.domain.manga.repository.PageBookmarkRepository

/**
 * MIKO — SQLDelight implementation of [PageBookmarkRepository] on top of `page_bookmarks`.
 *
 * A page is identified by (chapterId, pageIndex) — that pair is unique — so [insert] returns
 * `null` when the page was already bookmarked.
 */
class PageBookmarkRepositoryImpl(
    private val handler: DatabaseHandler,
) : PageBookmarkRepository {

    override fun subscribeAll(): Flow<List<PageBookmarkWithRelations>> {
        return handler.subscribeToList {
            page_bookmarksQueries.getAllWithRelations(::mapPageBookmarkWithRelations)
        }
    }

    override fun subscribeByMangaId(mangaId: Long): Flow<List<PageBookmark>> {
        return handler.subscribeToList { page_bookmarksQueries.getByMangaId(mangaId, ::mapPageBookmark) }
    }

    override fun subscribeByChapterId(chapterId: Long): Flow<List<PageBookmark>> {
        return handler.subscribeToList { page_bookmarksQueries.getByChapterId(chapterId, ::mapPageBookmark) }
    }

    override suspend fun getByMangaId(mangaId: Long): List<PageBookmark> {
        return handler.awaitList { page_bookmarksQueries.getByMangaId(mangaId, ::mapPageBookmark) }
    }

    override suspend fun get(chapterId: Long, pageIndex: Int): PageBookmark? {
        return handler.awaitOneOrNull {
            page_bookmarksQueries.get(chapterId, pageIndex.toLong(), ::mapPageBookmark)
        }
    }

    override suspend fun insert(
        mangaId: Long,
        chapterId: Long,
        pageIndex: Int,
        imageUrl: String?,
        note: String?,
        scrollFraction: Float?,
        focusFraction: Float?,
        previewWebp: ByteArray?,
    ): Long? {
        return try {
            handler.await(inTransaction = true) {
                page_bookmarksQueries.insert(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    pageIndex = pageIndex.toLong(),
                    imageUrl = imageUrl,
                    note = note,
                    createdAt = System.currentTimeMillis(),
                    scrollFraction = scrollFraction?.toDouble(),
                    focusFraction = focusFraction?.toDouble(),
                )
                val id = page_bookmarksQueries.selectLastInsertedRowId().executeAsOne()
                if (previewWebp != null) {
                    page_bookmark_previewsQueries.upsert(
                        id = id,
                        preview = previewWebp,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                id
            }
        } catch (e: Exception) {
            // Unique index violation (page already bookmarked) or a dangling manga/chapter id. The
            // concrete exception depends on the SQLite helper factory in use (requery/SQLCipher).
            logcat(LogPriority.DEBUG, e) { "Could not bookmark page $pageIndex of chapter $chapterId" }
            null
        }
    }

    override suspend fun updateNote(id: Long, note: String?) {
        handler.await { page_bookmarksQueries.updateNote(note = note, id = id) }
    }

    override suspend fun updateImageUrl(id: Long, imageUrl: String?) {
        handler.await { page_bookmarksQueries.updateImageUrl(imageUrl = imageUrl, id = id) }
    }

    override suspend fun delete(id: Long) {
        handler.await { page_bookmarksQueries.delete(id) }
    }

    override suspend fun delete(chapterId: Long, pageIndex: Int) {
        handler.await { page_bookmarksQueries.deleteByPage(chapterId, pageIndex.toLong()) }
    }

    override suspend fun deleteByMangaId(mangaId: Long) {
        handler.await { page_bookmarksQueries.deleteByMangaId(mangaId) }
    }

    override suspend fun deleteAll() {
        handler.await { page_bookmarksQueries.deleteAll() }
    }

    override suspend fun replaceAll(mangaId: Long, bookmarks: List<PageBookmark>) {
        val now = System.currentTimeMillis()
        val toKeep = bookmarks.distinctBy { it.chapterId to it.pageIndex }
        handler.await(inTransaction = true) {
            // Id-preserving upsert (C20): a row that survives the replace keeps its _id, so its
            // capture in page_bookmark_previews survives too — the old delete-and-reinsert
            // cascaded every local capture away on each backup restore.
            val existing = page_bookmarksQueries.getByMangaId(mangaId, ::mapPageBookmark).executeAsList()
            val existingByPage = existing.associateBy { it.chapterId to it.pageIndex }
            val keptPages = toKeep.mapTo(HashSet()) { it.chapterId to it.pageIndex }
            existing.forEach { current ->
                if ((current.chapterId to current.pageIndex) !in keptPages) {
                    page_bookmarksQueries.delete(current.id)
                }
            }
            toKeep.forEach { bookmark ->
                val current = existingByPage[bookmark.chapterId to bookmark.pageIndex]
                if (current != null) {
                    page_bookmarksQueries.replaceFields(
                        imageUrl = bookmark.imageUrl,
                        note = bookmark.note,
                        createdAt = bookmark.createdAt.takeIf { it > 0L } ?: current.createdAt,
                        scrollFraction = bookmark.scrollFraction?.toDouble(),
                        focusFraction = bookmark.focusFraction?.toDouble(),
                        id = current.id,
                    )
                } else {
                    page_bookmarksQueries.insert(
                        mangaId = mangaId,
                        chapterId = bookmark.chapterId,
                        pageIndex = bookmark.pageIndex.toLong(),
                        imageUrl = bookmark.imageUrl,
                        note = bookmark.note,
                        createdAt = bookmark.createdAt.takeIf { it > 0L } ?: now,
                        scrollFraction = bookmark.scrollFraction?.toDouble(),
                        focusFraction = bookmark.focusFraction?.toDouble(),
                    )
                }
            }
        }
    }
}

private fun mapPageBookmark(
    id: Long,
    mangaId: Long,
    chapterId: Long,
    pageIndex: Long,
    imageUrl: String?,
    note: String?,
    createdAt: Long,
    scrollFraction: Double?,
    focusFraction: Double?,
): PageBookmark = PageBookmark(
    id = id,
    mangaId = mangaId,
    chapterId = chapterId,
    pageIndex = pageIndex.toInt(),
    imageUrl = imageUrl,
    note = note,
    createdAt = createdAt,
    scrollFraction = scrollFraction?.toFloat(),
    focusFraction = focusFraction?.toFloat(),
)

private fun mapPageBookmarkWithRelations(
    id: Long,
    mangaId: Long,
    chapterId: Long,
    pageIndex: Long,
    imageUrl: String?,
    note: String?,
    createdAt: Long,
    scrollFraction: Double?,
    focusFraction: Double?,
    mangaTitle: String,
    thumbnailUrl: String?,
    sourceId: Long,
    isFavorite: Boolean,
    coverLastModified: Long,
    chapterName: String,
    chapterNumber: Double,
    previewUpdatedAt: Long?,
): PageBookmarkWithRelations = PageBookmarkWithRelations(
    bookmark = PageBookmark(
        id = id,
        mangaId = mangaId,
        chapterId = chapterId,
        pageIndex = pageIndex.toInt(),
        imageUrl = imageUrl,
        note = note,
        createdAt = createdAt,
        scrollFraction = scrollFraction?.toFloat(),
        focusFraction = focusFraction?.toFloat(),
    ),
    mangaTitle = mangaTitle,
    chapterName = chapterName,
    chapterNumber = chapterNumber,
    coverData = MangaCover(
        mangaId = mangaId,
        sourceId = sourceId,
        isMangaFavorite = isFavorite,
        ogUrl = thumbnailUrl,
        lastModified = coverLastModified,
    ),
    previewUpdatedAt = previewUpdatedAt,
)
