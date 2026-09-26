package eu.kanade.domain.source.enhancement

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.Source
import exh.source.getOriginalSource
import tachiyomi.i18n.miko.MKMR

/**
 * MIKO — who ships the source an [EnhancementGroup] belongs to. Each provider gets its own section
 * in Settings ("Keiyoushi", "Embedded providers"), and the groups of that provider are listed
 * inside it.
 */
enum class EnhancementProvider(val titleRes: StringResource, val summaryRes: StringResource) {
    /** Extensions from the Keiyoushi repository, installed as APKs. */
    KEIYOUSHI(MKMR.strings.pref_category_keiyoushi, MKMR.strings.pref_keiyoushi_summary),

    /** Sources compiled into the app (`:source-kotatsu` parsers). */
    EMBEDDED(MKMR.strings.pref_category_embedded_providers, MKMR.strings.pref_embedded_providers_summary),
}

/**
 * MIKO — a switchable bundle of [SourceEnhancement]s for one site.
 *
 * Enhancements are enabled **per group, not individually**: the user flips one switch per source
 * ("Asura Scans enhancements") and every enhancement registered under that group — present and
 * future — follows it. The switch lives in [EnhancementPreferences.groupEnabled] and defaults to
 * enabled.
 *
 * Adding a site = one entry here + one or more [SourceEnhancement]s with `group = <entry>`,
 * registered in `AppModule`. Nothing else has to change for it to show up in Settings.
 *
 * @param key stable preference key suffix; never rename once shipped.
 * @param sourceIds runtime ids of the sources this group applies to (`Source.id` of the original,
 * unwrapped source). Several ids let one group cover mirrors/forks of the same site.
 */
enum class EnhancementGroup(
    val provider: EnhancementProvider,
    val key: String,
    val titleRes: StringResource,
    val sourceIds: Set<Long>,
) {
    /** Keiyoushi "Asura Scans" extension (`eu.kanade.tachiyomi.extension.en.asurascans`). */
    ASURA_SCANS(
        provider = EnhancementProvider.KEIYOUSHI,
        key = "asura_scans",
        titleRes = MKMR.strings.enhancement_group_asura_scans,
        sourceIds = setOf(ASURA_SCANS_SOURCE_ID),
    ),

    /** Keiyoushi "Comix" extension (`eu.kanade.tachiyomi.extension.en.comix`, comix.to / comix.ws). */
    COMIX(
        provider = EnhancementProvider.KEIYOUSHI,
        key = "comix",
        titleRes = MKMR.strings.enhancement_group_comix,
        sourceIds = setOf(COMIX_SOURCE_ID),
    ),
    ;

    /** Whether [source] (possibly wrapped in an `EnhancedHttpSource`) belongs to this group. */
    fun matches(source: Source): Boolean = source.getOriginalSource().id in sourceIds

    companion object {
        fun of(provider: EnhancementProvider): List<EnhancementGroup> = entries.filter { it.provider == provider }
    }
}

/** Source id of the Keiyoushi "Asura Scans" extension (stable: derived from name/lang/versionId). */
const val ASURA_SCANS_SOURCE_ID = 6247824327199706550L

/** Source id of the Keiyoushi "Comix" extension (one id for both mirrors, comix.to and comix.ws). */
const val COMIX_SOURCE_ID = 7537715367149829912L
