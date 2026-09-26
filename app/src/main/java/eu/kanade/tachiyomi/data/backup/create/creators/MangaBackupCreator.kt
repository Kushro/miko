package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupChapterBookmarkType
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPageBookmark
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupMergedMangaReferenceMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChapterBookmarkTypes
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetMangaRating
import tachiyomi.domain.manga.interactor.GetMangaTags
import tachiyomi.domain.manga.interactor.GetPageBookmarkPreviews
import tachiyomi.domain.manga.interactor.GetPageBookmarks
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val getCustomMangaInfo: GetCustomMangaInfo = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    // SY <--
    // MIKO -->
    private val getMangaTags: GetMangaTags = Injekt.get(),
    private val getMangaRating: GetMangaRating = Injekt.get(),
    private val getPageBookmarks: GetPageBookmarks = Injekt.get(),
    private val getChapterBookmarkTypes: GetChapterBookmarkTypes = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getPageBookmarkPreviews: GetPageBookmarkPreviews = Injekt.get(),
    // MIKO <--
) {

    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        return mangas.map {
            backupManga(it, options)
        }
    }

    private suspend fun backupManga(manga: Manga, options: BackupOptions): BackupManga {
        // Entry for this manga
        val mangaObject = manga.toBackupManga(
            // SY -->
            if (options.customInfo) {
                getCustomMangaInfo.get(manga.id)
            } else {
                null
            },
            // SY <--
        )

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            mangaObject.mergedMangaReferences = handler.awaitList {
                mergedQueries.selectByMergeId(manga.id, backupMergedMangaReferenceMapper)
            }
        }

        val source = sourceManager.get(manga.source)?.getMainSource<MetadataSource<*, *>>()
        if (source != null) {
            getFlatMetadataById.await(manga.id)?.let { flatMetadata ->
                mangaObject.flatMetadata = BackupFlatMetadata.copyFrom(flatMetadata)
            }
        }
        // SY <--

        mangaObject.excludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
        }

        // KMK -->
        mangaObject.scanlatorPriorities = handler.awaitList {
            scanlator_prioritiesQueries.getScanlatorPrioritiesByMangaId(manga.id)
        }
        // KMK <--

        // MIKO -->
        // Local tags travel as `namespace:name` (MangaTag.displayName) and are re-split on restore.
        mangaObject.localTags = getMangaTags.await(manga.id).map { it.displayName }
        mangaObject.localRating = getMangaRating.await(manga.id) ?: 0
        backupChapterBoundData(manga, mangaObject, options)
        // MIKO <--

        if (options.chapters) {
            // Backup all the chapters
            handler.awaitList {
                chaptersQueries.getChaptersByMangaId(
                    mangaId = manga.id,
                    applyFilter = 0, // false
                    // KMK -->
                    Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                    Manga.CHAPTER_SHOW_BOOKMARKED,
                    // KMK <--
                    mapper = backupChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { mangaObject.chapters = it }
        }

        if (options.categories) {
            // Backup categories for this manga
            val categoriesForManga = getCategories.await(manga.id)
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { manga_syncQueries.getTracksByMangaId(manga.id, backupTrackMapper) }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByMangaId = getHistory.await(manga.id)
            if (historyByMangaId.isNotEmpty()) {
                val history = historyByMangaId.map { history ->
                    val chapter = handler.awaitOne { chaptersQueries.getChapterById(history.chapterId) }
                    BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }

    // MIKO -->
    /**
     * The two pieces of local data that hang off a *chapter*: page bookmarks and the kind of each
     * chapter bookmark. Both identify their chapter by url so they can be re-attached on another
     * device, so the `chapterId -> url` map is resolved once and only when there is something to
     * write. Entries whose chapter is gone are skipped.
     *
     * When [BackupOptions.momentCaptures] is on, the persisted viewport capture of each bookmark
     * (D9) is fetched once for the whole manga and attached by bookmark id; bookmarks without a
     * capture keep [BackupPageBookmark.previewWebp] at its empty default.
     */
    private suspend fun backupChapterBoundData(manga: Manga, mangaObject: BackupManga, options: BackupOptions) {
        val bookmarks = getPageBookmarks.await(manga.id)
        // A missing row already means GENERIC, which BackupChapter.bookmark carries on its own.
        val bookmarkTypes = getChapterBookmarkTypes.await(manga.id)
            .filterValues { it != ChapterBookmarkType.GENERIC }
        if (bookmarks.isEmpty() && bookmarkTypes.isEmpty()) return

        val chapterUrlById = getChaptersByMangaId.await(manga.id).associate { it.id to it.url }
        val previewsByBookmarkId = if (options.momentCaptures) {
            getPageBookmarkPreviews.byMangaId(manga.id)
        } else {
            emptyMap()
        }

        bookmarks
            .mapNotNull { bookmark ->
                val chapterUrl = chapterUrlById[bookmark.chapterId] ?: return@mapNotNull null
                BackupPageBookmark(
                    chapterUrl = chapterUrl,
                    pageIndex = bookmark.pageIndex,
                    imageUrl = bookmark.imageUrl,
                    note = bookmark.note,
                    createdAt = bookmark.createdAt,
                    scrollFraction = bookmark.scrollFraction,
                    focusFraction = bookmark.focusFraction,
                    previewWebp = previewsByBookmarkId[bookmark.id] ?: ByteArray(0),
                )
            }
            .takeUnless(List<BackupPageBookmark>::isEmpty)
            ?.let { mangaObject.pageBookmarks = it }

        bookmarkTypes
            .mapNotNull { (chapterId, type) ->
                val chapterUrl = chapterUrlById[chapterId] ?: return@mapNotNull null
                BackupChapterBookmarkType(chapterUrl = chapterUrl, type = type.id)
            }
            .takeUnless(List<BackupChapterBookmarkType>::isEmpty)
            ?.let { mangaObject.chapterBookmarkTypes = it }
    }
    // MIKO <--
}

private fun Manga.toBackupManga(/* SY --> */customMangaInfo: CustomMangaInfo?/* SY <-- */) =
    BackupManga(
        url = this.url,
        // SY -->
        title = this.ogTitle,
        artist = this.ogArtist,
        author = this.ogAuthor,
        description = this.ogDescription,
        genre = this.ogGenre.orEmpty(),
        status = this.ogStatus.toInt(),
        thumbnailUrl = this.ogThumbnailUrl,
        // SY <--
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        notes = this.notes,
        initialized = this.initialized,
        memo = MemoColumnAdapter.encode(this.memo),
        // SY -->
    ).also { backupManga ->
        customMangaInfo?.let {
            backupManga.customTitle = it.title
            backupManga.customArtist = it.artist
            backupManga.customAuthor = it.author
            backupManga.customThumbnailUrl = it.thumbnailUrl
            backupManga.customDescription = it.description
            backupManga.customGenre = it.genre
            backupManga.customStatus = it.status?.toInt() ?: 0
        }
    }
// SY <--
