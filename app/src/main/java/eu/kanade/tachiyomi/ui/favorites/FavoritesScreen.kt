package eu.kanade.tachiyomi.ui.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.bookmarks.PageBookmarkDeleteAllDialog
import eu.kanade.presentation.bookmarks.PageBookmarkDeleteDialog
import eu.kanade.presentation.components.SourceVisibilityDialog
import eu.kanade.presentation.favorites.FavoritesScreen
import eu.kanade.presentation.favorites.PageBookmarkNoteDialog
import eu.kanade.presentation.favorites.PageBookmarkPreviewDialog
import eu.kanade.presentation.manga.components.ChapterBookmarkTypeDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.bookmarks.PageBookmarksScreenModel
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MIKO — "Favorites" pushed as a plain screen: bookmarked pages (with their notes) and bookmarked
 * chapters, one tab each. Replaces the C9 "Page bookmarks" entry. Since C18 the primary entry is
 * [FavoritesTab] on the navigation bar; this Screen remains the pushed fallback (e.g. from a
 * launcher shortcut while another screen is open), so it keeps the back arrow.
 */
class FavoritesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        FavoritesScreenContent(navigateUp = navigator::pop)
    }
}

// MIKO --> C18: body shared by FavoritesScreen (pushed, back arrow) and FavoritesTab (first-level
// destination, no back arrow). Extension on Voyager's Screen so rememberScreenModel keys the
// screen models to whichever host composes it.
@Composable
internal fun cafe.adriel.voyager.core.screen.Screen.FavoritesScreenContent(
    navigateUp: (() -> Unit)?,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val pagesScreenModel = rememberScreenModel(tag = "pages") { PageBookmarksScreenModel() }
    val chaptersScreenModel = rememberScreenModel(tag = "chapters") { BookmarkedChaptersScreenModel() }
    val pagesState by pagesScreenModel.state.collectAsState()
    val chaptersState by chaptersScreenModel.state.collectAsState()
    // MIKO --> C20: presets are reactive at the routing site so a chip shows up without reopening
    // the dialog (they're shared with History, so another screen may add/remove one meanwhile).
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val sourceHidePresets by sourcePreferences.sourceHidePresets().collectAsState()
    // MIKO <--

    FavoritesScreen(
        pagesState = pagesState,
        chaptersState = chaptersState,
        navigateUp = navigateUp,
        onClickPageBookmark = { bookmark ->
            context.startActivity(
                ReaderActivity.newIntent(
                    context,
                    bookmark.bookmark.mangaId,
                    bookmark.bookmark.chapterId,
                    page = bookmark.bookmark.pageIndex,
                    // MIKO --> reopen where the page was left, not at its top
                    scrollFraction = bookmark.bookmark.scrollFraction,
                    // MIKO <--
                ),
            )
        },
        onOpenPageBookmarkPreview = pagesScreenModel::showPreview,
        onDeletePageBookmark = { pagesScreenModel.delete(it.bookmark.id) },
        onEditPageBookmarkNote = pagesScreenModel::showEditNoteDialog,
        onClickDeleteAllPageBookmarks = pagesScreenModel::showDeleteAllDialog,
        onClickChapter = { chapter ->
            context.startActivity(
                ReaderActivity.newIntent(context, chapter.mangaId, chapter.chapterId),
            )
        },
        onLongClickChapter = chaptersScreenModel::showChangeTypeDialog,
        onChangeChapterTypeFilter = chaptersScreenModel::setTypeFilter,
        onClickManga = { navigator.push(MangaScreen(it)) },
        // MIKO -->
        onOpenSourceVisibility = {
            pagesScreenModel.showSourceVisibilityDialog(chaptersScreenModel.sourceRecordCounts())
        },
        hasHiddenSources = pagesState.hiddenSourceIds.isNotEmpty(),
        onCompressAllMoments = { pagesScreenModel.compressAllMoments(context) },
        onBackfillMoments = { pagesScreenModel.backfillMoments(context) },
        // MIKO <--
    )

    when (val dialog = pagesState.dialog) {
        null -> {}
        PageBookmarksScreenModel.Dialog.DeleteAll -> {
            PageBookmarkDeleteAllDialog(
                onDismissRequest = pagesScreenModel::dismissDialog,
                onDelete = pagesScreenModel::deleteAll,
            )
        }
        is PageBookmarksScreenModel.Dialog.EditNote -> {
            PageBookmarkNoteDialog(
                initialNote = dialog.bookmark.bookmark.note,
                onDismissRequest = pagesScreenModel::dismissDialog,
                onConfirm = { note ->
                    pagesScreenModel.updateNote(dialog.bookmark.bookmark.id, note)
                    context.toast(MKMR.strings.page_bookmark_note_saved)
                },
            )
        }
        is PageBookmarksScreenModel.Dialog.Preview -> {
            // MIKO --> C15: full-screen page preview, own screen model per open bookmark
            // (same trick as MangaCoverScreenModel for MangaScreen's cover viewer).
            val live = pagesState.items.firstOrNull { it.bookmark.id == dialog.bookmark.bookmark.id }
            if (live == null) {
                // The bookmark was deleted from under the preview (e.g. from its own delete
                // confirmation) — close it, nothing else to show.
                LaunchedEffect(Unit) { pagesScreenModel.dismissDialog() }
            } else {
                val previewModel = rememberScreenModel(tag = "preview-${live.bookmark.id}") {
                    PageBookmarkPreviewScreenModel(live)
                }
                val previewState by previewModel.state.collectAsState()

                PageBookmarkPreviewDialog(
                    item = live,
                    image = previewState.image,
                    snackbarHostState = previewModel.snackbarHostState,
                    onDismissRequest = pagesScreenModel::dismissDialog,
                    onRetry = previewModel::retry,
                    onOpenInReader = {
                        context.startActivity(
                            ReaderActivity.newIntent(
                                context,
                                live.bookmark.mangaId,
                                live.bookmark.chapterId,
                                page = live.bookmark.pageIndex,
                                scrollFraction = live.bookmark.scrollFraction,
                            ),
                        )
                    },
                    onEditNote = previewModel::showEditNoteDialog,
                    onShareClick = { previewModel.shareImage(context) },
                    onSaveClick = { previewModel.saveImage(context) },
                    onDeleteClick = previewModel::showDeleteDialog,
                    onRecompressClick = { previewModel.recompressCapture(context) },
                    wink = previewState.wink,
                    retriedOnce = previewModel.retriedOnce,
                )

                // Layered on top of the preview, not instead of it: the tab's single `dialog`
                // slot is already taken by Preview.
                when (previewState.dialog) {
                    null -> {}
                    PageBookmarkPreviewScreenModel.Dialog.EditNote -> {
                        PageBookmarkNoteDialog(
                            initialNote = live.bookmark.note,
                            onDismissRequest = previewModel::dismissDialog,
                            onConfirm = { note ->
                                pagesScreenModel.updateNote(live.bookmark.id, note)
                                context.toast(MKMR.strings.page_bookmark_note_saved)
                                previewModel.flashWink()
                            },
                        )
                    }
                    PageBookmarkPreviewScreenModel.Dialog.ConfirmDelete -> {
                        PageBookmarkDeleteDialog(
                            onDismissRequest = previewModel::dismissDialog,
                            onDelete = { pagesScreenModel.delete(live.bookmark.id) },
                        )
                    }
                }
            }
            // MIKO <--
        }
        // MIKO -->
        is PageBookmarksScreenModel.Dialog.SourceVisibility -> {
            SourceVisibilityDialog(
                rows = dialog.rows,
                hiddenSourceIds = pagesState.hiddenSourceIds,
                presets = sourceHidePresets.presets.toImmutableList(),
                onDismissRequest = pagesScreenModel::dismissDialog,
                onConfirm = pagesScreenModel::confirmSourceVisibility,
                onSavePreset = pagesScreenModel::saveSourceHidePreset,
                onDeletePreset = pagesScreenModel::deleteSourceHidePreset,
            )
        }
        // MIKO <--
    }

    when (val dialog = chaptersState.dialog) {
        null -> {}
        is BookmarkedChaptersScreenModel.Dialog.ChangeType -> {
            ChapterBookmarkTypeDialog(
                current = dialog.chapter.type,
                onDismissRequest = chaptersScreenModel::dismissDialog,
                onSelect = { type -> chaptersScreenModel.setType(dialog.chapter.chapterId, type) },
                onRemoveBookmark = { chaptersScreenModel.removeBookmark(dialog.chapter.chapterId) },
            )
        }
    }
}
