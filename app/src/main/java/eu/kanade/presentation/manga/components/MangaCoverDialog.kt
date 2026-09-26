package eu.kanade.presentation.manga.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updatePadding
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.source.local.isLocal

@Composable
fun MangaCoverDialog(
    manga: Manga,
    isCustomCover: Boolean,
    snackbarHostState: SnackbarHostState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    onDismissRequest: () -> Unit,
    // MIKO --> null hides the action, same as onEditClick: the result is stored as a custom cover,
    // which only exists for manga in the library.
    onEnhanceClick: (() -> Unit)? = null,
    // MIKO --> size of the cover file on disk, shown centered in the bottom bar; null hides it.
    coverSizeBytes: Long? = null,
    onRedownloadClick: (() -> Unit)? = null,
    onRecompressClick: (() -> Unit)? = null,
    // Called once the AndroidView finishes loading the (possibly freshly redownloaded) cover,
    // so the screen model can refresh coverSizeBytes from the file that was just written.
    onImageLoaded: () -> Unit = {},
    // MIKO <--
    // KMK -->
    modifier: Modifier = Modifier,
    // KMK <--
) {
    // KMK -->
    val iconColor = contentColorFor(MaterialTheme.colorScheme.secondaryContainer)
    val dropdownBgColor = MaterialTheme.colorScheme.surfaceVariant
    // KMK <--
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false, // Doesn't work https://issuetracker.google.com/issues/246909281
        ),
    ) {
        Scaffold(
            // KMK -->
            modifier = modifier,
            // KMK <--
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent,
            bottomBar = {
                // MIKO --> BoxWithConstraints: Row measures the two pills before the weighted centre,
                // so six 48dp icons on a 360dp phone would leave the size text ~16dp and it would just
                // vanish. Below the width where everything fits with room for the text, the three cover
                // maintenance actions fold into one "more" menu instead; wide screens show them all.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactActions = maxWidth < COMPACT_ACTIONS_MAX_WIDTH
                    // Redownload/recompress only make sense for a library entry with a network
                    // source: a local entry's cover is the user's own file. Same test as the logic.
                    val maintainable = manga.favorite && !manga.isLocal()
                    // MIKO <--
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
                                    // KMK -->
                                    tint = iconColor,
                                    // KMK <--
                                )
                            }
                        }
                        // MIKO --> centered cover file size, between the two pills
                        Box(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (coverSizeBytes != null) {
                                val context = LocalContext.current
                                val sizeText = remember(coverSizeBytes) {
                                    Formatter.formatFileSize(context, coverSizeBytes)
                                }
                                Text(
                                    text = sizeText,
                                    color = iconColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                        // MIKO <--
                        ActionsPill {
                            AppBarActions(
                                actions = persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_share),
                                        icon = Icons.Outlined.Share,
                                        onClick = onShareClick,
                                        // KMK -->
                                        iconTint = iconColor,
                                        // KMK <--
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_save),
                                        icon = Icons.Outlined.Save,
                                        onClick = onSaveClick,
                                        // KMK -->
                                        iconTint = iconColor,
                                        // KMK <--
                                    ),
                                ),
                            )
                            // MIKO --> cover maintenance: enhance / redownload / recompress
                            val showEnhance = onEnhanceClick != null && manga.favorite
                            val showRedownload = onRedownloadClick != null && maintainable
                            val showRecompress = onRecompressClick != null && maintainable
                            if (compactActions && (showEnhance || showRedownload || showRecompress)) {
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
                                        if (showEnhance) {
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MKMR.strings.enhance_cover)) },
                                                onClick = {
                                                    expanded = false
                                                    onEnhanceClick!!()
                                                },
                                                colors = itemColors,
                                            )
                                        }
                                        if (showRedownload) {
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MKMR.strings.action_redownload_cover)) },
                                                onClick = {
                                                    expanded = false
                                                    onRedownloadClick!!()
                                                },
                                                colors = itemColors,
                                            )
                                        }
                                        if (showRecompress) {
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(MKMR.strings.action_recompress_cover)) },
                                                onClick = {
                                                    expanded = false
                                                    onRecompressClick!!()
                                                },
                                                colors = itemColors,
                                            )
                                        }
                                    }
                                }
                            } else {
                                if (showEnhance) {
                                    IconButton(onClick = onEnhanceClick!!) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoFixHigh,
                                            contentDescription = stringResource(MKMR.strings.enhance_cover),
                                            tint = iconColor,
                                        )
                                    }
                                }
                                if (showRedownload) {
                                    IconButton(onClick = onRedownloadClick!!) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = stringResource(MKMR.strings.action_redownload_cover),
                                            tint = iconColor,
                                        )
                                    }
                                }
                                if (showRecompress) {
                                    IconButton(onClick = onRecompressClick!!) {
                                        Icon(
                                            imageVector = Icons.Outlined.Compress,
                                            contentDescription = stringResource(MKMR.strings.action_recompress_cover),
                                            tint = iconColor,
                                        )
                                    }
                                }
                            }
                            // MIKO <--
                            if (onEditClick != null && manga.favorite) {
                                Box {
                                    var expanded by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {
                                            if (isCustomCover) {
                                                expanded = true
                                            } else {
                                                onEditClick(EditCoverAction.EDIT)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(MR.strings.action_edit_cover),
                                            // KMK -->
                                            tint = iconColor,
                                            // KMK <--
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        offset = DpOffset(8.dp, 0.dp),
                                        // KMK -->
                                        modifier = Modifier.background(dropdownBgColor),
                                        // KMK <--
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_edit)) },
                                            onClick = {
                                                onEditClick(EditCoverAction.EDIT)
                                                expanded = false
                                            },
                                            // KMK -->
                                            colors = MenuDefaults.itemColors().copy(
                                                textColor = contentColorFor(dropdownBgColor),
                                            ),
                                            // KMK <--
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_delete)) },
                                            onClick = {
                                                onEditClick(EditCoverAction.DELETE)
                                                expanded = false
                                            },
                                            // KMK -->
                                            colors = MenuDefaults.itemColors().copy(
                                                textColor = contentColorFor(dropdownBgColor),
                                            ),
                                            // KMK <--
                                        )
                                    }
                                }
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
                AndroidView(
                    factory = {
                        ReaderPageImageView(it).apply {
                            onViewClicked = onDismissRequest
                            clipToPadding = false
                            clipChildren = false
                        }
                    },
                    update = { view ->
                        val request = ImageRequest.Builder(view.context)
                            .data(manga)
                            .size(Size.ORIGINAL)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .target { image ->
                                val drawable = image.asDrawable(view.context.resources)

                                // Copy bitmap in case it came from memory cache
                                // Because SSIV needs to thoroughly read the image
                                // KMK -->
                                val src = (drawable as? BitmapDrawable)?.bitmap
                                val config = src?.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
                                // KMK <--
                                val copy = src?.copy(config, false)
                                    ?.toDrawable(view.context.resources)
                                    ?: drawable
                                view.setImage(copy, ReaderPageImageView.Config(zoomDuration = 500))
                                // MIKO --> lets the screen model refresh coverSizeBytes once the
                                // (possibly just redownloaded) cover file is loaded from disk.
                                onImageLoaded()
                                // MIKO <--
                            }
                            .build()
                        view.context.imageLoader.enqueue(request)

                        view.updatePadding(top = statusBarPaddingPx, bottom = bottomPaddingPx)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// MIKO -->
/**
 * Below this width the bottom bar can't fit close + share/save + the three maintenance actions +
 * edit AND still leave room for the size text in the middle (48dp per icon, ~80dp for the text),
 * so the maintenance actions fold into one "more" menu.
 */
private val COMPACT_ACTIONS_MAX_WIDTH = 440.dp
// MIKO <--

@Composable
private fun ActionsPill(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            // KMK -->
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)),
        // KMK <--
    ) {
        content()
    }
}
