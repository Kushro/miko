package eu.kanade.presentation.bookmarks

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.favorites.MikoEmptyStateHeight
import eu.kanade.presentation.favorites.MikoExpression
import eu.kanade.presentation.favorites.MomentStorageCard
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.ui.bookmarks.PageBookmarksScreenModel
import tachiyomi.domain.manga.model.PageBookmarkPreviewCover
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
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
import kotlin.math.roundToInt
import tachiyomi.domain.manga.model.MangaCover as MangaCoverData

private val PageBookmarkItemHeight = 88.dp
private val PageBookmarkItemCoverHeight = 72.dp
private val PageBookmarkHeaderCoverHeight = 48.dp

/**
 * MIKO — list of every bookmarked page, grouped by manga (molded on the History screen). Rendered
 * inside the "Pages" tab of the Favorites screen, which owns the Scaffold and the app bar.
 */
@Composable
fun PageBookmarksContent(
    state: PageBookmarksScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (PageBookmarkWithRelations) -> Unit,
    onDeleteItem: (PageBookmarkWithRelations) -> Unit,
    onEditNote: (PageBookmarkWithRelations) -> Unit,
    onClickManga: (Long) -> Unit,
    // MIKO --> C15: long-press opens the page preview instead of deleting
    onOpenPreview: (PageBookmarkWithRelations) -> Unit,
    // MIKO <--
    // MIKO --> C20: storage stats card (D13)
    onCompressAllMoments: () -> Unit,
    onBackfillMoments: () -> Unit,
    // MIKO <--
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MKMR.strings.page_bookmarks_empty,
            modifier = Modifier.padding(contentPadding),
            help = {
                // MIKO --> C16: nothing bookmarked yet — she sighs about it
                // (C18: unless the mascot is turned off in Settings → Appearance)
                val showMikoChibis by remember { Injekt.get<UiPreferences>().showMikoChibis() }
                    .collectAsState()
                if (showMikoChibis) {
                    Image(
                        painter = painterResource(MikoExpression.SIGHING.drawableRes),
                        contentDescription = stringResource(MikoExpression.SIGHING.descriptionRes),
                        modifier = Modifier
                            .padding(top = MaterialTheme.padding.medium)
                            .height(MikoEmptyStateHeight),
                    )
                }
                // MIKO <--
                Text(
                    text = stringResource(MKMR.strings.page_bookmarks_empty_hint),
                    modifier = Modifier
                        .padding(top = MaterialTheme.padding.small)
                        .secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
        else -> {
            val uiModels = remember(state.items) { state.getUiModel() }
            FastScrollLazyColumn(
                contentPadding = contentPadding,
            ) {
                // MIKO --> C20/D13: storage stats + maintenance actions, above the list
                item(key = "moment-stats") {
                    MomentStorageCard(
                        stats = state.previewStats,
                        pendingCaptureCount = state.pendingCaptureCount,
                        opProgress = state.opProgress,
                        onCompressAll = onCompressAllMoments,
                        onBackfill = onBackfillMoments,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                }
                // MIKO <--
                items(
                    items = uiModels,
                    key = {
                        when (it) {
                            is PageBookmarkUiModel.Header -> "page-bookmark-header-${it.mangaId}"
                            is PageBookmarkUiModel.Item -> "page-bookmark-${it.item.bookmark.id}"
                        }
                    },
                    contentType = {
                        when (it) {
                            is PageBookmarkUiModel.Header -> "header"
                            is PageBookmarkUiModel.Item -> "item"
                        }
                    },
                ) { model ->
                    when (model) {
                        is PageBookmarkUiModel.Header -> PageBookmarkHeader(
                            title = model.title,
                            coverData = model.coverData,
                            count = model.count,
                            onClick = { onClickManga(model.mangaId) },
                            modifier = Modifier.animateItemFastScroll(),
                        )
                        is PageBookmarkUiModel.Item -> PageBookmarkItem(
                            bookmark = model.item,
                            onClick = { onClickItem(model.item) },
                            onDelete = { onDeleteItem(model.item) },
                            onEditNote = { onEditNote(model.item) },
                            onOpenPreview = { onOpenPreview(model.item) },
                            modifier = Modifier.animateItemFastScroll(),
                        )
                    }
                }
            }
        }
    }
}

/** Confirmation for "delete all page bookmarks". */
@Composable
fun PageBookmarkDeleteAllDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        text = { Text(text = stringResource(MKMR.strings.page_bookmark_delete_all_confirm)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

// MIKO --> C15
/** Confirmation for deleting a single page bookmark from its preview. */
@Composable
fun PageBookmarkDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        text = { Text(text = stringResource(MKMR.strings.page_bookmark_delete_confirm)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
// MIKO <--

@Composable
private fun PageBookmarkHeader(
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
            modifier = Modifier.height(PageBookmarkHeaderCoverHeight),
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
                text = stringResource(MKMR.strings.page_bookmarks_count, count),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PageBookmarkItem(
    bookmark: PageBookmarkWithRelations,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEditNote: () -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val chapterCache = remember { Injekt.get<ChapterCache>() }
    // Best-effort thumbnail: the cached page image while the reader still has it, the cover otherwise.
    val pageImage = remember(bookmark.bookmark.id, bookmark.bookmark.imageUrl) {
        bookmark.bookmark.imageUrl
            ?.takeIf { it.isNotEmpty() && chapterCache.isImageInCache(it) }
            ?.let { chapterCache.getImageFile(it) }
    }
    val note = bookmark.bookmark.note?.takeIf { it.isNotBlank() }
    // MIKO --> C20/D6: a persisted capture (Moments) already IS the exact recorded viewport, so it
    // wins over the old best-effort cascade and is always centered (no cropping needed); the
    // ChapterCache/cover fallback keeps its focus-bias behaviour for bookmarks with no capture yet.
    val thumbnail: Any = bookmark.previewUpdatedAt
        ?.let { PageBookmarkPreviewCover(bookmark.bookmark.id, it) }
        ?: pageImage
        ?: bookmark.coverData
    val focus = bookmark.bookmark.focusFraction ?: bookmark.bookmark.scrollFraction
    val thumbnailAlignment = if (bookmark.previewUpdatedAt == null && pageImage != null && focus != null) {
        // BiasAlignment's vertical bias runs -1 (top) .. +1 (bottom).
        BiasAlignment(horizontalBias = 0f, verticalBias = (focus * 2f - 1f).coerceIn(-1f, 1f))
    } else {
        Alignment.Center
    }
    // MIKO <--

    Row(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // MIKO --> C15: long-press opens the full-screen preview, not delete anymore.
                    onOpenPreview()
                    // MIKO <--
                },
            )
            .heightIn(min = PageBookmarkItemHeight)
            .padding(
                start = MaterialTheme.padding.large,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            data = thumbnail,
            modifier = Modifier.height(PageBookmarkItemCoverHeight),
            // MIKO -->
            alignment = thumbnailAlignment,
            // MIKO <--
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            Text(
                text = bookmark.chapterName.ifBlank {
                    stringResource(MR.strings.display_mode_chapter, formatChapterNumber(bookmark.chapterNumber))
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MKMR.strings.page_bookmark_page, bookmark.bookmark.pageIndex + 1),
                    style = MaterialTheme.typography.bodySmall,
                )
                // MIKO --> how far into the page it was bookmarked; only worth showing once the
                // reader actually got somewhere into it (long-strip pages only ever set this).
                if (focus != null && focus > 0.01f) {
                    DotSeparatorText()
                    Text(
                        text = "${(focus * 100).roundToInt()}%",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // MIKO <--
                if (bookmark.bookmark.createdAt > 0L) {
                    DotSeparatorText()
                    Text(
                        text = relativeDateText(bookmark.bookmark.createdAt),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // MIKO -->
            if (note != null) {
                Text(
                    text = note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // MIKO <--
        }
        IconButton(onClick = onEditNote) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = stringResource(
                    if (note == null) MKMR.strings.page_bookmark_add_note else MKMR.strings.page_bookmark_edit_note,
                ),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

sealed interface PageBookmarkUiModel {
    data class Header(
        val mangaId: Long,
        val title: String,
        val coverData: MangaCoverData,
        val count: Int,
    ) : PageBookmarkUiModel

    data class Item(val item: PageBookmarkWithRelations) : PageBookmarkUiModel
}
