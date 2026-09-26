package eu.kanade.presentation.favorites

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.bookmarks.PageBookmarksScreenModel
import tachiyomi.domain.manga.model.PageBookmarkPreviewStats
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — C20: Moments storage stats card, molded on `UpscalingHubScreen.CacheCard`. Shown at the
 * top of the Pages tab (D13: `item(key = "moment-stats")` before the bookmark list). Reports live
 * capture count/bytes ([stats], reactive) and hosts the two maintenance actions — bulk
 * recompression and "generate missing captures" — which never run at the same time: [opProgress]
 * disables both buttons while either is in flight.
 */
@Composable
fun MomentStorageCard(
    stats: PageBookmarkPreviewStats?,
    pendingCaptureCount: Int,
    opProgress: PageBookmarksScreenModel.OpProgress?,
    onCompressAll: () -> Unit,
    onBackfill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmingCompress by remember { mutableStateOf(false) }
    val running = opProgress != null

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(MKMR.strings.moment_storage_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (stats != null && stats.count > 0) {
                    pluralStringResource(
                        MKMR.plurals.moment_storage_usage,
                        stats.count.toInt(),
                        Formatter.formatFileSize(context, stats.totalBytes),
                        stats.count.toInt(),
                    )
                } else {
                    stringResource(MKMR.strings.moment_storage_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (opProgress != null) {
                LinearProgressIndicator(
                    progress = {
                        if (opProgress.total == 0) 0f else opProgress.done / opProgress.total.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        if (opProgress.kind == PageBookmarksScreenModel.OpProgress.Kind.COMPRESS) {
                            MKMR.strings.compress_moments_running
                        } else {
                            MKMR.strings.backfill_moments_running
                        },
                        opProgress.done,
                        opProgress.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { confirmingCompress = true },
                    enabled = !running && stats != null && stats.count > 0,
                ) {
                    Text(text = stringResource(MKMR.strings.action_compress_moments))
                }
                if (pendingCaptureCount > 0) {
                    OutlinedButton(
                        onClick = onBackfill,
                        enabled = !running,
                    ) {
                        Text(text = stringResource(MKMR.strings.action_backfill_moments))
                    }
                }
            }
        }
    }

    if (confirmingCompress) {
        AlertDialog(
            onDismissRequest = { confirmingCompress = false },
            text = { Text(text = stringResource(MKMR.strings.compress_moments_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingCompress = false
                        onCompressAll()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCompress = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
