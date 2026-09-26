package tachiyomi.source.kotatsu

import android.app.Application
import logcat.LogPriority
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import tachiyomi.core.common.util.system.logcat

/**
 * Single place where the ~1200 parsers of the library become Mihon sources.
 *
 * The list is built lazily on first access: creating the adapters is cheap (the parser instance
 * behind each one is itself lazy) but there is no reason to pay for it before something browses.
 * Sources flagged `isBroken` upstream are left out entirely — they are known to be non-functional,
 * and registering them would only add dead entries to Browse and to global search.
 */
class KotatsuSourceRegistry(
    private val app: Application,
    private val loader: KotatsuLoaderContext,
) {

    /** Adapters for every usable parser, in [MangaParserSource.entries] order. */
    val sources: List<KotatsuParserSource> by lazy {
        val all = MangaParserSource.entries
        val usable = all.filter { !it.isBroken }
        // MIKO -->
        val dedicated = usable.count(KotatsuDedicatedParsers::isDedicated)
        logcat(LogPriority.INFO) {
            "Kotatsu parsers: ${usable.size} registered ($dedicated dedicated), " +
                "${all.size - usable.size} skipped as broken"
        }
        // A site with dedicated Miko code gets its own KotatsuParserSource subclass; everything
        // else falls back to the generic adapter. See KotatsuDedicatedParsers.
        usable.map { parserSource -> KotatsuDedicatedParsers.factoryFor(parserSource)(parserSource, loader, app) }
        // MIKO <--
    }

    private val sourcesById: Map<Long, KotatsuParserSource> by lazy { sources.associateBy { it.id } }

    private val sourcesByName: Map<String, KotatsuParserSource> by lazy {
        sources.associateBy { it.parserSource.name }
    }

    init {
        // Closes the loop the other way around: the loader needs a parser per request, and only the
        // registry knows which adapter owns which `MangaSource`.
        loader.parserResolver = { source -> byName(source.name)?.parser }
    }

    /** @param id a [KotatsuSourceIds.idOf] value. */
    fun byId(id: Long): KotatsuParserSource? = sourcesById[id]

    /** @param name a [MangaParserSource.name]. */
    fun byName(name: String): KotatsuParserSource? = sourcesByName[name]
}
