package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.model.kind
import eu.kanade.domain.source.service.SourceCapabilitiesCache
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.SourceCategoriesDialog
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.browse.SourcesSettingsDialog
import eu.kanade.presentation.browse.components.SourceInfoDialog
import eu.kanade.presentation.browse.components.SourcePresetDropdownMenu
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen.SmartSearchConfig
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.kotatsu.KotatsuSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.sourcesTab(
    smartSearchConfig: SmartSearchConfig? = null,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { SourcesScreenModel(smartSearchConfig = smartSearchConfig) }
    val state by screenModel.state.collectAsState()

    // MIKO -->
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    var displaySettingsOpen by remember { mutableStateOf(false) }
    // MIKO <--

    return TabContent(
        // SY -->
        titleRes = when (smartSearchConfig == null) {
            true -> MR.strings.label_sources
            false -> SYMR.strings.find_in_another_source
        },
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalSearchScreen(smartSearchConfig?.origTitle ?: "")) },
            ),
            // KMK -->
            AppBar.Action(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { screenModel.toggleNsfwOnly() },
            ),
            // KMK <--
            // MIKO -->
            AppBar.ActionCompose(
                title = stringResource(MKMR.strings.source_presets),
            ) {
                var presetsExpanded by remember { mutableStateOf(false) }
                Box {
                    Icon(
                        imageVector = Icons.Outlined.Bookmarks,
                        contentDescription = stringResource(MKMR.strings.source_presets),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { presetsExpanded = !presetsExpanded },
                        tint = if (state.hasActivePreset) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                    SourcePresetDropdownMenu(
                        expanded = presetsExpanded,
                        onDismissRequest = { presetsExpanded = false },
                        presets = state.categories,
                        activePreset = state.activePreset,
                        onSelectPreset = screenModel::setActivePreset,
                        onClickManagePresets = { navigator.push(SourceCategoryScreen()) },
                    )
                }
            },
            AppBar.Action(
                title = stringResource(MKMR.strings.sources_display_settings),
                icon = Icons.Outlined.Tune,
                onClick = { displaySettingsOpen = true },
            ),
            // MIKO <--
        ).let {
            when (smartSearchConfig) {
                null -> {
                    it.add(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_filter),
                            icon = Icons.Outlined.FilterList,
                            onClick = { navigator.push(SourcesFilterScreen()) },
                        ),
                    )
                }
                // Merge: find in another source
                else -> it
            }
        },
        // SY <--
        content = { contentPadding, snackbarHostState ->
            // MIKO -->
            // Local state on purpose: the "Source info" entry is owned by this file, so it does not
            // add a variant to SourcesScreenModel.Dialog.
            var sourceInfoFor by remember { mutableStateOf<Source?>(null) }
            // MIKO <--
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    // SY -->
                    val screen = when {
                        // Search selected source for entries to merge or for the recommending entry
                        smartSearchConfig != null -> SmartSearchScreen(source.id, smartSearchConfig)
                        listing == Listing.Popular && screenModel.useNewSourceNavigation -> SourceFeedScreen(source.id)
                        else -> BrowseSourceScreen(source.id, listing.query)
                    }
                    navigator.push(screen)
                    // SY <--
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                // KMK -->
                onChangeSearchQuery = screenModel::search,
                // KMK <--
                // MIKO -->
                onClearActivePreset = screenModel::clearActivePreset,
                onClickManagePresets = { navigator.push(SourceCategoryScreen()) },
                onSetKindFilter = screenModel::setKindFilter,
                onToggleGroupCollapsed = screenModel::toggleGroupCollapsed,
                onEnsureFeatures = screenModel::ensureFeatures,
                // MIKO <--
            )

            // MIKO -->
            if (displaySettingsOpen) {
                SourcesSettingsDialog(
                    onDismissRequest = { displaySettingsOpen = false },
                    preferences = sourcePreferences,
                    onSetSortMode = screenModel::setSortMode,
                    onSetGroupMode = screenModel::setGroupMode,
                    onSetDisplayMode = screenModel::setDisplayMode,
                )
            }
            // MIKO <--

            when (val dialog = state.dialog) {
                is SourcesScreenModel.Dialog.SourceLongClick -> {
                    val source = dialog.source
                    SourceOptionsDialog(
                        source = source,
                        onClickPin = {
                            screenModel.togglePin(source)
                            screenModel.closeDialog()
                        },
                        onClickDisable = {
                            screenModel.toggleSource(source)
                            screenModel.closeDialog()
                        },
                        // SY -->
                        onClickSetCategories = {
                            screenModel.showSourceCategoriesDialog(source)
                        }.takeIf { state.categories.isNotEmpty() },
                        onClickToggleDataSaver = {
                            screenModel.toggleExcludeFromDataSaver(source)
                            screenModel.closeDialog()
                        }.takeIf { state.dataSaverEnabled },
                        // SY <--
                        // MIKO -->
                        onClickAddToNewPreset = {
                            screenModel.showCreatePresetDialog(source)
                        },
                        onClickSourceInfo = {
                            screenModel.closeDialog()
                            sourceInfoFor = source
                        },
                        // MIKO <--
                        onDismiss = screenModel::closeDialog,
                        // KMK -->
                        onClickSettings = {
                            if (source.installedExtension !== null) {
                                navigator.push(ExtensionDetailsScreen(source.installedExtension!!.pkgName))
                            }
                            screenModel.closeDialog()
                        },
                        // KMK <--
                    )
                }
                is SourcesScreenModel.Dialog.SourceCategories -> {
                    val source = dialog.source
                    SourceCategoriesDialog(
                        source = source,
                        categories = state.categories,
                        onClickCategories = { categories ->
                            screenModel.setSourceCategories(source, categories)
                            screenModel.closeDialog()
                        },
                        onDismissRequest = screenModel::closeDialog,
                    )
                }
                // MIKO -->
                is SourcesScreenModel.Dialog.CreatePresetForSource -> {
                    val source = dialog.source
                    CategoryCreateDialog(
                        onDismissRequest = screenModel::closeDialog,
                        onCreate = { screenModel.createPresetAndAssign(source, it) },
                        categories = state.categories,
                        title = stringResource(MKMR.strings.source_preset_add_to_new),
                    )
                }
                // MIKO <--
                null -> Unit
            }

            // MIKO -->
            sourceInfoFor?.let { infoSource ->
                SourceInfoDialogFor(
                    source = infoSource,
                    onDismissRequest = { sourceInfoFor = null },
                )
            }
            // MIKO <--

            val internalErrString = stringResource(MR.strings.internal_error)
            // MIKO -->
            val presetCreatedString = stringResource(MKMR.strings.source_preset_created_and_added)
            val invalidPresetNameString = stringResource(SYMR.strings.invalid_category_name)
            // MIKO <--
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        SourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                        // MIKO -->
                        SourcesScreenModel.Event.PresetCreatedAndSourceAdded -> {
                            launch { snackbarHostState.showSnackbar(presetCreatedString) }
                        }
                        SourcesScreenModel.Event.InvalidPresetName -> {
                            launch { snackbarHostState.showSnackbar(invalidPresetNameString) }
                        }
                        // MIKO <--
                    }
                }
            }
        },
    )
}

