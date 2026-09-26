package eu.kanade.presentation.manga.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

// MIKO --> redownloading discards the cover currently on disk (which may be a custom or
// AI-enhanced one) and is not reversible, so we confirm before firing it off.
/** Confirmation for "redownload cover", shown before discarding the current cover file. */
@Composable
fun RedownloadCoverConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(MKMR.strings.redownload_cover_confirm_title)) },
        text = { Text(text = stringResource(MKMR.strings.redownload_cover_confirm_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
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
