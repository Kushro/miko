package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SyncFavoritesWarningDialog(
    onDismissRequest: () -> Unit,
    onAccept: () -> Unit,
) {
    // MIKO --> the notes describe the slot mapping (Miko) instead of "the first 10 categories" (SY)
    val text = stringResource(MKMR.strings.eh_favorites_sync_notes_message)
    // MIKO <--
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(stringResource(SYMR.strings.favorites_sync_notes))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = text)
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}
