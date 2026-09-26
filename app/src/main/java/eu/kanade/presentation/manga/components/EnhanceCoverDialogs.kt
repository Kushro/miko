package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — asks whether to upscale the cover on the remote server or on this device.
 *
 * Modelled on `ChapterBookmarkTypeDialog`: an [AlertDialog] with one selectable row per option.
 *
 * @param remoteAvailable null while the server is still being probed, false when there is no host
 * configured or it answered that it can't upscale — that row is then disabled rather than hidden,
 * so the option doesn't silently disappear and leave the user wondering.
 */
@Composable
fun EnhanceCoverChoiceDialog(
    remoteAvailable: Boolean?,
    onDismissRequest: () -> Unit,
    onSelect: (remote: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MKMR.strings.enhance_cover)) },
        text = {
            Column {
                Text(
                    text = stringResource(MKMR.strings.enhance_cover_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                EnhanceModeRow(
                    label = stringResource(KMR.strings.reader_enhancement_live),
                    enabled = true,
                    onClick = { onSelect(false) },
                )
                EnhanceModeRow(
                    label = stringResource(KMR.strings.reader_enhancement_remote),
                    secondaryLabel = when (remoteAvailable) {
                        null -> stringResource(MKMR.strings.enhance_cover_checking_server)
                        false -> stringResource(MKMR.strings.enhance_cover_server_unavailable)
                        true -> null
                    },
                    enabled = remoteAvailable == true,
                    onClick = { onSelect(true) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun EnhanceModeRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .selectable(selected = false, enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = false, enabled = enabled, onClick = null)
        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * MIKO — runs while the cover is being upscaled. Not dismissable by tapping away, like
 * `MigrationProgressDialog`; cancelling is explicit.
 *
 * @param status the upscaler's own description of the current step. The remote upscaler reports
 * these; the on-device one has nothing to say, so it stays null.
 * @param percent 0..100 when the on-device model reports progress. It is drawn as a bar only once
 * it actually moves — that counter is unused anywhere else in the app, so a spinner is the
 * baseline and the bar is the bonus, never a bar frozen at zero.
 */
@Composable
fun EnhanceCoverProgressDialog(
    status: String?,
    percent: Int?,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(text = stringResource(MKMR.strings.enhance_cover_in_progress)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (percent != null) {
                    val animated by animateFloatAsState(
                        targetValue = percent / 100f,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "enhance_cover_progress",
                    )
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(text = "$percent%", style = MaterialTheme.typography.bodySmall)
                } else {
                    CircularProgressIndicator()
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
