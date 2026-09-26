package eu.kanade.presentation.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.zIndex
import eu.kanade.presentation.bookmarks.PageBookmarksContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.bookmarks.PageBookmarksScreenModel
import eu.kanade.tachiyomi.ui.favorites.BookmarkedChaptersScreenModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

private const val TAB_PAGES = 0
private const val TAB_CHAPTERS = 1

/**
 * MIKO — the "Favorites" section: everything the user flagged as worth coming back to, split in two
 * tabs — bookmarked **pages** (with their notes) and bookmarked **chapters**.
 *
 * The Scaffold/tabs skeleton is copied from `presentation/components/TabbedScreen.kt` instead of
 * reusing it: that component is wired to Browse's bulk-favorite and feed screen models.
 *
 * @param navigateUp null when hosted as a first-level tab (C18): the app bar shows no back arrow.
 */
@Composable
fun FavoritesScreen(
    pagesState: PageBookmarksScreenModel.State,
    chaptersState: BookmarkedChaptersScreenModel.State,
    navigateUp: (() -> Unit)?,
    onClickPageBookmark: (PageBookmarkWithRelations) -> Unit,
    onOpenPageBookmarkPreview: (PageBookmarkWithRelations) -> Unit,
    onDeletePageBookmark: (PageBookmarkWithRelations) -> Unit,
    onEditPageBookmarkNote: (PageBookmarkWithRelations) -> Unit,
    onClickDeleteAllPageBookmarks: () -> Unit,
    onClickChapter: (BookmarkedChapter) -> Unit,
    onLongClickChapter: (BookmarkedChapter) -> Unit,
    onChangeChapterTypeFilter: (ChapterBookmarkType?) -> Unit,
    onClickManga: (Long) -> Unit,
    // MIKO --> C20: the eye is always visible (both tabs share one hidden-source set, D12); the
    // overflow "delete all" stays Pages-only.
    onOpenSourceVisibility: () -> Unit,
    hasHiddenSources: Boolean,
    onCompressAllMoments: () -> Unit,
    onBackfillMoments: () -> Unit,
    // MIKO <--
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 2 }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(MKMR.strings.favorites),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MKMR.strings.action_source_visibility),
                                icon = Icons.Outlined.Visibility,
                                iconTint = if (hasHiddenSources) {
                                    MaterialTheme.colorScheme.active
                                } else {
                                    LocalContentColor.current
                                },
                                onClick = onOpenSourceVisibility,
                            ),
                        ),
                    )
                    if (pagerState.currentPage == TAB_PAGES && !pagesState.isEmpty) {
                        AppBarActions(
                            persistentListOf(
                                AppBar.OverflowAction(
                                    title = stringResource(MKMR.strings.page_bookmark_delete_all),
                                    onClick = onClickDeleteAllPageBookmarks,
                                ),
                            ),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.zIndex(1f),
            ) {
                Tab(
                    selected = pagerState.currentPage == TAB_PAGES,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_PAGES) } },
                    text = { TabText(text = stringResource(MKMR.strings.favorites_tab_pages)) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
                Tab(
                    selected = pagerState.currentPage == TAB_CHAPTERS,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_CHAPTERS) } },
                    text = { TabText(text = stringResource(MKMR.strings.favorites_tab_chapters)) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page ->
                val innerPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                when (page) {
                    TAB_PAGES -> PageBookmarksContent(
                        state = pagesState,
                        contentPadding = innerPadding,
                        onClickItem = onClickPageBookmark,
                        onDeleteItem = onDeletePageBookmark,
                        onEditNote = onEditPageBookmarkNote,
                        onClickManga = onClickManga,
                        onOpenPreview = onOpenPageBookmarkPreview,
                        onCompressAllMoments = onCompressAllMoments,
                        onBackfillMoments = onBackfillMoments,
                    )
                    else -> BookmarkedChaptersContent(
                        state = chaptersState,
                        contentPadding = innerPadding,
                        onClickItem = onClickChapter,
                        onLongClickItem = onLongClickChapter,
                        onClickManga = onClickManga,
                        onChangeTypeFilter = onChangeChapterTypeFilter,
                    )
                }
            }
        }
    }
}
