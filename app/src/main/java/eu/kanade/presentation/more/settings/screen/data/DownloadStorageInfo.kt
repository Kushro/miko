package eu.kanade.presentation.more.settings.screen.data

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Ratio of the quota past which the bar turns red. */
private const val NEAR_QUOTA_RATIO = 0.9f

/**
 * MIKO — reads the current download usage in bytes. `null` while it's still being computed.
 *
 * Companion of [DownloadStorageInfo], which shows that usage against the storage quota
 * (`DownloadPreferences.downloadStorageQuotaMb`). Modelled on `StorageInfo`, but reading the size
 * from `DownloadCache` instead of walking the filesystem.
 *
 * The value follows `DownloadCache.changes`, so it stays current while chapters are downloaded or
 * purged with the screen open.
 *
 * @param refreshKey changing it recomputes the value as well (used after editing the quota).
 */
@Composable
fun rememberDownloadStorageUsage(refreshKey: Any? = null): State<Long?> {
    val downloadManager = remember { Injekt.get<DownloadManager>() }
    val downloadCache = remember { Injekt.get<DownloadCache>() }
    return produceState<Long?>(null, refreshKey) {
        downloadCache.changes.collect {
            value = downloadManager.getTotalDownloadSize()
        }
    }
}

/**
 * @param usageBytes bytes taken by the downloads, or null while it's still being computed.
 * @param quotaBytes the quota in bytes; 0 means there is no quota.
 */
@Composable
fun DownloadStorageInfo(
    usageBytes: Long?,
    quotaBytes: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val usage = usageBytes ?: 0L
    val usageText = remember(usage) { Formatter.formatFileSize(context, usage) }
    val quotaText = remember(quotaBytes) { Formatter.formatFileSize(context, quotaBytes) }
    val ratio = if (quotaBytes > 0L) (usage.toFloat() / quotaBytes).coerceIn(0f, 1f) else 0f
    val overQuota = quotaBytes > 0L && usage > quotaBytes

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        if (quotaBytes > 0L) {
            LinearProgressIndicator(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .fillMaxWidth()
                    .height(12.dp),
                progress = { ratio },
                color = if (ratio >= NEAR_QUOTA_RATIO) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        Text(
            text = if (quotaBytes > 0L) {
                stringResource(MKMR.strings.download_storage_usage_of_quota, usageText, quotaText)
            } else {
                stringResource(MKMR.strings.download_storage_usage_no_quota, usageText)
            },
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.bodySmall,
        )

        if (overQuota) {
            Text(
                text = stringResource(MKMR.strings.download_storage_over_quota),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
