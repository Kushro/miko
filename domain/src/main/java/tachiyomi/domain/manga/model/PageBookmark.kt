package tachiyomi.domain.manga.model

/**
 * MIKO — a bookmark on a single page of a chapter (`page_bookmarks`), like Kotatsu's page
 * bookmarks. Identity for toggling is (chapterId, pageIndex).
 *
 * @param pageIndex 0-based `ReaderPage.index`.
 * @param imageUrl the page image URL at bookmark time, used for a best-effort thumbnail.
 * @param scrollFraction how far into the page the reader had scrolled, as a fraction (0..1) of the
 * page's height — the part of the page above the top edge of the viewport. Deliberately a fraction
 * and not a pixel offset: rotating recreates the reader and re-lays the page out at another width,
 * so pixels do not survive a rotation, a different device, or a different zoom level.
 * `null` means unknown — bookmarks made before this existed, and bookmarks made in a paged reading
 * mode, where a page is never partially scrolled. Those reopen at the top of the page.
 * @param focusFraction where on the page the user's attention was when bookmarking, as a fraction
 * (0..1) of the page's height: the long-pressed point for the reader's long-press sheet, the centre
 * of the viewport for the top-bar toggle. Only the Favorites thumbnail and the page preview use it
 * (they centre on it); reopening the reader keeps restoring [scrollFraction]. `null` = unknown
 * (pre-C15 bookmarks, paged modes) — consumers fall back to the [scrollFraction] band, then centre.
 */
data class PageBookmark(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val imageUrl: String? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
    val scrollFraction: Float? = null,
    val focusFraction: Float? = null,
)

/**
 * A [PageBookmark] joined with what the bookmarks list needs to render it.
 *
 * @param previewUpdatedAt `updated_at` of the persistent viewport capture in
 * `page_bookmark_previews` (C20), or `null` when the bookmark has no capture — the list then falls
 * back to the old best-effort thumbnail (ChapterCache -> manga cover). Only the timestamp travels
 * here: the blob itself is never selected by list queries and is fetched per id through Coil.
 */
data class PageBookmarkWithRelations(
    val bookmark: PageBookmark,
    val mangaTitle: String,
    val chapterName: String,
    val chapterNumber: Double,
    val coverData: MangaCover,
    val previewUpdatedAt: Long? = null,
)
