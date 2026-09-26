package eu.kanade.presentation.more.settings.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.system.toast
import exh.favorites.EhFavoritesSlotMapping
import exh.favorites.EhFavoritesSyncConfig
import exh.favorites.EhFavoritesUpstreamSlot
import exh.source.EXH_SOURCE_ID
import exh.source.ExhPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// MIKO -->
/**
 * Editor of the E-Hentai favorites mapping: one row per upstream slot (`favcat` 0..9) with an
 * enable switch and a local-category picker.
 *
 * The mapping is keyed by category **name** and only enabled + mapped slots take part in a sync;
 * see `docs/features/favorites-sync/en.md`. Mirrors the `FrontPageCategoriesDialog` mold of
 * `SettingsEhScreen`.
 */
@Composable
fun EhFavoritesMappingDialog(
    onDismissRequest: () -> Unit,
    exhPreferences: ExhPreferences,
    categories: List<Category>,
    initialValue: EhFavoritesSyncConfig,
    onValueChange: (EhFavoritesSyncConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val upstreamSlots by exhPreferences.exhFavoritesUpstreamSlots().collectAsState()
    var config by remember(initialValue) { mutableStateOf(initialValue) }
    var refreshing by remember { mutableStateOf(false) }

    val duplicates = config.duplicateCategoryNames

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MKMR.strings.eh_favorites_mapping_dialog_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(MKMR.strings.eh_favorites_mapping_dialog_message))

                (0 until EhFavoritesSyncConfig.SLOT_COUNT).forEach { slot ->
                    EhFavoritesMappingRow(
                        mapping = config.slotOf(slot),
                        upstream = upstreamSlots.firstOrNull { it.slot == slot },
                        categories = categories,
                        onEnabledChange = { enabled ->
                            config = config.withSlot(slot) { it.copy(enabled = enabled) }
                        },
                        onCategoryChange = { name ->
                            config = config.withSlot(slot) { it.copy(categoryName = name) }
                        },
                    )
                }

                if (duplicates.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            MKMR.strings.eh_favorites_mapping_duplicate,
                            duplicates.joinToString(", "),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        enabled = !refreshing,
                        onClick = {
                            refreshing = true
                            scope.launch {
                                try {
                                    exhPreferences.exhFavoritesUpstreamSlots()
                                        .set(fetchUpstreamSlots(context))
                                    context.toast(MKMR.strings.eh_favorites_mapping_refreshed)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    context.logcat(LogPriority.ERROR, e)
                                    context.toast(MKMR.strings.eh_favorites_mapping_refresh_failed)
                                } finally {
                                    refreshing = false
                                }
                            }
                        },
                    ) {
                        Text(text = stringResource(MKMR.strings.eh_favorites_mapping_refresh))
                    }
                    TextButton(
                        onClick = { config = EhFavoritesSyncConfig.byOrder(categories.map { it.name }) },
                    ) {
                        Text(text = stringResource(MKMR.strings.eh_favorites_mapping_auto))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = duplicates.isEmpty(),
                onClick = { onValueChange(config) },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun EhFavoritesMappingRow(
    mapping: EhFavoritesSlotMapping,
    upstream: EhFavoritesUpstreamSlot?,
    categories: List<Category>,
    onEnabledChange: (Boolean) -> Unit,
    onCategoryChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val slotLabel = stringResource(MKMR.strings.eh_favorites_mapping_slot, mapping.slot)
    val upstreamName = upstream?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(MKMR.strings.eh_favorites_mapping_upstream_unknown)

    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = "$slotLabel · $upstreamName",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (upstream != null) {
                    Text(
                        text = if (upstream.isEmpty) {
                            stringResource(MKMR.strings.eh_favorites_mapping_upstream_empty)
                        } else {
                            stringResource(MKMR.strings.eh_favorites_mapping_slot_count, upstream.count)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = mapping.enabled, onCheckedChange = onEnabledChange)
        }

        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = mapping.categoryName?.takeIf { it.isNotBlank() }
                        ?: stringResource(MKMR.strings.eh_favorites_mapping_no_category),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(MKMR.strings.eh_favorites_mapping_no_category)) },
                    onClick = {
                        expanded = false
                        onCategoryChange(null)
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            expanded = false
                            onCategoryChange(category.name)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Page 1 of `favorites.php` through the installed EXH source (same resolution the favorites sync
 * helper does), so the dialog can show upstream names and counts without running a full sync.
 */
private suspend fun fetchUpstreamSlots(context: Context): List<EhFavoritesUpstreamSlot> = withIOContext {
    val source = Injekt.get<SourceManager>().get(EXH_SOURCE_ID) as? EHentai
        ?: EHentai(0, true, context)
    source.fetchFavoriteSlots()
}
// MIKO <--
