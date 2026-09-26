package eu.kanade.domain.recommendation

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * MIKO — the "Suggestion system" configuration (C14). Registered in `PreferenceModule`; written
 * only by the Settings → Advanced dialog, read by [RecommendationEngine]. Regular preference
 * (travels in backups). The KMK switches that gate the whole feature
 * (`SourcePreferences.relatedMangas()`, `UiPreferences.expandRelatedMangas()`) stay where they are.
 */
class RecommendationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun settings(): Preference<RecommendationSettings> = preferenceStore.getObjectFromString(
        "miko_recommendation_settings",
        RecommendationSettings.default,
        RecommendationSettings::toJson,
        RecommendationSettings::fromJson,
    )
}