// MIKO -->
/**
 * MIKO — gathers everything [SourceInfoDialog] shows for one source: kind and installed extension
 * (cheap, resolved once per id), the backing Kotatsu parser when there is one, and the detected
 * features, which are computed off the main thread by [SourceCapabilitiesCache].
 */
@Composable
private fun SourceInfoDialogFor(
    source: Source,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val kind = remember(source.id) { source.kind }
    val extension = remember(source.id) { source.installedExtension }
    val parserName = remember(source.id) {
        (Injekt.get<SourceManager>().get(source.id) as? KotatsuSource)?.parserName
    }
    val features by produceState<Set<SourceFeature>?>(null, source.id) {
        value = Injekt.get<SourceCapabilitiesCache>().get(source.id) ?: emptySet()
    }
    SourceInfoDialog(
        sourceName = source.visualName,
        sourceId = source.id,
        languageLabel = LocaleHelper.getSourceDisplayName(source.lang, context),
        kind = kind,
        features = features,
        extensionLabel = extension?.let {
            stringResource(MKMR.strings.source_info_extension, it.name, it.versionName)
        },
        parserLabel = parserName?.let { stringResource(MKMR.strings.source_info_parser, it) },
        onDismissRequest = onDismissRequest,
    )
}
// MIKO <--
