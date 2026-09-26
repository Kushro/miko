package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.source.enhancement.EnhancementGroup
import eu.kanade.domain.source.enhancement.EnhancementPreferences
import eu.kanade.domain.source.enhancement.EnhancementProvider
import eu.kanade.domain.source.enhancement.SourceEnhancementRegistry
import eu.kanade.domain.source.service.SourceCapabilitiesCache
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MIKO — the body shared by every "enhancement provider" settings screen
 * ([SettingsKeiyoushiScreen], [SettingsEmbeddedProvidersScreen]).
 *
 * The list is derived entirely from the registry: an info banner, then one [Preference.PreferenceGroup]
 * per [EnhancementGroup] of [provider] holding the single switch that enables/disables the whole
 * group. Adding a group (or an enhancement inside one) needs no change here.
 *
 * When the provider has no group yet, a disabled "no enhancements yet" row is shown instead, so the
 * section never looks broken.
 */
@Composable
internal fun enhancementProviderPreferences(provider: EnhancementProvider): List<Preference> {
    val enhancementPreferences = remember { Injekt.get<EnhancementPreferences>() }
    val registry = remember { Injekt.get<SourceEnhancementRegistry>() }
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val capabilitiesCache = remember { Injekt.get<SourceCapabilitiesCache>() }

    val groups = remember(provider) { EnhancementGroup.of(provider) }

    return buildList {
        add(Preference.PreferenceItem.InfoPreference(stringResource(MKMR.strings.enhancements_info)))
        if (groups.isEmpty()) {
            add(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MKMR.strings.enhancements_none_yet),
                    subtitle = stringResource(MKMR.strings.enhancements_none_yet_summary),
                    enabled = false,
                ),
            )
            return@buildList
        }
        groups.forEach { group ->
            add(
                enhancementGroupPreference(
                    group = group,
                    enhancementPreferences = enhancementPreferences,
                    registry = registry,
                    sourceManager = sourceManager,
                    capabilitiesCache = capabilitiesCache,
                ),
            )
        }
    }
}

/**
 * One group = one [Preference.PreferenceGroup] titled after the site, containing the group switch.
 * The subtitle lists the enhancements the switch covers and warns when the source that would use
 * them is not available on this device.
 */
@Composable
private fun enhancementGroupPreference(
    group: EnhancementGroup,
    enhancementPreferences: EnhancementPreferences,
    registry: SourceEnhancementRegistry,
    sourceManager: SourceManager,
    capabilitiesCache: SourceCapabilitiesCache,
): Preference.PreferenceGroup {
    val groupTitle = stringResource(group.titleRes)
    val enhancementLabels = registry.enhancementsIn(group).map { stringResource(it.titleRes) }

    val summary = if (enhancementLabels.isEmpty()) {
        stringResource(MKMR.strings.enhancements_none_yet)
    } else {
        stringResource(MKMR.strings.enhancements_group_toggle_summary, enhancementLabels.joinToString())
    }
    // Recomputed whenever the source list changes (extension installed/removed while the screen is
    // open, or the manager finishing its initial load), so the warning never goes stale.
    val loadedSources by sourceManager.sources.collectAsState(initial = emptyList())
    val sourceAvailable = remember(group, loadedSources) {
        group.sourceIds.any { id ->
            sourceManager.get(id).let { it != null && it !is StubSource }
        }
    }
    val subtitle = if (sourceAvailable) {
        summary
    } else {
        summary + " · " + stringResource(MKMR.strings.enhancements_group_extension_missing)
    }

    return Preference.PreferenceGroup(
        title = groupTitle,
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = enhancementPreferences.groupEnabled(group),
                title = stringResource(MKMR.strings.enhancements_group_toggle, groupTitle),
                subtitle = subtitle,
                onValueChanged = {
                    // Kinds, badges and capability icons are all derived from the registry, which
                    // now answers differently: drop every cached feature set.
                    capabilitiesCache.invalidateAll()
                    true
                },
            ),
        ),
    )
}
