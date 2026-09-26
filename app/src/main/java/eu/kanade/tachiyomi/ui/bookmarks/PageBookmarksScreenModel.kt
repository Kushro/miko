package eu.kanade.tachiyomi.ui.bookmarks

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.bookmarks.PageBookmarkUiModel
import eu.kanade.presentation.components.SourceVisibilityRow
import eu.kanade.presentation.components.buildSourceVisibilityRows
import eu.kanade.tachiyomi.ui.favorites.PageBookmarkPageResolver
import eu.kanade.tachiyomi.util.bookmark.PageBookmarkPreviewCodec
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.favorites.service.FavoritesPreferences
import tachiyomi.domain.manga.interactor.GetPageBookmarkPreviews
import tachiyomi.domain.manga.interactor.GetPageBookmarks
import tachiyomi.domain.manga.interactor.SetPageBookmarkPreview
import tachiyomi.domain.manga.interactor.TogglePageBookmark
import tachiyomi.domain.manga.model.PageBookmarkPreviewStats
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.miko.MKMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max

/**
 * MIKO — state for the Favorites → Pages tab: every page bookmark, newest first, grouped by manga.
 */
class PageBookmarksScreenModel(
    private val getPageBookmarks: GetPageBookmarks = Injekt.get(),
    private val togglePageBookmark: TogglePageBookmark = Injekt.get(),
    // MIKO -->
    private val favoritesPreferences: FavoritesPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getPageBookmarkPreviews: GetPageBookmarkPreviews = Injekt.get(),
    private val setPageBookmarkPreview: SetPageBookmarkPreview = Injekt.get(),
    private val pageResolver: PageBookmarkPageResolver = PageBookmarkPageResolver(),
    // MIKO <--
) : StateScreenModel<PageBookmarksScreenModel.State>(State()) {

    // MIKO -->
    /**
     * Unfiltered snapshot of the last emission, kept for the eye dialog's per-source record
     * counts (D12: shown even for a source that is about to be hidden) and for the backfill
     * action, which must still see bookmarks whose source is currently hidden.
     */
    private var rawItems: List<PageBookmarkWithRelations> = emptyList()
    // MIKO <--

    init {
        screenModelScope.launchIO {
            // MIKO --> filter by the Moments-wide hidden-source set (D12: one set, both tabs).
            combine(
                getPageBookmarks.subscribeAll()
                    .catch { error ->
                        logcat(LogPriority.ERROR, error)
                        emit(emptyList())
                    },
                favoritesPreferences.hiddenSources().changes(),
            ) { bookmarks, hiddenRaw -> bookmarks to hiddenRaw.mapNotNull { it.toLongOrNull() }.toSet() }
                .collectLatest { (bookmarks, hidden) ->
                    rawItems = bookmarks
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = bookmarks.filter { b -> b.coverData.sourceId !in hidden }.toImmutableList(),
                            hiddenSourceIds = hidden,
                            pendingCaptureCount = bookmarks.count { b -> b.previewUpdatedAt == null },
                        )
                    }
                }
            // MIKO <--
        }
        // MIKO -->
        screenModelScope.launchIO {
            getPageBookmarkPreviews.subscribeStats()
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collectLatest { stats ->
                    mutableState.update { it.copy(previewStats = stats) }
                }
        }
        // MIKO <--
    }

    fun delete(id: Long) {
        screenModelScope.launchIO {
            togglePageBookmark.remove(id)
        }
    }

    fun deleteAll() {
        screenModelScope.launchIO {
            togglePageBookmark.removeAll()
        }
    }

    /** Writes (or clears, when [note] is null) the free-text note of a single bookmark. */
    fun updateNote(id: Long, note: String?) {
        screenModelScope.launchIO {
            togglePageBookmark.updateNote(id, note)
        }
    }

    fun showDeleteAllDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteAll) }
    }

    fun showEditNoteDialog(bookmark: PageBookmarkWithRelations) {
        mutableState.update { it.copy(dialog = Dialog.EditNote(bookmark)) }
    }

    /** C15 — opens the full-screen page preview (long-press on a row). */
    fun showPreview(bookmark: PageBookmarkWithRelations) {
        mutableState.update { it.copy(dialog = Dialog.Preview(bookmark)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    // MIKO -->
    /**
     * Opens the eye dialog (owner of the Moments-wide source-visibility state, D12). [chapterCounts]
     * are the Chapters tab's per-source record counts, computed unfiltered by the host from
     * `BookmarkedChaptersScreenModel` — one hidden set covers both tabs, so a source's row must
     * reflect bookmarks on either. Rows are built once, at open time, not on every recomposition.
     */
    fun showSourceVisibilityDialog(chapterCounts: Map<Long, Int>) {
        val pageCounts = rawItems.groupingBy { it.coverData.sourceId }.eachCount()
        val recordCounts = (pageCounts.keys + chapterCounts.keys).associateWith { sourceId ->
            (pageCounts[sourceId] ?: 0) + (chapterCounts[sourceId] ?: 0)
        }
        val rows = buildSourceVisibilityRows(sourceManager, recordCounts)
        mutableState.update { it.copy(dialog = Dialog.SourceVisibility(rows)) }
    }

    /** Persists the working hidden set from the eye dialog (only on its OK, D10). */
    fun confirmSourceVisibility(hiddenIds: Set<Long>) {
        favoritesPreferences.hiddenSources().set(hiddenIds.map(Long::toString).toSet())
    }

    /** Saves (or replaces, by name) a shared source-hide preset (D11). */
    fun saveSourceHidePreset(name: String, sourceIds: Set<Long>) {
        val pref = sourcePreferences.sourceHidePresets()
        pref.set(pref.get().save(name, sourceIds))
    }

    fun deleteSourceHidePreset(name: String) {
        val pref = sourcePreferences.sourceHidePresets()
        pref.set(pref.get().delete(name))
    }

    /**
     * D14 — bulk recompression: every stored capture, largest metadata list walked sequentially,
     * re-encoded at the fixed [PageBookmarkPreviewCodec.RECOMPRESS_WEBP_QUALITY] profile downscaled
     * to the screen's larger side. Each item's read-recompress-upsert runs inside its own
     * [NonCancellable] section (not the whole run) so a mid-run cancellation only risks the item in
     * flight. Reports the total bytes saved, or "no gain" when nothing shrank.
     */
    fun compressAllMoments(context: Context) {
        if (!tryStartOp(OpProgress.Kind.COMPRESS)) return
        val maxDimension = screenMaxDimension(context)
        screenModelScope.launchIO {
            val metas = getPageBookmarkPreviews.metas()
            if (metas.isEmpty()) {
                mutableState.update { it.copy(opProgress = null) }
                return@launchIO
            }
            var totalBefore = 0L
            var totalAfter = 0L
            var anyGain = false
            metas.forEachIndexed { index, meta ->
                mutableState.update { it.copy(opProgress = OpProgress(OpProgress.Kind.COMPRESS, index, metas.size)) }
                totalBefore += meta.sizeBytes
                val afterSize = withContext(NonCancellable) {
                    val bytes = getPageBookmarkPreviews.await(meta.bookmarkId) ?: return@withContext meta.sizeBytes
                    val recompressed = runCatching {
                        PageBookmarkPreviewCodec.recompress(
                            bytes,
                            PageBookmarkPreviewCodec.RECOMPRESS_WEBP_QUALITY,
                            maxDimension,
                        )
                    }.getOrNull()
                    if (recompressed != null) {
                        setPageBookmarkPreview.upsert(meta.bookmarkId, recompressed)
                        anyGain = true
                        recompressed.size.toLong()
                    } else {
                        meta.sizeBytes
                    }
                }
                totalAfter += afterSize
            }
            mutableState.update {
                it.copy(opProgress = OpProgress(OpProgress.Kind.COMPRESS, metas.size, metas.size))
            }
            withUIContext {
                if (anyGain) {
                    context.toast(
                        context.stringResource(
                            MKMR.strings.compress_moments_result,
                            Formatter.formatFileSize(context, totalBefore),
                            Formatter.formatFileSize(context, totalAfter),
                        ),
                    )
                } else {
                    context.toast(MKMR.strings.compress_moments_no_gain)
                }
            }
            mutableState.update { it.copy(opProgress = null) }
        }
    }

    /**
     * D14 — "generate missing captures": every bookmark with no persisted capture yet, resolved
     * through [PageBookmarkPageResolver] (the same cascade the preview falls back to) and encoded
     * at [PageBookmarkPreviewCodec.CAPTURE_WEBP_QUALITY]. A single item failing (page no longer
     * resolvable, decode failure) is counted and skipped — the result reports how many of the total
     * succeeded.
     */
    fun backfillMoments(context: Context) {
        if (!tryStartOp(OpProgress.Kind.BACKFILL)) return
        val maxDimension = screenMaxDimension(context)
        screenModelScope.launchIO {
            val pending = rawItems.filter { it.previewUpdatedAt == null }
            if (pending.isEmpty()) {
                mutableState.update { it.copy(opProgress = null) }
                return@launchIO
            }
            var succeeded = 0
            pending.forEachIndexed { index, item ->
                mutableState.update {
                    it.copy(opProgress = OpProgress(OpProgress.Kind.BACKFILL, index, pending.size))
                }
                val ok = withContext(NonCancellable) {
                    runCatching {
                        val openStream = pageResolver.resolveOpenStream(item.bookmark) ?: return@runCatching false
                        // Bounded decode: a tall webtoon/upscaled page decoded at full resolution
                        // allocates hundreds of MB and OOMs exactly on the pages worth capturing.
                        val bitmap = PageBookmarkPreviewCodec.decodeBounded(openStream, maxDimension)
                            ?: return@runCatching false
                        try {
                            val compressed = PageBookmarkPreviewCodec.compressBounded(
                                bitmap,
                                PageBookmarkPreviewCodec.CAPTURE_WEBP_QUALITY,
                            ) ?: return@runCatching false
                            setPageBookmarkPreview.upsert(item.bookmark.id, compressed)
                            true
                        } finally {
                            bitmap.recycle()
                        }
                    }.getOrElse { e ->
                        logcat(LogPriority.ERROR, e) { "Backfill failed for bookmark ${item.bookmark.id}" }
                        false
                    }
                }
                if (ok) succeeded++
            }
            mutableState.update {
                it.copy(opProgress = OpProgress(OpProgress.Kind.BACKFILL, pending.size, pending.size))
            }
            withUIContext {
                context.toast(
                    context.stringResource(MKMR.strings.backfill_moments_result, succeeded, pending.size),
                )
            }
            mutableState.update { it.copy(opProgress = null) }
        }
    }

    private fun screenMaxDimension(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * Atomically claims the single bulk-operation slot BEFORE launching (a plain check inside the
     * coroutine leaves a double-tap window where two runs start concurrently). The claimed
     * progress starts at 0/0; the real total replaces it on the first loop iteration.
     */
    private fun tryStartOp(kind: OpProgress.Kind): Boolean {
        var started = false
        mutableState.update { current ->
            if (current.opProgress != null) {
                started = false
                current
            } else {
                started = true
                current.copy(opProgress = OpProgress(kind, 0, 0))
            }
        }
        return started
    }
    // MIKO <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<PageBookmarkWithRelations> = persistentListOf(),
        val dialog: Dialog? = null,
        // MIKO -->
        /** Source ids hidden by the Moments eye (D12); tints the toolbar icon when non-empty. */
        val hiddenSourceIds: Set<Long> = emptySet(),
        /** Reactive storage stats of every persisted capture; null until the first emission. */
        val previewStats: PageBookmarkPreviewStats? = null,
        /** Bookmarks with no persisted capture yet (unfiltered by the eye) — gates the backfill action. */
        val pendingCaptureCount: Int = 0,
        /** Non-null while the bulk compression or the backfill is running (never both at once). */
        val opProgress: OpProgress? = null,
        // MIKO <--
    ) {
        val isEmpty: Boolean
            get() = items.isEmpty()

        /**
         * Flattens [items] into a header-per-manga + its bookmarks. [items] arrives ordered by
         * creation date (newest first), so a manga shows up where its newest bookmark is.
         */
        fun getUiModel(): List<PageBookmarkUiModel> {
            return items
                .groupBy { it.bookmark.mangaId }
                .flatMap { (_, bookmarks) ->
                    val first = bookmarks.first()
                    buildList {
                        add(
                            PageBookmarkUiModel.Header(
                                mangaId = first.bookmark.mangaId,
                                title = first.mangaTitle,
                                coverData = first.coverData,
                                count = bookmarks.size,
                            ),
                        )
                        bookmarks.forEach { add(PageBookmarkUiModel.Item(it)) }
                    }
                }
        }
    }

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class EditNote(val bookmark: PageBookmarkWithRelations) : Dialog

        /**
         * C15 — the page preview. [bookmark] is the snapshot at open time; the host re-reads the
         * live row from [State.items] by id so note edits show up in the preview immediately.
         */
        data class Preview(val bookmark: PageBookmarkWithRelations) : Dialog

        // MIKO -->
        /** The eye dialog; [rows] are built once at open time (see [showSourceVisibilityDialog]). */
        data class SourceVisibility(val rows: ImmutableList<SourceVisibilityRow>) : Dialog
        // MIKO <--
    }

    // MIKO -->
    /** D14 — progress of the running bulk compression or backfill, for the stats card. */
    data class OpProgress(val kind: Kind, val done: Int, val total: Int) {
        enum class Kind { COMPRESS, BACKFILL }
    }
    // MIKO <--
}
