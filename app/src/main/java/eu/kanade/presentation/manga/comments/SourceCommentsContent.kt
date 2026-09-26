package eu.kanade.presentation.manga.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.enhancement.CommentsSort
import eu.kanade.domain.source.enhancement.SourceComment
import eu.kanade.tachiyomi.ui.manga.comments.SourceCommentsUiState
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * MIKO — the whole comment list: sort chips, the paged list itself and its loading/error/empty
 * states. Shared by the dedicated comments screen and the reader sheet, so it takes no navigation
 * and no source: everything comes from [state] and the callbacks.
 */
@Composable
fun SourceCommentsContent(
    state: SourceCommentsUiState,
    contentPadding: PaddingValues,
    onSortChange: (CommentsSort) -> Unit,
    onLoadMore: () -> Unit,
    onToggleReplies: (SourceComment) -> Unit,
    onRetry: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CommentsSortRow(
            sort = state.sort,
            total = state.total,
            onSortChange = onSortChange,
        )

        when {
            state.loading -> LoadingScreen()
            state.comments.isEmpty() && state.error != null -> CommentsErrorState(
                message = state.error,
                onRetry = onRetry,
            )
            state.comments.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.padding.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(MKMR.strings.comments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> CommentsList(
                state = state,
                contentPadding = contentPadding,
                onLoadMore = onLoadMore,
                onToggleReplies = onToggleReplies,
                onRetry = onRetry,
                onOpenImage = onOpenImage,
            )
        }
    }
}

@Composable
private fun CommentsSortRow(
    sort: CommentsSort,
    total: Int?,
    onSortChange: (CommentsSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        FilterChip(
            selected = sort == CommentsSort.TOP,
            onClick = { onSortChange(CommentsSort.TOP) },
            label = { Text(text = stringResource(MKMR.strings.comments_sort_top)) },
        )
        FilterChip(
            selected = sort == CommentsSort.NEWEST,
            onClick = { onSortChange(CommentsSort.NEWEST) },
            label = { Text(text = stringResource(MKMR.strings.comments_sort_newest)) },
        )
        if (total != null) {
            Text(
                text = stringResource(MKMR.strings.comments_count, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = MaterialTheme.padding.extraSmall),
            )
        }
    }
}

@Composable
private fun CommentsList(
    state: SourceCommentsUiState,
    contentPadding: PaddingValues,
    onLoadMore: () -> Unit,
    onToggleReplies: (SourceComment) -> Unit,
    onRetry: () -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Infinite scroll: the explicit "Load more" button below stays as the manual fallback for when
    // the auto-trigger already fired and failed.
    LaunchedEffect(listState, state.comments.size, state.hasNextPage) {
        if (!state.hasNextPage) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= state.comments.size - 1) onLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(
            items = state.comments,
            key = { "source-comment-${it.id}" },
        ) { comment ->
            CommentItem(
                comment = comment,
                depth = 0,
                expanded = comment.id in state.expandedReplies,
                loadingReplies = comment.id in state.loadingReplies,
                onToggleReplies = onToggleReplies,
                onOpenImage = onOpenImage,
            )
        }

        if (state.hasNextPage || state.error != null) {
            item(key = "source-comment-footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.loadingMore -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        state.error != null -> TextButton(onClick = onRetry) {
                            Text(text = stringResource(MR.strings.action_retry))
                        }
                        else -> TextButton(onClick = onLoadMore) {
                            Text(text = stringResource(MKMR.strings.comments_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = stringResource(MKMR.strings.comments_error),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))
        TextButton(onClick = onRetry) {
            Text(text = stringResource(MR.strings.action_retry))
        }
    }
}
