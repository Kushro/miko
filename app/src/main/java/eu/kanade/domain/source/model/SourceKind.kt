package eu.kanade.domain.source.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.miko.MKMR

/**
 * MIKO — where a source comes from, as shown to the user (Sources tab groups, badges on covers,
 * manga details, migration screens).
 *
 * Order matters: it is the default group order in the Sources tab and the sort key of
 * [eu.kanade.domain.source.model.SourcesSortMode.KIND].
 */
enum class SourceKind(
    val titleRes: StringResource,
    val shortLabelRes: StringResource,
    val descriptionRes: StringResource,
) {
    /** `LocalSource`: files on the device. */
    LOCAL(
        MKMR.strings.source_kind_local,
        MKMR.strings.source_kind_short_local,
        MKMR.strings.source_kind_desc_local,
    ),

    /** A stock Kotatsu parser bundled in the app (`:source-kotatsu`). */
    BUILT_IN(
        MKMR.strings.source_kind_built_in,
        MKMR.strings.source_kind_short_built_in,
        MKMR.strings.source_kind_desc_built_in,
    ),

    /**
     * Bundled source with dedicated code: a Kotatsu parser that has a Miko enhancement registered,
     * or an app-native source (E-Hentai/ExHentai, Merged).
     */
    BUILT_IN_DEDICATED(
        MKMR.strings.source_kind_built_in_dedicated,
        MKMR.strings.source_kind_short_built_in_dedicated,
        MKMR.strings.source_kind_desc_built_in_dedicated,
    ),

    /** A plain source from an installed Tachiyomi/Mihon extension. */
    EXTENSION(
        MKMR.strings.source_kind_extension,
        MKMR.strings.source_kind_short_extension,
        MKMR.strings.source_kind_desc_extension,
    ),

    /**
     * An extension source wrapped with extra behaviour: SY's delegated sources
     * (`EnhancedHttpSource`: MangaDex, nhentai, Pururin, 8muses, LANraragi) or a Miko enhancement.
     */
    EXTENSION_ENHANCED(
        MKMR.strings.source_kind_extension_enhanced,
        MKMR.strings.source_kind_short_extension_enhanced,
        MKMR.strings.source_kind_desc_extension_enhanced,
    ),

    /** Stub: referenced by the library but not available on this device. */
    NOT_INSTALLED(
        MKMR.strings.source_kind_not_installed,
        MKMR.strings.source_kind_short_not_installed,
        MKMR.strings.source_kind_desc_not_installed,
    ),
    ;

    val isBuiltIn: Boolean get() = this == BUILT_IN || this == BUILT_IN_DEDICATED
    val isExtension: Boolean get() = this == EXTENSION || this == EXTENSION_ENHANCED
    val isEnhanced: Boolean get() = this == BUILT_IN_DEDICATED || this == EXTENSION_ENHANCED
}
