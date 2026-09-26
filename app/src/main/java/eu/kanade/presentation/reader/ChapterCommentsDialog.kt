package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.enhancement.SourceCommentsProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.comments.SourceCommentsContent
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.comments.SourceCommentsLoader
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — the reader's comment sheet: the same list the dedicated screen shows, scoped to the chapter
 * being read. The loader lives in the composition (keyed by the chapter url) rather than in
 * `ReaderViewModel`, so closing the sheet drops the paging state with it.
 */
@Composable
fun ChapterCommentsDialog(
    onDismissRequest: () -> Unit,
    manga: SManga,
    chapter: SChapter,
    provider: SourceCommentsProvider,
    onOpenImage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loader = remember(chapter.url) {
        SourceCommentsLoader(
            provider = provider,
            manga = manga,
            chapter = chapter,
            scope = scope,
        )
    }
    val state by loader.state.collectAsState()

    LaunchedEffect(loader) { loader.load() }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.heightIn(min = SHEET_MIN_HEIGHT, max = SHEET_MAX_HEIGHT)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                        top = MaterialTheme.padding.medium,
                    ),
            ) {
                Text(
                    text = stringResource(MKMR.strings.comments_chapter),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SourceCommentsContent(
                state = state,
                contentPadding = PaddingValues(bottom = MaterialTheme.padding.medium),
                onSortChange = loader::setSort,
                onLoadMore = loader::loadMore,
                onToggleReplies = loader::toggleReplies,
                onRetry = loader::retry,
                onOpenImage = onOpenImage,
            )
        }
    }
}

private val SHEET_MIN_HEIGHT = 300.dp
private val SHEET_MAX_HEIGHT = 640.dp
