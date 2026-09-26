package tachiyomi.data.chapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.chapter.repository.ChapterBookmarkRepository
import tachiyomi.domain.manga.model.MangaCover

/**
 * MIKO — SQLDelight implementation of [ChapterBookmarkRepository] on top of
 * `chapter_bookmark_types`.
 *
 * [ChapterBookmarkType.GENERIC] is the absence of a row, so [setType] with `GENERIC` deletes
 * instead of storing a 0 and every read maps a missing row back to `GENERIC`. Nothing here touches
 * `chapters.bookmark`: un-bookmarking is handled by the `clear_chapter_bookmark_type_on_unbookmark`
 * trigger, and bookmarking is [tachiyomi.domain.chapter.interactor.SetChapterBookmarkType]'s job.
 */
class ChapterBookmarkRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterBookmarkRepository {

    override fun subscribeTypesByMangaId(mangaId: Long): Flow<Map<Long, ChapterBookmarkType>> {
        return handler.subscribeToList {
            chapter_bookmark_typesQueries.getTypesByMangaId(mangaId, ::mapChapterBookmarkType)
        }
            .map { it.toMap() }
    }

    override fun subscribeAllTypes(): Flow<Map<Long, ChapterBookmarkType>> {
        return handler.subscribeToList { chapter_bookmark_typesQueries.getAllTypes(::mapChapterBookmarkType) }
            .map { it.toMap() }
    }

    override suspend fun getTypesByMangaId(mangaId: Long): Map<Long, ChapterBookmarkType> {
        return handler.awaitList { chapter_bookmark_typesQueries.getTypesByMangaId(mangaId, ::mapChapterBookmarkType) }
            .toMap()
    }

    override suspend fun getType(chapterId: Long): ChapterBookmarkType {
        val stored = handler.awaitOneOrNull { chapter_bookmark_typesQueries.getType(chapterId) }
        return ChapterBookmarkType.fromId(stored?.toInt())
    }

    override suspend fun setType(chapterId: Long, type: ChapterBookmarkType) {
        if (type == ChapterBookmarkType.GENERIC) {
            handler.await { chapter_bookmark_typesQueries.delete(chapterId) }
        } else {
            handler.await {
                chapter_bookmark_typesQueries.upsert(
                    chapterId = chapterId,
                    type = type.id.toLong(),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    override suspend fun setTypes(chapterIds: List<Long>, type: ChapterBookmarkType) {
        if (chapterIds.isEmpty()) return
        val now = System.currentTimeMillis()
        handler.await(inTransaction = true) {
            chapterIds.forEach { chapterId ->
                if (type == ChapterBookmarkType.GENERIC) {
                    chapter_bookmark_typesQueries.delete(chapterId)
                } else {
                    chapter_bookmark_typesQueries.upsert(
                        chapterId = chapterId,
                        type = type.id.toLong(),
                        updatedAt = now,
                    )
                }
            }
        }
    }

    override fun subscribeBookmarkedChapters(): Flow<List<BookmarkedChapter>> {
        return handler.subscribeToList {
            chapter_bookmark_typesQueries.getBookmarkedChaptersWithRelations(::mapBookmarkedChapter)
        }
    }
}

private fun mapChapterBookmarkType(chapterId: Long, type: Long): Pair<Long, ChapterBookmarkType> =
    chapterId to ChapterBookmarkType.fromId(type.toInt())

private fun mapBookmarkedChapter(
    chapterId: Long,
    mangaId: Long,
    chapterName: String,
    chapterNumber: Double,
    scanlator: String?,
    read: Boolean,
    dateUpload: Long,
    mangaTitle: String,
    sourceId: Long,
    isFavorite: Boolean,
    thumbnailUrl: String?,
    coverLastModified: Long,
    bookmarkType: Long,
): BookmarkedChapter = BookmarkedChapter(
    chapterId = chapterId,
    mangaId = mangaId,
    chapterName = chapterName,
    chapterNumber = chapterNumber,
    scanlator = scanlator,
    read = read,
    dateUpload = dateUpload,
    mangaTitle = mangaTitle,
    coverData = MangaCover(
        mangaId = mangaId,
        sourceId = sourceId,
        isMangaFavorite = isFavorite,
        ogUrl = thumbnailUrl,
        lastModified = coverLastModified,
    ),
    type = ChapterBookmarkType.fromId(bookmarkType.toInt()),
)
