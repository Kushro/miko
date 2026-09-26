package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import eu.kanade.domain.source.enhancement.EnhancementProvider
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.miko.MKMR

/**
 * MIKO — enhancements for sources that come from the Keiyoushi extension repository.
 *
 * Always visible (no `isEnabled()` gate): the user has to be able to find the switches before the
 * matching extension is installed, and the rows say so themselves when it is missing.
 */
object SettingsKeiyoushiScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsKeiyoushiScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MKMR.strings.pref_category_keiyoushi

    @Composable
    override fun getPreferences(): List<Preference> =
        enhancementProviderPreferences(EnhancementProvider.KEIYOUSHI)
}
