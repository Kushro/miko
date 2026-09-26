package tachiyomi.domain.chapter.model

import tachiyomi.domain.manga.model.MangaCover

/**
 * MIKO — a bookmarked chapter (`chapters.bookmark = 1`) joined with what the Favorites screen needs
 * to render it: its manga, its cover and its [ChapterBookmarkType] (`GENERIC` when the chapter has
 * no row in `chapter_bookmark_types`).
 *
 * Chapters carry no "bookmarked at" timestamp, so lists are ordered by manga title and chapter
 * number, never by date.
 */
data class BookmarkedChapter(
    val chapterId: Long,
    val mangaId: Long,
    val chapterName: String,
    val chapterNumber: Double,
    val scanlator: String?,
    val read: Boolean,
    val dateUpload: Long,
    val mangaTitle: String,
    val coverData: MangaCover,
    val type: ChapterBookmarkType,
)
