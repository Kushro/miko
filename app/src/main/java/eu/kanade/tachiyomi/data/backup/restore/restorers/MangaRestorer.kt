package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupChapterBookmarkType
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupPageBookmark
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChapterBookmarkTypes
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetChapterBookmarkType
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.GetMangaTags
import tachiyomi.domain.manga.interactor.GetPageBookmarks
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.interactor.SetMangaRating
import tachiyomi.domain.manga.interactor.SetMangaTags
import tachiyomi.domain.manga.interactor.SetPageBookmarkPreview
import tachiyomi.domain.manga.interactor.TogglePageBookmark
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.PageBookmark
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class MangaRestorer(
    private var isSync: Boolean = false,

    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    fetchInterval: FetchInterval = Injekt.get(),
    // SY -->
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
    private val insertFlatMetadata: InsertFlatMetadata = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    // SY <--
    // MIKO -->
    private val getMangaTags: GetMangaTags = Injekt.get(),
    private val setMangaTags: SetMangaTags = Injekt.get(),
    private val setMangaRating: SetMangaRating = Injekt.get(),
    private val getPageBookmarks: GetPageBookmarks = Injekt.get(),
    private val togglePageBookmark: TogglePageBookmark = Injekt.get(),
    private val getChapterBookmarkTypes: GetChapterBookmarkTypes = Injekt.get(),
    private val setChapterBookmarkType: SetChapterBookmarkType = Injekt.get(),
    private val setPageBookmarkPreview: SetPageBookmarkPreview = Injekt.get(),
    // MIKO <--
) {
    private var now = ZonedDateTime.now()
    private var currentFetchWindow = fetchInterval.getWindow(now)

    init {
        now = ZonedDateTime.now()
        currentFetchWindow = fetchInterval.getWindow(now)
    }

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = handler.awaitList { mangasQueries.getAllMangaSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupMangas
            .sortedWith(
                // KMK -->
                compareBy<BackupManga> { it.source == MERGED_SOURCE_ID }
                    // KMK <--
                    .then(compareBy { it.url in urlsBySource[it.source].orEmpty() })
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    /**
     * Restore a single manga
     */
    suspend fun restore(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {
            val dbManga = findExistingManga(backupManga)
            var manga = backupManga.getMangaImpl()
            // SY -->
            manga = EXHMigrations.migrateBackupEntry(manga)
            // SY <--
            val restoredManga = if (dbManga == null) {
                restoreNewManga(manga)
            } else {
                restoreExistingManga(manga, dbManga)
            }

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.chapters,
                categories = backupManga.categories,
                backupCategories = backupCategories,
                history = backupManga.history,
                tracks = backupManga.tracking,
                excludedScanlators = backupManga.excludedScanlators,
                // KMK -->
                scanlatorPriorities = backupManga.scanlatorPriorities,
                // KMK <--
                // MIKO -->
                localTags = backupManga.localTags,
                localRating = backupManga.localRating,
                pageBookmarks = backupManga.pageBookmarks,
                chapterBookmarkTypes = backupManga.chapterBookmarkTypes,
                // MIKO <--
                // SY -->
                mergedMangaReferences = backupManga.mergedMangaReferences,
                flatMetadata = backupManga.flatMetadata,
                customManga = backupManga.getCustomMangaInfo(),
                // SY <--
            )

            if (isSync) {
                mangasQueries.resetIsSyncing()
                chaptersQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingManga(backupManga: BackupManga): Manga? {
        return getMangaByUrlAndSourceId.await(backupManga.url, backupManga.source)
    }

    private suspend fun restoreExistingManga(manga: Manga, dbManga: Manga): Manga {
        return if (manga.version > dbManga.version) {
            updateManga(dbManga.copyFrom(manga).copy(id = dbManga.id))
        } else {
            updateManga(manga.copyFrom(dbManga).copy(id = dbManga.id))
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            // SY -->
            ogAuthor = newer.author,
            ogArtist = newer.artist,
            ogDescription = newer.description,
            ogGenre = newer.genre,
            ogThumbnailUrl = newer.thumbnailUrl,
            ogStatus = newer.status,
            // SY <--
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }

    suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre?.joinToString(separator = ", "),
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                mangaId = manga.id,
                updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = manga.version,
                isSyncing = 1,
                notes = manga.notes,
                memo = manga.memo.let(MemoColumnAdapter::encode),
            )
        }
        return manga
    }

    private suspend fun restoreNewManga(
        manga: Manga,
    ): Manga {
        return manga.copy(
            id = insertManga(manga),
        )
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull { backupChapter ->
                val chapter = backupChapter.toChapterImpl().copy(mangaId = manga.id)
                val dbChapter = dbChaptersByUrl[chapter.url]

                when {
                    dbChapter == null -> chapter // New chapter
                    chapter.forComparison() == dbChapter.forComparison() -> null // Same state; skip
                    else -> updateChapterBasedOnSyncState(chapter, dbChapter) // Update existed chapter
                }
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun updateChapterBasedOnSyncState(chapter: Chapter, dbChapter: Chapter): Chapter {
        return if (isSync) {
            chapter.copy(
                id = dbChapter.id,
                bookmark = chapter.bookmark || dbChapter.bookmark,
                read = chapter.read,
                lastPageRead = chapter.lastPageRead,
                // KMK -->
                sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
                // KMK <--
            )
        } else {
            chapter.copyFrom(dbChapter)
                // KMK -->
                .copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                    dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
                )
                // KMK <--
                .let {
                    when {
                        dbChapter.read && !it.read -> it.copy(read = true, lastPageRead = dbChapter.lastPageRead)
                        it.lastPageRead == 0L && dbChapter.lastPageRead != 0L -> it.copy(
                            lastPageRead = dbChapter.lastPageRead,
                        )
                        else -> it
                    }
                }
        }
    }

    private fun Chapter.forComparison() =
        this.copy(
            id = 0L,
            mangaId = 0L,
            dateFetch = 0L,
            // KMK -->
            // dateUpload = 0L, some time source loses dateUpload so we overwrite with backup
            // sourceOrder = 0L, although sourceOrder will be updated on refresh, we want to avoid order mixed up anyway
            // KMK <--
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.insert(
                    chapter.mangaId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastPageRead,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                    chapter.memo,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = null,
                    dateFetch = null,
                    // KMK -->
                    sourceOrder = chapter.sourceOrder,
                    dateUpload = chapter.dateUpload,
                    // KMK <--
                    chapterId = chapter.id,
                    version = chapter.version,
                    isSyncing = 1,
                    memo = chapter.memo.let(MemoColumnAdapter::encode),
                )
            }
        }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre,
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
                notes = manga.notes,
                memo = manga.memo,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        excludedScanlators: List<String>,
        // KMK -->
        scanlatorPriorities: List<String>,
        // KMK <--
        // MIKO -->
        localTags: List<String>,
        localRating: Int,
        pageBookmarks: List<BackupPageBookmark>,
        chapterBookmarkTypes: List<BackupChapterBookmarkType>,
        // MIKO <--
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaInfo?,
        // SY <--
    ): Manga {
        restoreCategories(manga, categories, backupCategories)
        restoreChapters(manga, chapters)
        restoreTracking(manga, tracks)
        restoreHistory(manga, history)
        restoreExcludedScanlators(manga, excludedScanlators)
        // KMK -->
        restoreScanlatorPriorities(manga, scanlatorPriorities)
        // KMK <--
        // MIKO -->
        // After restoreChapters(): page bookmarks and bookmark kinds are resolved against the
        // chapters just inserted.
        restoreLocalTags(manga, localTags)
        restoreLocalRating(manga, localRating)
        restorePageBookmarks(manga, pageBookmarks)
        restoreChapterBookmarkTypes(manga, chapterBookmarkTypes)
        // MIKO <--
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        // SY -->
        restoreMergedMangaReferencesForManga(manga.id, mergedMangaReferences)
        flatMetadata?.let { restoreFlatMetadata(manga.id, it) }
        restoreEditedInfo(customManga?.copy(id = manga.id))
        // SY <--

        return manga
    }

    /**
     * Restores the categories a manga is in.
     * Only if [backupCategories] is provided and user chooses to restore it.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }

        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val mangaCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(manga.id, dbCategory.id)
                }
            }
        }

        if (mangaCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(manga.id)
                mangaCategoriesToUpdate.forEach { (mangaId, categoryId) ->
                    mangas_categoriesQueries.insert(mangaId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(manga: Manga, backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            // KMK -->
            val dbHistory = handler.awaitList { historyQueries.getHistoryByChapterUrl(manga.id, history.url) }
                .firstOrNull()
            // KMK <--
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                // KMK -->
                val chapter = handler.awaitList { chaptersQueries.getChapterByUrlAndMangaId(history.url, manga.id) }
                    .firstOrNull()
                // KMK <--
                return@mapNotNull if (chapter == null) {
                    // Chapter doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(chapterId = chapter._id)
                }
            }

            // Update history entry
            item.copy(
                id = dbHistory._id,
                chapterId = dbHistory.chapter_id,
                readAt = max(item.readAt?.time ?: 0L, dbHistory.last_read?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbHistory.time_read) - dbHistory.time_read,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    manga_syncQueries.update(
                        track.mangaId,
                        track.trackerId,
                        track.remoteId,
                        track.libraryId,
                        track.title,
                        track.lastChapterRead,
                        track.totalChapters,
                        track.status,
                        track.score,
                        track.remoteUrl,
                        track.startDate,
                        track.finishDate,
                        track.private,
                        track.id,
                    )
                }
            }
        }
    }

    // SY -->
    /**
     * Restore the categories from Json
     *
     * @param mergeMangaId the merge manga for the references
     * @param backupMergedMangaReferences the list of backup manga references for the merged manga
     */
    private suspend fun restoreMergedMangaReferencesForManga(
        mergeMangaId: Long,
        backupMergedMangaReferences: List<BackupMergedMangaReference>,
    ) {
        // Get merged manga references from file and from db
        val dbMergedMangaReferences = handler.awaitList {
            mergedQueries.selectAll(MergedMangaMapper::map)
        }

        // Iterate over them
        backupMergedMangaReferences
            // KMK -->
            .map { EXHMigrations.migrateBackupMergedMangaReference(it) }
            // KMK <--
            .forEach { backupMergedMangaReference ->
                // If the backupMergedMangaReference isn't in the db,
                // remove the id and insert a new backupMergedMangaReference
                // Store the inserted id in the backupMergedMangaReference
                if (dbMergedMangaReferences.none {
                        backupMergedMangaReference.mergeUrl == it.mergeUrl &&
                            backupMergedMangaReference.mangaUrl == it.mangaUrl
                    }
                ) {
                    // Let the db assign the id
                    // KMK -->
                    val mergedManga = handler.awaitList {
                        // KMK <--
                        mangasQueries.getMangaByUrlAndSource(
                            backupMergedMangaReference.mangaUrl,
                            backupMergedMangaReference.mangaSourceId,
                            MangaMapper::mapManga,
                        )
                        // KMK -->
                    }.firstOrNull()
                        // KMK <--
                        ?: return@forEach
                    backupMergedMangaReference.getMergedMangaReference().run {
                        handler.await {
                            mergedQueries.insert(
                                infoManga = isInfoManga,
                                getChapterUpdates = getChapterUpdates,
                                chapterSortMode = chapterSortMode.toLong(),
                                chapterPriority = chapterPriority.toLong(),
                                downloadChapters = downloadChapters,
                                mergeId = mergeMangaId,
                                mergeUrl = mergeUrl,
                                mangaId = mergedManga.id,
                                mangaUrl = mangaUrl,
                                mangaSource = mangaSourceId,
                            )
                        }
                    }
                }
            }
    }

    private suspend fun restoreFlatMetadata(mangaId: Long, backupFlatMetadata: BackupFlatMetadata) {
        if (getFlatMetadataById.await(mangaId) == null) {
            insertFlatMetadata.await(backupFlatMetadata.getFlatMetadata(mangaId))
        }
    }

    private fun restoreEditedInfo(mangaJson: CustomMangaInfo?) {
        mangaJson ?: return
        setCustomMangaInfo.set(mangaJson)
    }

    private fun BackupManga.getCustomMangaInfo(): CustomMangaInfo? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customThumbnailUrl != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0
        ) {
            return CustomMangaInfo(
                id = 0L,
                title = customTitle,
                author = customAuthor,
                artist = customArtist,
                thumbnailUrl = customThumbnailUrl,
                description = customDescription,
                genre = customGenre,
                status = customStatus.takeUnless { it == 0 }?.toLong(),
            )
        }
        return null
    }
    // SY <--

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

    /**
     * Restores the excluded scanlators for the manga.
     *
     * @param manga the manga whose excluded scanlators have to be restored.
     * @param excludedScanlators the excluded scanlators to restore.
     */
    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
            // KMK -->
        }.toSet()
        val toInsert = excludedScanlators.toSet().subtract(existingExcludedScanlators)
        if (toInsert.isNotEmpty()) {
            handler.await(inTransaction = true) {
                // KMK <--
                toInsert.forEach {
                    excluded_scanlatorsQueries.insert(manga.id, it)
                }
            }
        }
    }

    // KMK -->
    /**
     * Restores the scanlator priority order for the manga. Unlike excluded scanlators, order
     * matters here, so we only seed it from the backup when nothing is configured locally yet
     * (never overwrite a priority order the user may have already customized on this device).
     *
     * @param manga the manga whose scanlator priorities have to be restored.
     * @param scanlatorPriorities the scanlator priority order to restore.
     */
    private suspend fun restoreScanlatorPriorities(manga: Manga, scanlatorPriorities: List<String>) {
        if (scanlatorPriorities.isEmpty()) return
        val existingPriorities = handler.awaitList {
            scanlator_prioritiesQueries.getScanlatorPrioritiesByMangaId(manga.id)
        }
        if (existingPriorities.isNotEmpty()) return
        handler.await(inTransaction = true) {
            scanlatorPriorities.forEachIndexed { index, scanlator ->
                scanlator_prioritiesQueries.insert(manga.id, scanlator, index.toLong())
            }
        }
    }
    // KMK <--

    // MIKO -->
    /**
     * Restores the local (user-defined) tags of a manga. Merges instead of overwriting: a tag the
     * user added on this device is never dropped because the backup predates it.
     *
     * @param manga the manga whose local tags have to be restored.
     * @param localTags the tags to restore, as `MangaTag.displayName` (`namespace:name`).
     */
    private suspend fun restoreLocalTags(manga: Manga, localTags: List<String>) {
        if (localTags.isEmpty()) return
        val existing = getMangaTags.await(manga.id).map { it.displayName }
        val merged = (existing + localTags)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (merged.size == existing.size) return
        setMangaTags.replaceAll(manga.id, merged)
    }

    /**
     * Restores the user's own rating. A backup without a rating (0) never clears a local one.
     *
     * @param manga the manga whose rating has to be restored.
     * @param localRating the 1..5 rating to restore, 0 when the backup carries none.
     */
    private suspend fun restoreLocalRating(manga: Manga, localRating: Int) {
        if (localRating <= 0) return
        setMangaRating.await(manga.id, localRating)
    }

    /**
     * Restores per-page bookmarks. Must run after [restoreChapters] because each bookmark names its
     * chapter by url; bookmarks whose chapter is missing are dropped, and pages already bookmarked
     * on this device keep their local row.
     *
     * @param manga the manga whose page bookmarks have to be restored.
     * @param pageBookmarks the bookmarks to restore.
     */
    private suspend fun restorePageBookmarks(manga: Manga, pageBookmarks: List<BackupPageBookmark>) {
        if (pageBookmarks.isEmpty()) return
        val chapterIdByUrl = getChaptersByMangaId.await(manga.id).associate { it.url to it.id }
        val existing = getPageBookmarks.await(manga.id)
        val seen = existing.mapTo(mutableSetOf<Pair<Long, Int>>()) { it.chapterId to it.pageIndex }

        val toAdd = pageBookmarks.mapNotNull { backupBookmark ->
            val chapterId = chapterIdByUrl[backupBookmark.chapterUrl] ?: return@mapNotNull null
            if (!seen.add(chapterId to backupBookmark.pageIndex)) return@mapNotNull null
            PageBookmark(
                id = 0L,
                mangaId = manga.id,
                chapterId = chapterId,
                pageIndex = backupBookmark.pageIndex,
                imageUrl = backupBookmark.imageUrl,
                note = backupBookmark.note,
                createdAt = backupBookmark.createdAt,
                scrollFraction = backupBookmark.scrollFraction,
                focusFraction = backupBookmark.focusFraction,
            )
        }
        // MIKO --> replaceAll is id-preserving (D8): rows already present keep their _id, so their
        // captures survive; only inserting new rows requires re-reading to learn the ids they got.
        val current = if (toAdd.isEmpty()) {
            existing
        } else {
            togglePageBookmark.replaceAll(manga.id, existing + toAdd)
            getPageBookmarks.await(manga.id)
        }
        restorePageBookmarkPreviews(pageBookmarks, chapterIdByUrl, current)
        // MIKO <--
    }

    /**
     * Restores the persisted viewport captures (C20) that travelled inside [pageBookmarks]
     * (`BackupPageBookmark.previewWebp`, D9). Must run after the bookmarks themselves are
     * committed: [current] carries the ids they now have in the database, keyed by
     * (chapterId, pageIndex), used to resolve each backup entry to the local bookmark it belongs
     * to. A blob over the CursorWindow-safe cap is skipped defensively rather than trusted from an
     * external file.
     */
    private suspend fun restorePageBookmarkPreviews(
        pageBookmarks: List<BackupPageBookmark>,
        chapterIdByUrl: Map<String, Long>,
        current: List<PageBookmark>,
    ) {
        val withPreview = pageBookmarks.filter { it.previewWebp.isNotEmpty() }
        if (withPreview.isEmpty()) return
        val idByChapterAndPage = current.associate { (it.chapterId to it.pageIndex) to it.id }

        withPreview.forEach { backupBookmark ->
            val chapterId = chapterIdByUrl[backupBookmark.chapterUrl] ?: return@forEach
            val bookmarkId = idByChapterAndPage[chapterId to backupBookmark.pageIndex] ?: return@forEach
            val bytes = backupBookmark.previewWebp
            if (bytes.size !in 1..MAX_RESTORED_PREVIEW_BYTES) return@forEach
            setPageBookmarkPreview.upsert(bookmarkId, bytes)
        }
    }

    /**
     * Restores the *kind* of each chapter bookmark. Must run after [restoreChapters] because each
     * entry names its chapter by url. Non-destructive: a chapter that already carries a kind on this
     * device keeps it, and chapters missing from the database are dropped. Writing a kind also marks
     * the chapter as bookmarked, which is what makes a restore on a device that never saw the
     * chapter as a favorite still end up correct.
     *
     * @param manga the manga whose chapter bookmark kinds have to be restored.
     * @param chapterBookmarkTypes the kinds to restore, keyed by chapter url.
     */
    private suspend fun restoreChapterBookmarkTypes(
        manga: Manga,
        chapterBookmarkTypes: List<BackupChapterBookmarkType>,
    ) {
        if (chapterBookmarkTypes.isEmpty()) return
        val chapterIdByUrl = getChaptersByMangaId.await(manga.id).associate { it.url to it.id }
        val existing = getChapterBookmarkTypes.await(manga.id)

        val claimed = mutableSetOf<Long>()
        val idsByType = mutableMapOf<ChapterBookmarkType, MutableList<Long>>()
        chapterBookmarkTypes.forEach { backupType ->
            val type = ChapterBookmarkType.fromId(backupType.type)
            if (type == ChapterBookmarkType.GENERIC) return@forEach
            val chapterId = chapterIdByUrl[backupType.chapterUrl] ?: return@forEach
            val local = existing[chapterId] ?: ChapterBookmarkType.GENERIC
            if (local != ChapterBookmarkType.GENERIC) return@forEach
            if (!claimed.add(chapterId)) return@forEach
            idsByType.getOrPut(type) { mutableListOf() }.add(chapterId)
        }

        idsByType.forEach { (type, chapterIds) ->
            setChapterBookmarkType.awaitAll(chapterIds, type)
        }
    }

    private companion object {
        /** Defensive skip cap for restored capture blobs (D9); the writer already caps at 1.5 MB. */
        const val MAX_RESTORED_PREVIEW_BYTES = 1_900_000
    }
    // MIKO <--
}
