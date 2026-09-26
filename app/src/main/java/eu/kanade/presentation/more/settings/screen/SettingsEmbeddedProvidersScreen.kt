package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import eu.kanade.domain.source.enhancement.EnhancementProvider
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.miko.MKMR

/**
 * MIKO — enhancements for the sources compiled into the app (the `:source-kotatsu` parsers).
 *
 * There is no [EnhancementProvider.EMBEDDED] group yet, so today this screen only explains what the
 * section is for; the shared body fills it in as soon as one is registered.
 */
object SettingsEmbeddedProvidersScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsEmbeddedProvidersScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MKMR.strings.pref_category_embedded_providers

    @Composable
    override fun getPreferences(): List<Preference> =
        enhancementProviderPreferences(EnhancementProvider.EMBEDDED)
}
