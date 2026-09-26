package eu.kanade.domain.source.model

import eu.kanade.domain.source.enhancement.SourceEnhancementRegistry
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.EhBasedSource
import exh.source.EnhancedHttpSource
import exh.source.MERGED_SOURCE_ID
import exh.source.eHentaiSourceIds
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.kotatsu.KotatsuSource
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * MIKO — classification of a source into a [SourceKind].
 *
 * The pure function takes everything it needs as parameters so it can be unit-tested; the
 * extension properties are the convenience entry points for screen models and composables.
 */

/**
 * @param id the source id (works even when [runtime] is null).
 * @param runtime the loaded source, or null when it is not available on this device.
 * @param hasInstalledExtension whether an installed extension declares this id.
 * @param hasEnhancement whether a Miko [eu.kanade.domain.source.enhancement.SourceEnhancement] applies.
 */
fun classifySourceKind(
    id: Long,
    runtime: Source?,
    hasInstalledExtension: Boolean,
    hasEnhancement: Boolean,
): SourceKind {
    if (id == LocalSource.ID) return SourceKind.LOCAL
    if (id == MERGED_SOURCE_ID || id in eHentaiSourceIds) return SourceKind.BUILT_IN_DEDICATED
    if (runtime == null || runtime is StubSource) return SourceKind.NOT_INSTALLED
    return when {
        runtime is EhBasedSource -> SourceKind.BUILT_IN_DEDICATED
        runtime is KotatsuSource -> if (hasEnhancement) SourceKind.BUILT_IN_DEDICATED else SourceKind.BUILT_IN
        runtime is EnhancedHttpSource -> SourceKind.EXTENSION_ENHANCED
        hasInstalledExtension -> if (hasEnhancement) SourceKind.EXTENSION_ENHANCED else SourceKind.EXTENSION
        // A loaded HttpSource we cannot attribute: treat it as an extension so it is never hidden.
        else -> if (hasEnhancement) SourceKind.EXTENSION_ENHANCED else SourceKind.EXTENSION
    }
}

/** Kind of a runtime source (uses Injekt for the enhancement registry). */
val Source.kind: SourceKind
    get() = classifySourceKind(
        id = id,
        runtime = this,
        hasInstalledExtension = Injekt.get<ExtensionManager>()
            .installedExtensionsFlow.value.any { ext -> ext.sources.any { it.id == id } },
        hasEnhancement = Injekt.get<SourceEnhancementRegistry>().hasEnhancement(this),
    )

/** Kind of a domain source: resolves the runtime source through [SourceManager]. */
val tachiyomi.domain.source.model.Source.kind: SourceKind
    get() = sourceKindOf(id)

/** Kind of an arbitrary source id (library covers, history, migration lists). */
fun sourceKindOf(id: Long): SourceKind {
    val runtime = Injekt.get<SourceManager>().get(id)
    return classifySourceKind(
        id = id,
        runtime = runtime,
        hasInstalledExtension = Injekt.get<ExtensionManager>()
            .installedExtensionsFlow.value.any { ext -> ext.sources.any { it.id == id } },
        hasEnhancement = runtime != null && Injekt.get<SourceEnhancementRegistry>().hasEnhancement(runtime),
    )
}
