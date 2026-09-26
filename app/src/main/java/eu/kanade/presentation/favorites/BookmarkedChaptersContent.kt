package eu.kanade.presentation.favorites

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterBookmarkTypeIcon
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.labelRes
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.favorites.BookmarkedChaptersScreenModel
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData

private val BookmarkedChapterHeaderCoverHeight = 48.dp
private val BookmarkedChapterItemHeight = 64.dp

/**
 * MIKO — every bookmarked chapter of the database, grouped by manga, with a chip row that narrows
 * the list down to a single [ChapterBookmarkType]. Rendered inside the "Chapters" tab of the
 * Favorites screen, which owns the Scaffold and the app bar.
 */
@Composable
fun BookmarkedChaptersContent(
    state: BookmarkedChaptersScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (BookmarkedChapter) -> Unit,
    onLongClickItem: (BookmarkedChapter) -> Unit,
    onClickManga: (Long) -> Unit,
    onChangeTypeFilter: (ChapterBookmarkType?) -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MKMR.strings.favorites_chapters_empty,
            modifier = Modifier.padding(contentPadding),
            help = {
                // MIKO --> C16: she finds the lack of bookmarked chapters mildly offensive
                // (C18: unless the mascot is turned off in Settings → Appearance)
                val showMikoChibis by remember { Injekt.get<UiPreferences>().showMikoChibis() }
                    .collectAsState()
                if (showMikoChibis) {
                    Image(
                        painter = painterResource(MikoExpression.POUTING.drawableRes),
                        contentDescription = stringResource(MikoExpression.POUTING.descriptionRes),
                        modifier = Modifier
                            .padding(top = MaterialTheme.padding.medium)
                            .height(MikoEmptyStateHeight),
                    )
                }
                // MIKO <--
                Text(
                    text = stringResource(MKMR.strings.favorites_chapters_empty_hint),
                    modifier = Modifier
                        .padding(top = MaterialTheme.padding.small)
                        .secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
        else -> {
            val uiModels = remember(state.items, state.typeFilter) { state.getUiModel() }
            val typeCounts = remember(state.items) { state.typeCounts }
            Column {
                BookmarkTypeFilterRow(
                    selected = state.typeFilter,
                    counts = typeCounts,
                    onSelect = onChangeTypeFilter,
                )
                FastScrollLazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding,
                ) {
                    items(
                        items = uiModels,
                        key = {
                            when (it) {
                                is BookmarkedChapterUiModel.Header -> "bookmarked-chapter-header-${it.mangaId}"
                                is BookmarkedChapterUiModel.Item -> "bookmarked-chapter-${it.item.chapterId}"
                            }
                        },
                        contentType = {
                            when (it) {
                                is BookmarkedChapterUiModel.Header -> "header"
                                is BookmarkedChapterUiModel.Item -> "item"
                            }
                        },
                    ) { model ->
                        when (model) {
                            is BookmarkedChapterUiModel.Header -> BookmarkedChapterHeader(
                                title = model.title,
                                coverData = model.coverData,
                                count = model.count,
                                onClick = { onClickManga(model.mangaId) },
                                modifier = Modifier.animateItemFastScroll(),
                            )
                            is BookmarkedChapterUiModel.Item -> BookmarkedChapterItem(
                                chapter = model.item,
                                onClick = { onClickItem(model.item) },
                                onLongClick = { onLongClickItem(model.item) },
                                modifier = Modifier.animateItemFastScroll(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkTypeFilterRow(
    selected: ChapterBookmarkType?,
    counts: Map<ChapterBookmarkType, Int>,
    onSelect: (ChapterBookmarkType?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.extraSmall,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(text = stringResource(MKMR.strings.favorites_filter_all), maxLines = 1) },
        )
        ChapterBookmarkType.entries.forEach { type ->
            val count = counts[type] ?: 0
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type.takeIf { it != selected }) },
                enabled = count > 0 || selected == type,
                label = { Text(text = "${stringResource(type.labelRes)} ($count)", maxLines = 1) },
                leadingIcon = {
                    ChapterBookmarkTypeIcon(
                        type = type,
                        size = 18.dp,
                        showTooltip = false,
                    )
                },
            )
        }
    }
}

@Composable
private fun BookmarkedChapterHeader(
    title: String,
    coverData: MangaCoverData,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            data = coverData,
            modifier = Modifier.height(BookmarkedChapterHeaderCoverHeight),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(MKMR.strings.favorites_chapters_count, count),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BookmarkedChapterItem(
    chapter: BookmarkedChapter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .heightIn(min = BookmarkedChapterItemHeight)
            .padding(
                start = MaterialTheme.padding.large,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row's own long-press opens the kind dialog, so the glyph must not steal it for a tooltip.
        ChapterBookmarkTypeIcon(type = chapter.type, showTooltip = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium),
        ) {
            Text(
                text = chapter.chapterName.ifBlank {
                    stringResource(MR.strings.display_mode_chapter, formatChapterNumber(chapter.chapterNumber))
                },
                modifier = if (chapter.read) Modifier.secondaryItemAlpha() else Modifier,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val scanlator = chapter.scanlator?.takeIf { it.isNotBlank() }
            if (scanlator != null || chapter.dateUpload > 0L) {
                Row(
                    modifier = Modifier.secondaryItemAlpha(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chapter.dateUpload > 0L) {
                        Text(
                            text = relativeDateText(chapter.dateUpload),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (scanlator != null) {
                        if (chapter.dateUpload > 0L) {
                            DotSeparatorText()
                        }
                        Text(
                            text = scanlator,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

sealed interface BookmarkedChapterUiModel {
    data class Header(
        val mangaId: Long,
        val title: String,
        val coverData: MangaCoverData,
        val count: Int,
    ) : BookmarkedChapterUiModel

    data class Item(val item: BookmarkedChapter) : BookmarkedChapterUiModel
}

// MIKO --> C16
internal val MikoEmptyStateHeight = 110.dp
// MIKO <--
