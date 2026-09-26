package eu.kanade.tachiyomi.ui.favorites

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.favorites.BookmarkedChapterUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetBookmarkedChapters
import tachiyomi.domain.chapter.interactor.SetChapterBookmarkType
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.favorites.service.FavoritesPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MIKO — state for the Favorites → Chapters tab: every bookmarked chapter of the database, grouped
 * by manga, optionally narrowed down to one [ChapterBookmarkType].
 *
 * Chapters carry no "bookmarked at" timestamp, so the order is the repository's (manga title asc,
 * chapter number desc) and there is no sorting option.
 */
class BookmarkedChaptersScreenModel(
    private val getBookmarkedChapters: GetBookmarkedChapters = Injekt.get(),
    private val setChapterBookmarkType: SetChapterBookmarkType = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    // MIKO -->
    private val favoritesPreferences: FavoritesPreferences = Injekt.get(),
    // MIKO <--
) : StateScreenModel<BookmarkedChaptersScreenModel.State>(State()) {

    // MIKO -->
    /** Unfiltered snapshot, kept for the eye dialog's per-source record counts (D12). */
    private var rawItems: List<BookmarkedChapter> = emptyList()
    // MIKO <--

    init {
        screenModelScope.launchIO {
            // MIKO --> filter by the Moments-wide hidden-source set (D12: same pref as Pages).
            combine(
                getBookmarkedChapters.subscribeAll()
                    .catch { error ->
                        logcat(LogPriority.ERROR, error)
                        emit(emptyList())
                    },
                favoritesPreferences.hiddenSources().changes(),
            ) { chapters, hiddenRaw -> chapters to hiddenRaw.mapNotNull { it.toLongOrNull() }.toSet() }
                .collectLatest { (chapters, hidden) ->
                    rawItems = chapters
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = chapters.filter { c -> c.coverData.sourceId !in hidden }.toImmutableList(),
                        )
                    }
                }
            // MIKO <--
        }
    }

    // MIKO -->
    /** Per-source record counts of every bookmarked chapter, unfiltered — the eye dialog's rows. */
    fun sourceRecordCounts(): Map<Long, Int> = rawItems.groupingBy { it.coverData.sourceId }.eachCount()
    // MIKO <--

    fun setTypeFilter(type: ChapterBookmarkType?) {
        mutableState.update { it.copy(typeFilter = type) }
    }

    /** Also bookmarks the chapter if it somehow lost the flag meanwhile (see `SetChapterBookmarkType`). */
    fun setType(chapterId: Long, type: ChapterBookmarkType) {
        screenModelScope.launchIO {
            setChapterBookmarkType.await(chapterId, type)
        }
    }

    /** Drops the bookmark itself; the database trigger clears the kind row. */
    fun removeBookmark(chapterId: Long) {
        screenModelScope.launchIO {
            updateChapter.await(ChapterUpdate(id = chapterId, bookmark = false))
        }
    }

    fun showChangeTypeDialog(chapter: BookmarkedChapter) {
        mutableState.update { it.copy(dialog = Dialog.ChangeType(chapter)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<BookmarkedChapter> = persistentListOf(),
        val typeFilter: ChapterBookmarkType? = null,
        val dialog: Dialog? = null,
    ) {
        val isEmpty: Boolean
            get() = items.isEmpty()

        /** How many bookmarked chapters each kind holds, for the filter chips. */
        val typeCounts: Map<ChapterBookmarkType, Int>
            get() = items.groupingBy { it.type }.eachCount()

        private val filteredItems: List<BookmarkedChapter>
            get() = typeFilter?.let { type -> items.filter { it.type == type } } ?: items

        /**
         * Flattens the (filtered) chapters into a header-per-manga + its chapters, preserving the
         * repository order.
         */
        fun getUiModel(): List<BookmarkedChapterUiModel> {
            return filteredItems
                .groupBy { it.mangaId }
                .flatMap { (_, chapters) ->
                    val first = chapters.first()
                    buildList {
                        add(
                            BookmarkedChapterUiModel.Header(
                                mangaId = first.mangaId,
                                title = first.mangaTitle,
                                coverData = first.coverData,
                                count = chapters.size,
                            ),
                        )
                        chapters.forEach { add(BookmarkedChapterUiModel.Item(it)) }
                    }
                }
        }
    }

    sealed interface Dialog {
        data class ChangeType(val chapter: BookmarkedChapter) : Dialog
    }
}
