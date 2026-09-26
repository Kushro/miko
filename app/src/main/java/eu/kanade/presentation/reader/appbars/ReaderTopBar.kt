package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.ChapterBookmarkTypeIcon
import eu.kanade.presentation.manga.components.labelRes
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderTopBar(
    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // MIKO -->
    pageBookmarked: Boolean = false,
    onTogglePageBookmarked: (() -> Unit)? = null,
    bookmarkType: ChapterBookmarkType = ChapterBookmarkType.GENERIC,
    onChangeBookmarkType: (() -> Unit)? = null,
    /** null when the source has no comments enhancement, which hides the button. */
    onOpenComments: (() -> Unit)? = null,
    // MIKO <--
) {
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = mangaTitle,
        subtitle = chapterTitle,
        navigateUp = navigateUp,
        actions = {
            AppBarActions(
                actions = persistentListOf<AppBar.AppBarAction>().builder()
                    .apply {
                        // MIKO --> the chapter bookmark is a compose action so a bookmarked chapter
                        // can show the kind's exponent glyph; the tap still toggles the bookmark
                        // and the tooltip names the kind (that is where the kind is readable, the
                        // exponent alone being tiny). The page bookmark below is a compose action
                        // too because AppBarActions draws every Action before every ActionCompose,
                        // and the pair has to keep its order.
                        // Comments come first so they sit at the left edge of the action group,
                        // right next to the bookmark pair.
                        onOpenComments?.let { onOpen ->
                            val commentsTitle = stringResource(MKMR.strings.comments)
                            add(
                                AppBar.ActionCompose(title = commentsTitle) {
                                    BarIconButton(onClick = onOpen) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Comment,
                                            contentDescription = commentsTitle,
                                        )
                                    }
                                },
                            )
                        }
                        add(
                            AppBar.ActionCompose(
                                title = if (bookmarked) {
                                    stringResource(bookmarkType.labelRes)
                                } else {
                                    stringResource(MR.strings.action_bookmark)
                                },
                            ) {
                                BarIconButton(onClick = onToggleBookmarked) {
                                    if (bookmarked) {
                                        ChapterBookmarkTypeIcon(
                                            type = bookmarkType,
                                            size = 24.dp,
                                            tint = LocalContentColor.current,
                                            showTooltip = false,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.BookmarkBorder,
                                            contentDescription = stringResource(MR.strings.action_bookmark),
                                        )
                                    }
                                }
                            },
                        )
                        // bookmark of the page on screen, next to the chapter one
                        onTogglePageBookmarked?.let { onToggle ->
                            val pageTitle = stringResource(
                                if (pageBookmarked) {
                                    MKMR.strings.unbookmark_page
                                } else {
                                    MKMR.strings.bookmark_page
                                },
                            )
                            add(
                                AppBar.ActionCompose(title = pageTitle) {
                                    BarIconButton(onClick = onToggle) {
                                        Icon(
                                            imageVector = if (pageBookmarked) {
                                                Icons.Outlined.BookmarkRemove
                                            } else {
                                                Icons.Outlined.BookmarkAdd
                                            },
                                            contentDescription = pageTitle,
                                        )
                                    }
                                },
                            )
                        }
                        onChangeBookmarkType?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MKMR.strings.chapter_bookmark_type_change),
                                    onClick = it,
                                ),
                            )
                        }
                        // MIKO <--
                        onOpenInWebView?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_open_in_web_view),
                                    onClick = it,
                                ),
                            )
                        }
                        onOpenInBrowser?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_open_in_browser),
                                    onClick = it,
                                ),
                            )
                        }
                        onShare?.let {
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.action_share),
                                    onClick = it,
                                ),
                            )
                        }
                    }
                    .build(),
            )
        },
    )
}

// MIKO --> [AppBar.ActionCompose] only centers its content, so the bookmark buttons bring their own
// touch target and ripple — what [AppBar.Action] gets for free from `IconButton`.
@Composable
private fun BarIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
// MIKO <--
