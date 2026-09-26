package eu.kanade.domain.source.model

import eu.kanade.domain.source.enhancement.SourceEnhancementRegistry
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.FollowsSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.source.EnhancedHttpSource
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.source.kotatsu.KotatsuCapabilities
import tachiyomi.source.kotatsu.KotatsuSource

/**
 * MIKO — static detection of [SourceFeature]s. Pure functions over injectable inputs so they are
 * unit-testable; the only "expensive" step is [CatalogueSource.getFilterList] on extensions and
 * the lazy parser instantiation behind [KotatsuSource.capabilities], which is why callers go
 * through [eu.kanade.domain.source.service.SourceCapabilitiesCache] and never call this from a
 * composable.
 */
object SourceCapabilities {

    /** Keywords that mark a filter as a tag/genre selector (English + a few common translations). */
    private val TAG_FILTER_KEYWORDS = listOf(
        "tag", "genre", "género", "genero", "categor", "theme", "demograph", "content", "type", "format",
    )

    /**
     * Full detection for a runtime source. Runs [CatalogueSource.getFilterList] for extension
     * sources, so call it off the main thread.
     */
    fun of(
        source: Source,
        installedExtension: Extension.Installed?,
        registry: SourceEnhancementRegistry?,
    ): Set<SourceFeature> {
        val features = mutableSetOf<SourceFeature>()

        // The delegate (SY) or the original extension source may each contribute.
        val unwrapped = if (source is EnhancedHttpSource) listOf(source.originalSource, source.enhancedSource) else listOf(source)

        unwrapped.forEach { s -> features += ofMarkers(s) }

        val kotatsu = unwrapped.firstNotNullOfOrNull { it as? KotatsuSource }
        if (kotatsu != null) {
            features += ofKotatsu(kotatsu.capabilities)
        } else {
            unwrapped.filterIsInstance<CatalogueSource>().forEach { s ->
                val filters = runCatching { s.getFilterList() }
                    .onFailure {
                        if (it is CancellationException) throw it
                        logcat(LogPriority.WARN, it) { "getFilterList() failed for ${s.name}" }
                    }
                    .getOrNull()
                if (filters != null) features += ofFilterList(filters)
            }
        }

        if (installedExtension?.isNsfw == true) features += SourceFeature.NSFW

        registry?.let { features += it.extraFeatures(source) }
        return features
    }

    /** Features derivable from marker interfaces and simple flags — no work at all. */
    fun ofMarkers(source: Source): Set<SourceFeature> {
        val features = mutableSetOf<SourceFeature>()
        if (source.supportsLatest) features += SourceFeature.LATEST
        if (source is CatalogueSource) features += SourceFeature.SEARCH
        if (source is ConfigurableSource) features += SourceFeature.CONFIGURABLE
        if (source is LoginSource) features += SourceFeature.LOGIN
        if (source is FollowsSource) features += SourceFeature.FOLLOWS
        if (source is MetadataSource<*, *>) features += SourceFeature.METADATA
        if (source.supportsRelatedMangas) features += SourceFeature.RELATED
        if (source.lang == "all" || source.lang == "other") features += SourceFeature.MULTI_LANGUAGE
        return features
    }

    /** Features of a Kotatsu parser from its static capabilities. */
    fun ofKotatsu(caps: KotatsuCapabilities): Set<SourceFeature> {
        val features = mutableSetOf<SourceFeature>()
        if (caps.latest) features += SourceFeature.LATEST
        if (caps.search) features += SourceFeature.SEARCH
        // Every Kotatsu parser filters by tag (getFilterOptions().availableTags); "multipleTags"
        // and "tagExclusion" are the stronger, statically known variants.
        features += SourceFeature.TAGS
        if (caps.tagExclusion) features += SourceFeature.TAG_EXCLUSION
        if (caps.year || caps.yearRange) features += SourceFeature.YEAR
        if (caps.authorSearch) features += SourceFeature.AUTHOR_SEARCH
        if (caps.multiLanguage || caps.originalLocale) features += SourceFeature.MULTI_LANGUAGE
        if (caps.login) features += SourceFeature.LOGIN
        if (caps.configurable) features += SourceFeature.CONFIGURABLE
        if (caps.alternativeDomains) features += SourceFeature.ALTERNATIVE_DOMAINS
        if (caps.nsfw) features += SourceFeature.NSFW
        features += SourceFeature.RELATED
        return features
    }

    /** Features an extension declares through its filter list. */
    fun ofFilterList(filters: FilterList): Set<SourceFeature> {
        val features = mutableSetOf<SourceFeature>()
        filters.forEach { filter ->
            val name = filter.name.lowercase()
            val looksLikeTag = TAG_FILTER_KEYWORDS.any { it in name }
            when (filter) {
                is Filter.Group<*> -> if (looksLikeTag || filter.state.size >= 5) {
                    features += SourceFeature.TAGS
                    if (filter.state.any { it is Filter.TriState }) features += SourceFeature.TAG_EXCLUSION
                }
                is Filter.Select<*> -> if (looksLikeTag) features += SourceFeature.TAGS
                is Filter.Text -> {
                    if ("author" in name || "autor" in name || "artist" in name) features += SourceFeature.AUTHOR_SEARCH
                    if ("year" in name || "año" in name || "ano" in name) features += SourceFeature.YEAR
                }
                else -> Unit
            }
        }
        return features
    }
}
