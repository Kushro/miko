package eu.kanade.presentation.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.updatePadding
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.ui.favorites.PageBookmarkPreviewScreenModel
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.source
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

/**
 * MIKO — C15: full-screen preview of one bookmarked page, opened by long-pressing a row of
 * Favorites → Pages. Molded on `MangaCoverDialog` (same `Dialog` properties, transparent
 * `Scaffold`, `ReaderPageImageView` inside an `AndroidView`, bottom pills of actions).
 *
 * Shows the page image zoomable (fit-to-width and centred on
 * `focusFraction ?: scrollFraction ?: 0f` when [image] is tall, whole page otherwise), and a small
 * Miko in a corner: thinking, with a comic thought bubble that holds the bookmark's note when there
 * is one; surprised ("ara, ara", hand to her mouth) without a bubble otherwise.
 *
 * @param item the **live** bookmark row (the host re-reads it by id), so an edited note updates the
 * bubble in place.
 * @param image what the screen model resolved so far: spinner, the image, or an error with retry.
 */
@Composable
fun PageBookmarkPreviewDialog(
    item: PageBookmarkWithRelations,
    image: PageBookmarkPreviewScreenModel.ImageState,
    snackbarHostState: SnackbarHostState,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onOpenInReader: () -> Unit,
    onEditNote: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    // MIKO --> C20/D15: only meaningful while the shown image came from the persisted capture
    // blob (ImageState.Ready.fromBlob) — the dialog gates visibility on that itself.
    onRecompressClick: () -> Unit = {},
    // MIKO <--
    // MIKO --> C16: transient wink (just saved/shared/noted) and "already retried" for the
    // loading pose; see MikoExpression.
    wink: Boolean = false,
    retriedOnce: Boolean = false,
    // MIKO <--
) {
    val iconColor = contentColorFor(MaterialTheme.colorScheme.secondaryContainer)
    val dropdownBgColor = MaterialTheme.colorScheme.surfaceVariant
    val isReady = image is PageBookmarkPreviewScreenModel.ImageState.Ready
    // MIKO --> D15: "compress this capture" only makes sense while the shown image is the
    // persisted capture blob, not the fallback cascade.
    val showRecompress = image is PageBookmarkPreviewScreenModel.ImageState.Ready && image.fromBlob
    // MIKO <--
    val focus = item.bookmark.focusFraction ?: item.bookmark.scrollFraction

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent,
            bottomBar = {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactActions = maxWidth < COMPACT_ACTIONS_MAX_WIDTH
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .navigationBarsPadding(),
                    ) {
                        ActionsPill {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(MR.strings.action_close),
                                    tint = iconColor,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(
                                        MKMR.strings.page_bookmark_page,
                                        item.bookmark.pageIndex + 1,
                                    ),
                                    color = iconColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                                if (focus != null) {
                                    Text(
                                        text = stringResource(
                                            MKMR.strings.page_bookmark_focus_percent,
                                            (focus * 100).roundToInt(),
                                        ),
                                        color = iconColor,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                    )
                                }
                            }
                        }
                        ActionsPill {
                            AppBarActions(
                                actions = persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MKMR.strings.page_bookmark_open_in_reader),
                                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                                        onClick = onOpenInReader,
                                        iconTint = iconColor,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(
                                            if (item.bookmark.note.isNullOrBlank()) {
                                                MKMR.strings.page_bookmark_add_note
                                            } else {
                                                MKMR.strings.page_bookmark_edit_note
                                            },
                                        ),
                                        icon = Icons.Outlined.EditNote,
                                        onClick = onEditNote,
                                        iconTint = iconColor,
                                    ),
                                ),
                            )
                            if (compactActions) {
                                Box {
                                    var expanded by remember { mutableStateOf(false) }
                                    IconButton(onClick = { expanded = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = stringResource(MR.strings.label_more),
                                            tint = iconColor,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        offset = DpOffset(8.dp, 0.dp),
                                        modifier = Modifier.background(dropdownBgColor),
                                    ) {
                                        val itemColors = MenuDefaults.itemColors().copy(
                                            textColor = contentColorFor(dropdownBgColor),
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_share)) },
                                            onClick = {
                                                expanded = false
                                                onShareClick()
                                            },
                                            enabled = isReady,
                                            colors = itemColors,
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_save)) },
                                            onClick = {
                                                expanded = false
                                                onSaveClick()
                                            },
                                            enabled = isReady,
                                            colors = itemColors,
                                        )
                                        // MIKO -->
                                        if (showRecompress) {
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MKMR.strings.action_recompress_moment)) },
                                                onClick = {
                                                    expanded = false
                                                    onRecompressClick()
                                                },
                                                colors = itemColors,
                                            )
                                        }
                                        // MIKO <--
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MKMR.strings.page_bookmark_delete)) },
                                            onClick = {
                                                expanded = false
                                                onDeleteClick()
                                            },
                                            colors = itemColors,
                                        )
                                    }
                                }
                            } else {
                                AppBarActions(
                                    actions = persistentListOf(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_share),
                                            icon = Icons.Outlined.Share,
                                            onClick = onShareClick,
                                            iconTint = iconColor,
                                            enabled = isReady,
                                        ),
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_save),
                                            icon = Icons.Outlined.Save,
                                            onClick = onSaveClick,
                                            iconTint = iconColor,
                                            enabled = isReady,
                                        ),
                                    ),
                                )
                                // MIKO -->
                                if (showRecompress) {
                                    IconButton(onClick = onRecompressClick) {
                                        Icon(
                                            imageVector = Icons.Outlined.Compress,
                                            contentDescription = stringResource(MKMR.strings.action_recompress_moment),
                                            tint = iconColor,
                                        )
                                    }
                                }
                                // MIKO <--
                                AppBarActions(
                                    actions = persistentListOf(
                                        AppBar.Action(
                                            title = stringResource(MKMR.strings.page_bookmark_delete),
                                            icon = Icons.Outlined.Delete,
                                            onClick = onDeleteClick,
                                            iconTint = iconColor,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { contentPadding ->
            val statusBarPaddingPx = with(LocalDensity.current) { contentPadding.calculateTopPadding().roundToPx() }
            val bottomPaddingPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().roundToPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickableNoIndication(onClick = onDismissRequest),
            ) {
                when (image) {
                    is PageBookmarkPreviewScreenModel.ImageState.Loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(MKMR.strings.page_bookmark_loading_page),
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    is PageBookmarkPreviewScreenModel.ImageState.Error -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = stringResource(MKMR.strings.page_bookmark_page_unavailable),
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            if (image.message != null) {
                                Text(
                                    text = image.message,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(text = stringResource(MR.strings.action_retry))
                            }
                        }
                    }
                    is PageBookmarkPreviewScreenModel.ImageState.Ready -> {
                        // Reading the stream (even just to hand SSIV an InputStream-backed source)
                        // touches disk/network, so it happens off the UI thread; setImage is only
                        // ever called once per Ready instance (imageSet), so a later recomposition
                        // (e.g. the note bubble updating) doesn't reset zoom/pan.
                        var bufferedSource by remember(image) { mutableStateOf<BufferedSource?>(null) }
                        LaunchedEffect(image) {
                            // The screen model resolved this image a moment (or, for a reused
                            // model, a while) ago; the ChapterCache is an LRU, so the file can be
                            // gone by now. Opening it is the check — if it fails, resolve again
                            // instead of crashing the dialog.
                            val opened = withContext(Dispatchers.IO) {
                                runCatching { image.openStream().source().buffer() }.getOrNull()
                            }
                            if (opened == null) onRetry() else bufferedSource = opened
                        }
                        val source = bufferedSource
                        if (source == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            val imageSet = remember(image) { mutableStateOf(false) }
                            AndroidView(
                                factory = {
                                    ReaderPageImageView(it).apply {
                                        onViewClicked = onDismissRequest
                                        clipToPadding = false
                                        clipChildren = false
                                    }
                                },
                                update = { view ->
                                    if (!imageSet.value) {
                                        imageSet.value = true
                                        view.setImage(
                                            source,
                                            image.isAnimated,
                                            ReaderPageImageView.Config(
                                                zoomDuration = 500,
                                                minimumScaleType = if (image.isTall) {
                                                    SCALE_TYPE_FIT_WIDTH
                                                } else {
                                                    SCALE_TYPE_CENTER_INSIDE
                                                },
                                            ),
                                        )
                                        if (image.isTall) {
                                            view.onImageLoaded = {
                                                view.centerOnFraction(focus ?: 0f)
                                            }
                                        }
                                    }
                                    view.updatePadding(top = statusBarPaddingPx, bottom = bottomPaddingPx)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                // MIKO --> C16: which face she makes. The wink overrides everything for a
                // moment; otherwise the pose follows the image state, and with no note the
                // positive pose is stable per bookmark (same bookmark, same face).
                val expression = when {
                    wink -> MikoExpression.WINK
                    image is PageBookmarkPreviewScreenModel.ImageState.Loading ->
                        if (retriedOnce) MikoExpression.DETERMINED else MikoExpression.CONFUSED
                    image is PageBookmarkPreviewScreenModel.ImageState.Error -> MikoExpression.CRYING
                    !item.bookmark.note.isNullOrBlank() -> MikoExpression.THINKING
                    else -> NO_NOTE_POSES[(item.bookmark.id % NO_NOTE_POSES.size).toInt()]
                }
                // C18: the mascot toggle only hides her drawing — the pose above still resolves
                // (it is pure state) and the note bubble keeps rendering inside MikoReaction.
                val showMikoChibis by remember { Injekt.get<UiPreferences>().showMikoChibis() }
                    .collectAsState()
                MikoReaction(
                    expression = expression,
                    note = item.bookmark.note,
                    showChibi = showMikoChibis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
                )
                // MIKO <--
            }
        }
    }
}

/** Same threshold as `MangaCoverDialog.COMPACT_ACTIONS_MAX_WIDTH`: below this the fold-away actions collapse into "more". */
private val COMPACT_ACTIONS_MAX_WIDTH = 440.dp

// MIKO --> C16: the positive poses a note-less bookmark picks from, by id (ids are >= 1).
// WINK is deliberately NOT here: it is the transient save/share/note reward, and a bookmark
// resting on a wink would make that reward invisible (review finding, C16).
private val NO_NOTE_POSES = listOf(
    MikoExpression.ARA_ARA,
    MikoExpression.EXCITED,
    MikoExpression.AMAZED,
)
// MIKO <--

@Composable
private fun ActionsPill(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)),
    ) {
        content()
    }
}
