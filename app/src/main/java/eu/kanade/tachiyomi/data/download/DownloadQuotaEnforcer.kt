package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds

/**
 * MIKO — keeps the size of the downloads folder under the user's quota
 * ([DownloadPreferences.downloadStorageQuotaMb]) by deleting the oldest downloaded chapters first.
 *
 * Ported behaviour from Futon's `EnforceStorageQuotaUseCase`, with two differences: the size is read
 * from [DownloadCache] (memoized) instead of walking the whole tree on every call, and the deletion
 * goes through [DownloadManager.deleteChapters], so the existing protections (bookmarked chapters,
 * excluded categories) still apply.
 *
 * It runs (1) before starting a chapter, from `Downloader.downloadChapter`, and (2) debounced after
 * every finished chapter, via [requestEnforce].
 */
class DownloadQuotaEnforcer(
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val provider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getAllManga: GetAllManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) {

    /**
     * Resolved lazily on purpose: [DownloadManager] builds the `Downloader` that injects this class,
     * so asking Injekt for it eagerly would recurse while the singleton is still being built.
     */
    private val downloadManager: DownloadManager by injectLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val enforceRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        enforceRequests
            .debounce(ENFORCE_DEBOUNCE)
            .onEach {
                try {
                    enforce(queuedChapterIds())
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to enforce the download storage quota" }
                }
            }
            .launchIn(scope)
    }

    /**
     * Current size in bytes of the downloaded chapters.
     */
    suspend fun usage(): Long = downloadCache.getTotalDownloadSize()

    /**
     * The configured quota in bytes, or `0` when there is no quota.
     */
    fun quotaBytes(): Long {
        val quotaMb = downloadPreferences.downloadStorageQuotaMb().get()
        return if (quotaMb <= 0) 0L else quotaMb * BYTES_PER_MB
    }

    /**
     * Whether there is a quota and the downloads are already past it.
     */
    suspend fun isOverQuota(): Boolean {
        val quota = quotaBytes()
        return quota > 0L && usage() > quota
    }

    /**
     * Requests a debounced [enforce] run. Returns immediately; the queued chapters at the time the
     * run actually happens are excluded from the purge.
     */
    fun requestEnforce() {
        enforceRequests.tryEmit(Unit)
    }

    /**
     * Deletes the oldest downloaded chapters until the downloads fit inside the quota.
     *
     * @param skipChapterIds chapters that must never be purged (queued or downloading ones).
     * @return true when there is no quota or the downloads ended up under it, false when they are
     * still over the quota (nothing left to delete, or everything left is protected).
     */
    suspend fun enforce(skipChapterIds: Set<Long> = emptySet()): Boolean = withIOContext {
        val quota = quotaBytes()
        if (quota <= 0L) return@withIOContext true

        val usage = usage()
        if (usage <= quota) return@withIOContext true

        val mangaById = mutableMapOf<Long, Manga>()
        val chapterById = mutableMapOf<Long, Chapter>()
        val candidates = mutableListOf<PurgeCandidate>()

        getAllManga.await().forEach { manga ->
            if (downloadCache.getDownloadCount(manga) <= 0) return@forEach
            val source = sourceManager.get(manga.source) ?: return@forEach
            // Local entries aren't downloads, their files are the library itself
            if (source.isLocal()) return@forEach
            val mangaDir = provider.findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)
                ?: return@forEach

            getChaptersByMangaId.await(manga.id).forEach chapters@{ chapter ->
                if (chapter.id in skipChapterIds) return@chapters
                // findChapterDirs() drops the chapters without a directory, so the chapter <-> dir
                // pairing would be lost; resolve them one by one against the already found manga dir
                val chapterDir = provider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url)
                    .firstNotNullOfOrNull { mangaDir.findFile(it) }
                    ?: return@chapters

                mangaById[manga.id] = manga
                chapterById[chapter.id] = chapter
                candidates += PurgeCandidate(
                    chapterId = chapter.id,
                    mangaId = manga.id,
                    sizeBytes = chapterDir.recursiveSize(),
                    lastModified = chapterDir.lastModified().takeIf { it > 0L } ?: chapter.dateFetch,
                )
            }
        }

        val selected = selectChaptersToPurge(candidates, usage, quota)
        if (selected.isEmpty()) return@withIOContext false

        // Snapshot the count before the (asynchronous) deletions start, otherwise a fast deletion
        // would make the target too low and the wait below would always burn its whole timeout.
        val expectedCount = (downloadCache.getTotalDownloadCount() - selected.size).coerceAtLeast(0)

        selected.groupBy { it.mangaId }.forEach { (mangaId, group) ->
            val manga = mangaById[mangaId] ?: return@forEach
            val source = sourceManager.get(manga.source) ?: return@forEach
            val chapters = group.mapNotNull { chapterById[it.chapterId] }
            if (chapters.isEmpty()) return@forEach
            // Goes through DownloadManager so bookmarked chapters and excluded categories are kept
            downloadManager.deleteChapters(chapters, manga, source)
        }

        // deleteChapters() is fire-and-forget, wait (bounded) until the index reflects the deletions.
        // Protected chapters (bookmarked / excluded categories) never disappear, so this may run
        // into the timeout; that is the price of reusing DownloadManager's exclusion rules.
        withTimeoutOrNull(DELETION_SETTLE_TIMEOUT) {
            while (downloadCache.getTotalDownloadCount() > expectedCount) {
                delay(DELETION_SETTLE_INTERVAL)
            }
        }

        usage() <= quota
    }

    private fun queuedChapterIds(): Set<Long> =
        downloadManager.queueState.value.mapTo(mutableSetOf()) { it.chapter.id }

    private fun UniFile.recursiveSize(): Long = if (isDirectory) {
        listFiles().orEmpty().sumOf { it.recursiveSize() }
    } else {
        length().coerceAtLeast(0L)
    }

    companion object {
        private const val BYTES_PER_MB = 1024L * 1024L

        /** Wait this long after the last finished chapter before purging. */
        private val ENFORCE_DEBOUNCE = 5.seconds.inWholeMilliseconds

        private val DELETION_SETTLE_TIMEOUT = 5.seconds.inWholeMilliseconds
        private const val DELETION_SETTLE_INTERVAL = 200L
    }
}
