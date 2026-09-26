package eu.kanade.tachiyomi.ui.manga.comments

import eu.kanade.domain.source.enhancement.CommentsSort
import eu.kanade.domain.source.enhancement.SourceComment
import eu.kanade.domain.source.enhancement.SourceCommentsPage
import eu.kanade.domain.source.enhancement.SourceCommentsProvider
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap

/**
 * MIKO — paging/sorting state machine for one comment list, shared by the dedicated comments screen
 * and the reader sheet.
 *
 * It is deliberately **not** a `ScreenModel` nor a composable: the screen owns it through its own
 * `screenModelScope`, the reader dialog through a `rememberCoroutineScope()`, and both render the
 * same [SourceCommentsUiState] with the same composables.
 *
 * Which endpoint it talks to is fixed at construction time: series comments when [chapter] is null,
 * chapter comments otherwise.
 */
class SourceCommentsLoader(
    private val provider: SourceCommentsProvider,
    private val manga: SManga,
    private val chapter: SChapter?,
    private val scope: CoroutineScope,
) {

    private val mutableState = MutableStateFlow(SourceCommentsUiState())
    val state: StateFlow<SourceCommentsUiState> = mutableState.asStateFlow()

    /** 1-based index of the last page already merged into the state. */
    private var loadedPages = 0

    private var pageJob: Job? = null

    /** In-flight reply fetches by comment id; entries remove themselves on completion. */
    private val replyJobs = ConcurrentHashMap<String, Job>()

    /** (Re)loads the first page, dropping whatever was loaded before. */
    fun load() {
        pageJob?.cancel()
        // A reply fetch started against the previous list must not land on the new one.
        replyJobs.values.forEach { it.cancel() }
        replyJobs.clear()
        loadedPages = 0
        mutableState.update {
            it.copy(
                comments = emptyList(),
                loading = true,
                loadingMore = false,
                error = null,
                hasNextPage = false,
                total = null,
                expandedReplies = emptySet(),
                loadingReplies = emptySet(),
            )
        }
        pageJob = scope.launchIO {
            try {
                val page = fetch(1)
                loadedPages = 1
                mutableState.update {
                    it.copy(
                        comments = page.comments,
                        loading = false,
                        error = null,
                        hasNextPage = page.hasNextPage,
                        total = page.total,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to load comments" }
                mutableState.update { it.copy(loading = false, error = e.message ?: FALLBACK_ERROR) }
            }
        }
    }

    /** Appends the next page; a no-op while another page is in flight or when there is none left. */
    fun loadMore() {
        val current = mutableState.value
        if (current.loading || current.loadingMore || !current.hasNextPage) return
        if (pageJob?.isActive == true) return
        val next = loadedPages + 1
        mutableState.update { it.copy(loadingMore = true, error = null) }
        pageJob = scope.launchIO {
            try {
                val page = fetch(next)
                loadedPages = next
                mutableState.update { state ->
                    // The site can repeat a comment across pages when new ones arrive mid-scroll.
                    val known = state.comments.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        comments = state.comments + page.comments.filter { it.id !in known },
                        loadingMore = false,
                        hasNextPage = page.hasNextPage,
                        total = page.total ?: state.total,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to load more comments" }
                mutableState.update { it.copy(loadingMore = false, error = e.message ?: FALLBACK_ERROR) }
            }
        }
    }

    /** Switches the sort order and reloads from the first page. */
    fun setSort(sort: CommentsSort) {
        if (mutableState.value.sort == sort) return
        mutableState.update { it.copy(sort = sort) }
        load()
    }

    /**
     * Expands/collapses the replies of [comment], fetching them on first expansion when the site
     * only reported a count.
     */
    fun toggleReplies(comment: SourceComment) {
        val current = mutableState.value
        if (comment.id in current.expandedReplies) {
            mutableState.update { it.copy(expandedReplies = it.expandedReplies - comment.id) }
            return
        }
        mutableState.update { it.copy(expandedReplies = it.expandedReplies + comment.id) }

        if (comment.replies.isNotEmpty() || comment.replyCount <= 0) return
        if (replyJobs[comment.id]?.isActive == true) return
        mutableState.update { it.copy(loadingReplies = it.loadingReplies + comment.id) }
        val job = scope.launchIO {
            try {
                val replies = withIOContext { provider.getReplies(comment) }
                mutableState.update { state ->
                    state.copy(
                        comments = state.comments.map {
                            if (it.id == comment.id) it.copy(replies = replies) else it
                        },
                        loadingReplies = state.loadingReplies - comment.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to load replies of comment ${comment.id}" }
                mutableState.update {
                    it.copy(
                        loadingReplies = it.loadingReplies - comment.id,
                        expandedReplies = it.expandedReplies - comment.id,
                        error = e.message ?: FALLBACK_ERROR,
                    )
                }
            }
        }
        replyJobs[comment.id] = job
        job.invokeOnCompletion { replyJobs.remove(comment.id, job) }
    }

    /** Retries whatever failed: the first page when nothing loaded, the next one otherwise. */
    fun retry() {
        if (mutableState.value.comments.isEmpty()) {
            load()
        } else {
            mutableState.update { it.copy(error = null) }
            loadMore()
        }
    }

    private suspend fun fetch(page: Int): SourceCommentsPage = withIOContext {
        val sort = mutableState.value.sort
        if (chapter == null) {
            provider.getMangaComments(manga, page, sort)
        } else {
            provider.getChapterComments(manga, chapter, page, sort)
        }
    }

    private companion object {
        const val FALLBACK_ERROR = "error"
    }
}

/** Everything the comment composables need; see [SourceCommentsLoader]. */
data class SourceCommentsUiState(
    val comments: List<SourceComment> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasNextPage: Boolean = false,
    val total: Int? = null,
    val sort: CommentsSort = CommentsSort.TOP,
    /** Ids of the comments whose replies are shown. */
    val expandedReplies: Set<String> = emptySet(),
    /** Ids of the comments whose replies are being fetched. */
    val loadingReplies: Set<String> = emptySet(),
)
