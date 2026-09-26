package eu.kanade.domain.source.enhancement

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * MIKO — one boolean per [EnhancementGroup], **enabled by default**. Registered in
 * `PreferenceModule`; the Settings sections "Keiyoushi" / "Embedded providers" are the only UI that
 * writes it, [SourceEnhancementRegistry] is the only reader that matters.
 */
class EnhancementPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun groupEnabled(group: EnhancementGroup): Preference<Boolean> =
        preferenceStore.getBoolean("miko_enhancement_group_${group.key}", true)

    fun isEnabled(group: EnhancementGroup): Boolean = groupEnabled(group).get()
}
