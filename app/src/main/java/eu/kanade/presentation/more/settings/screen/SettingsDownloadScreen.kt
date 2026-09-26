package eu.kanade.presentation.more.settings.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.DownloadStorageInfo
import eu.kanade.presentation.more.settings.screen.data.rememberDownloadStorageUsage
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.data.download.formatMinutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDownloadScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsDownloadScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val parallelSourceLimit by downloadPreferences.parallelSourceLimit().collectAsState()
        val parallelPageLimit by downloadPreferences.parallelPageLimit().collectAsState()
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.saveChaptersAsCBZ(),
                title = stringResource(MR.strings.save_chapter_as_cbz),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.splitTallImages(),
                title = stringResource(MR.strings.split_tall_images),
                subtitle = stringResource(MR.strings.split_tall_images_summary),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = parallelSourceLimit,
                valueRange = 1..10,
                title = stringResource(MR.strings.pref_download_concurrent_sources),
                onValueChanged = { downloadPreferences.parallelSourceLimit().set(it) },
            ),
            Preference.PreferenceItem.SliderPreference(
                value = parallelPageLimit,
                valueRange = 1..15,
                title = stringResource(MR.strings.pref_download_concurrent_pages),
                subtitle = stringResource(MR.strings.pref_download_concurrent_pages_summary),
                onValueChanged = { downloadPreferences.parallelPageLimit().set(it) },
            ),
            // MIKO -->
            getSchedulingGroup(downloadPreferences = downloadPreferences),
            // MIKO <--
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            // KMK -->
            getDownloadCacheRenewInterval(downloadPreferences = downloadPreferences),
            // KMK <--
        )
    }

    // MIKO -->
    /** Which edge of the off-peak window the time picker is currently editing. */
    private enum class OffPeakEdge { Start, End }

    /**
     * Scheduling restrictions, speed limit and storage quota for downloads (C4, ported from Futon).
     */
    @Composable
    private fun getSchedulingGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val offPeakEnabledPref = downloadPreferences.downloadOffPeakEnabled()
        val offPeakStartPref = downloadPreferences.downloadOffPeakStartMinutes()
        val offPeakEndPref = downloadPreferences.downloadOffPeakEndMinutes()
        val quotaPref = downloadPreferences.downloadStorageQuotaMb()

        val offPeakEnabled by offPeakEnabledPref.collectAsState()
        val offPeakStart by offPeakStartPref.collectAsState()
        val offPeakEnd by offPeakEndPref.collectAsState()
        val quotaMb by quotaPref.collectAsState()

        // Recomputed whenever the quota changes so the usage card doesn't go stale after an edit
        val usageBytes by rememberDownloadStorageUsage(quotaMb)

        var editingEdge by remember { mutableStateOf<OffPeakEdge?>(null) }

        editingEdge?.let { edge ->
            OffPeakTimePickerDialog(
                title = if (edge == OffPeakEdge.Start) {
                    stringResource(MKMR.strings.pref_download_off_peak_start)
                } else {
                    stringResource(MKMR.strings.pref_download_off_peak_end)
                },
                initialMinutes = if (edge == OffPeakEdge.Start) offPeakStart else offPeakEnd,
                onDismissRequest = { editingEdge = null },
                onConfirm = { minutes ->
                    if (edge == OffPeakEdge.Start) {
                        offPeakStartPref.set(minutes)
                    } else {
                        offPeakEndPref.set(minutes)
                    }
                    editingEdge = null
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MKMR.strings.pref_category_download_scheduling),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadOnlyWhenCharging(),
                    title = stringResource(MKMR.strings.pref_download_only_when_charging),
                    subtitle = stringResource(MKMR.strings.pref_download_only_when_charging_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = offPeakEnabledPref,
                    title = stringResource(MKMR.strings.pref_download_off_peak),
                    subtitle = stringResource(MKMR.strings.pref_download_off_peak_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MKMR.strings.pref_download_off_peak_start),
                    subtitle = formatMinutes(offPeakStart),
                    enabled = offPeakEnabled,
                    onClick = { editingEdge = OffPeakEdge.Start },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MKMR.strings.pref_download_off_peak_end),
                    subtitle = formatMinutes(offPeakEnd),
                    enabled = offPeakEnabled,
                    onClick = { editingEdge = OffPeakEdge.End },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.downloadSpeedLimitKbps(),
                    entries = SPEED_LIMIT_VALUES
                        .associateWith { kbps ->
                            if (kbps == 0) {
                                stringResource(MKMR.strings.download_speed_limit_unlimited)
                            } else {
                                stringResource(MKMR.strings.download_speed_limit_value, kbps.toString())
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MKMR.strings.pref_download_speed_limit),
                    subtitle = stringResource(MKMR.strings.pref_download_speed_limit_summary) + "\n%s",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MKMR.strings.pref_download_storage_quota),
                ) {
                    DownloadStorageQuotaPreference(
                        quotaMb = quotaMb,
                        usageBytes = usageBytes,
                        onQuotaChanged = { quotaPref.set(it) },
                    )
                },
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MKMR.strings.download_storage_usage),
                ) {
                    BasePreferenceWidget(
                        title = stringResource(MKMR.strings.download_storage_usage),
                        subcomponent = {
                            DownloadStorageInfo(
                                usageBytes = usageBytes,
                                quotaBytes = quotaMb.toLong() * BYTES_PER_MB,
                                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                            )
                        },
                    )
                },
            ),
        )
    }

    /**
     * Storage quota entry: opens a numeric dialog and refuses a quota below the current usage, the
     * same guard Futon's settings screen has.
     */
    @Composable
    private fun DownloadStorageQuotaPreference(
        quotaMb: Int,
        usageBytes: Long?,
        onQuotaChanged: (Int) -> Unit,
    ) {
        val context = LocalContext.current
        var showDialog by rememberSaveable { mutableStateOf(false) }

        TextPreferenceWidget(
            title = stringResource(MKMR.strings.pref_download_storage_quota),
            subtitle = if (quotaMb > 0) {
                Formatter.formatFileSize(context, quotaMb.toLong() * BYTES_PER_MB)
            } else {
                stringResource(MKMR.strings.pref_download_storage_quota_summary)
            },
            onPreferenceClick = { showDialog = true },
        )

        if (!showDialog) return

        val usage = usageBytes ?: 0L
        val tooLowMessage = stringResource(
            MKMR.strings.download_storage_quota_too_low,
            Formatter.formatFileSize(context, usage),
        )
        var text by rememberSaveable { mutableStateOf(if (quotaMb > 0) quotaMb.toString() else "") }
        var showTooLow by remember { mutableStateOf(false) }
        val onDismissRequest = { showDialog = false }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(MKMR.strings.pref_download_storage_quota)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { value ->
                            text = value.filter { it.isDigit() }.take(MAX_QUOTA_DIGITS)
                            showTooLow = false
                        },
                        label = { Text(text = stringResource(MKMR.strings.download_storage_quota_dialog_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = showTooLow,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showTooLow) {
                        Text(
                            text = tooLowMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newQuotaMb = text.toIntOrNull() ?: 0
                        if (newQuotaMb > 0 && newQuotaMb.toLong() * BYTES_PER_MB < usage) {
                            showTooLow = true
                        } else {
                            onQuotaChanged(newQuotaMb)
                            onDismissRequest()
                        }
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
        )
    }

    /**
     * 24 h Compose time picker; the value is stored as minutes since local midnight.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OffPeakTimePickerDialog(
        title: String,
        initialMinutes: Int,
        onDismissRequest: () -> Unit,
        onConfirm: (Int) -> Unit,
    ) {
        val state = rememberTimePickerState(
            initialHour = (initialMinutes / 60).coerceIn(0, 23),
            initialMinute = (initialMinutes % 60).coerceIn(0, 59),
            is24Hour = true,
        )

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = { TimePicker(state = state) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {
                TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
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
    // MIKO <--

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(MR.strings.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.removeAfterReadSlots(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(MR.strings.last_read_chapter),
                        1 to stringResource(MR.strings.second_to_last),
                        2 to stringResource(MR.strings.third_to_last),
                        3 to stringResource(MR.strings.fourth_to_last),
                        4 to stringResource(MR.strings.fifth_to_last),
                    ),
                    title = stringResource(MR.strings.pref_remove_after_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(MR.strings.pref_remove_bookmarked_chapters),
                ),
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeCategories(),
            entries = categories()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
            title = stringResource(MR.strings.pref_remove_exclude_categories),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnreadChaptersOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(newIncluded.fastMap { it.id.toString() }.toSet())
                    downloadNewChapterCategoriesExcludePref.set(newExcluded.fastMap { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewChaptersPref,
                    title = stringResource(MR.strings.pref_download_new),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnreadChaptersOnlyPref,
                    title = stringResource(MR.strings.pref_download_new_unread_chapters_only),
                    enabled = downloadNewChapters,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    enabled = downloadNewChapters,
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileReading(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(MR.plurals.next_unread_chapters, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.auto_download_while_reading),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.download_ahead_info)),
            ),
        )
    }

    // KMK -->
    @Composable
    private fun getDownloadCacheRenewInterval(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.download_cache_renew_interval),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.downloadCacheRenewInterval(),
                    entries = persistentMapOf(
                        -1 to stringResource(KMR.strings.download_cache_renew_interval_manual),
                        1 to stringResource(KMR.strings.download_cache_renew_interval_1hour),
                        2 to stringResource(KMR.strings.download_cache_renew_interval_2hour),
                        6 to stringResource(KMR.strings.download_cache_renew_interval_6hour),
                        12 to stringResource(KMR.strings.download_cache_renew_interval_12hour),
                        24 to stringResource(KMR.strings.download_cache_renew_interval_24hour),
                    ),
                    title = stringResource(KMR.strings.download_cache_renew_interval),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.download_cache_renew_interval_info)),
            ),
        )
    }
    // KMK <--
}

// MIKO -->
private const val BYTES_PER_MB = 1024L * 1024L

/** Enough for a ~10 TiB quota; keeps the Int arithmetic from overflowing. */
private const val MAX_QUOTA_DIGITS = 7

private val SPEED_LIMIT_VALUES = listOf(0, 256, 512, 1024, 2048, 4096, 8192)
// MIKO <--
