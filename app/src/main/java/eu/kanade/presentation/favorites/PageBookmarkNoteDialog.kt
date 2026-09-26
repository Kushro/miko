package eu.kanade.presentation.favorites

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — writes the free-text note of a page bookmark (Favorites → Pages).
 *
 * Confirming with an empty field clears the note, so the same dialog covers "add", "edit" and
 * "remove"; the caller gets null in that case.
 */
@Composable
fun PageBookmarkNoteDialog(
    initialNote: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var note by rememberSaveable(initialNote) { mutableStateOf(initialNote.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MKMR.strings.page_bookmark_note)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(text = stringResource(MKMR.strings.page_bookmark_note_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(note.trim().ifEmpty { null })
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
    )
}
