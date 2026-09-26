package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.miko.MKMR

// MIKO -->
/**
 * MIKO — the six first-level destinations that can sit on the bottom navigation bar (or the
 * navigation rail on tablets). The ids are stable identifiers for the tab objects in
 * `eu.kanade.tachiyomi.ui.*`; the mapping lives in `HomeScreen` so this file stays UI-free and
 * unit-testable.
 */
enum class NavTabId {
    LIBRARY,
    UPDATES,
    HISTORY,
    BROWSE,
    // C18: Favorites (bookmarked pages + chapters) promoted from a "More" row to a real tab.
    FAVORITES,
    MORE,
}

/**
 * MIKO — ready-made layouts for the navigation bar (order + visibility of the tabs), inspired by
 * Kotatsu-Redo's configurable `nav_main`. No drag & drop editor: a preset is picked from
 * Settings → Appearance → Navigation bar.
 *
 * [NavTabId.LIBRARY], [NavTabId.BROWSE], [NavTabId.FAVORITES] and [NavTabId.MORE] are present in
 * every preset, and [NavTabId.MORE] is always last, so there is always a reachable home tab and an
 * overflow destination for whatever the preset hides (see `MoreScreen`). Favorites sits right
 * before More in every preset (C18, user request: it must live on the main menu).
 *
 * Only [CUSTOM] honours the inherited TachiyomiSY switches `showNavUpdates` / `showNavHistory`;
 * the other presets define the bar on their own and ignore them — see [resolveNavTabs].
 */
enum class NavPreset(val titleRes: StringResource, val tabs: List<NavTabId>) {
    MIHON(
        MKMR.strings.nav_preset_mihon,
        listOf(
            NavTabId.LIBRARY,
            NavTabId.UPDATES,
            NavTabId.HISTORY,
            NavTabId.BROWSE,
            NavTabId.FAVORITES,
            NavTabId.MORE,
        ),
    ),
    KOTATSU(
        MKMR.strings.nav_preset_kotatsu,
        listOf(
            NavTabId.HISTORY,
            NavTabId.LIBRARY,
            NavTabId.BROWSE,
            NavTabId.UPDATES,
            NavTabId.FAVORITES,
            NavTabId.MORE,
        ),
    ),
    COMPACT(
        MKMR.strings.nav_preset_compact,
        listOf(NavTabId.LIBRARY, NavTabId.BROWSE, NavTabId.FAVORITES, NavTabId.MORE),
    ),
    CUSTOM(
        MKMR.strings.nav_preset_custom,
        listOf(
            NavTabId.LIBRARY,
            NavTabId.UPDATES,
            NavTabId.HISTORY,
            NavTabId.BROWSE,
            NavTabId.FAVORITES,
            NavTabId.MORE,
        ),
    ),
}

/**
 * MIKO — resolves the tabs shown on the navigation bar for [preset].
 *
 * For [NavPreset.CUSTOM] the Updates and History tabs are filtered out according to [showUpdates]
 * and [showHistory] (the SY switches); every other preset returns [NavPreset.tabs] untouched, so
 * the switches are ignored. The result always keeps the preset order, always contains
 * [NavTabId.LIBRARY] and ends with [NavTabId.MORE].
 */
fun resolveNavTabs(
    preset: NavPreset,
    showUpdates: Boolean,
    showHistory: Boolean,
): List<NavTabId> {
    if (preset != NavPreset.CUSTOM) return preset.tabs
    return preset.tabs.filter {
        when (it) {
            NavTabId.UPDATES -> showUpdates
            NavTabId.HISTORY -> showHistory
            else -> true
        }
    }
}
// MIKO <--
