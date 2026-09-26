package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — picks the kind of a chapter bookmark. One radio row per [ChapterBookmarkType], each with
 * the glyph it produces (bookmark + exponent) and its short name; tapping a row selects it and
 * closes the dialog. Selecting a kind on a chapter that is not bookmarked yet bookmarks it (see
 * `SetChapterBookmarkType`), which is why the subtitle reads "Bookmark this chapter as…".
 *
 * @param current the kind currently set, or null when the selection is mixed / not bookmarked.
 * @param onRemoveBookmark when non-null, a "Remove bookmark" text button is shown as the negative
 *   action (used from places where the dialog is the only way to un-bookmark, e.g. Favorites).
 */
@Composable
fun ChapterBookmarkTypeDialog(
    current: ChapterBookmarkType?,
    onDismissRequest: () -> Unit,
    onSelect: (ChapterBookmarkType) -> Unit,
    onRemoveBookmark: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MKMR.strings.chapter_bookmark_type)) },
        text = {
            Column {
                Text(
                    text = stringResource(MKMR.strings.chapter_bookmark_type_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ChapterBookmarkType.entries.forEach { type ->
                    val selected = type == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .selectable(
                                selected = selected,
                                onClick = {
                                    onSelect(type)
                                    onDismissRequest()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        ChapterBookmarkTypeIcon(type = type, showTooltip = false)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(type.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        dismissButton = if (onRemoveBookmark != null) {
            {
                TextButton(
                    onClick = {
                        onRemoveBookmark()
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MKMR.strings.chapter_bookmark_remove))
                }
            }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
