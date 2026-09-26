package eu.kanade.domain.source.service

import eu.kanade.domain.source.enhancement.SourceEnhancementRegistry
import eu.kanade.domain.source.model.SourceCapabilities
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * MIKO — process-wide cache of [SourceFeature] sets keyed by source id.
 *
 * Detection is cheap but not free (extension `getFilterList()`, lazy Kotatsu parser
 * instantiation), so screens ask for it lazily — typically only for the rows that are visible —
 * and re-render when [changes] emits the id. Values are dropped when the source manager reloads
 * (extension installed/uninstalled, source mode changed): call [invalidate] from there.
 */
class SourceCapabilitiesCache(
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val registry: SourceEnhancementRegistry,
) {

    private val cache = ConcurrentHashMap<Long, Set<SourceFeature>>()
    private val computing = ConcurrentHashMap<Long, Mutex>()

    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 64)

    /** Emits a source id every time its feature set becomes available. */
    val changes: SharedFlow<Long> = _changes.asSharedFlow()

    /** Cached value, or null when not computed yet. Safe to call from a composable. */
    fun peek(sourceId: Long): Set<SourceFeature>? = cache[sourceId]

    /** Computes (once) and returns the features of [source]. Runs on IO. */
    suspend fun get(source: Source): Set<SourceFeature> {
        cache[source.id]?.let { return it }
        return withIOContext {
            computing.getOrPut(source.id) { Mutex() }.withLock {
                cache[source.id]?.let { return@withLock it }
                val extension = extensionManager.installedExtensionsFlow.value
                    .find { ext -> ext.sources.any { it.id == source.id } }
                val features = SourceCapabilities.of(source, extension, registry)
                cache[source.id] = features
                _changes.tryEmit(source.id)
                features
            }
        }
    }

    /** Computes (once) the features of the source with [sourceId]; null if it isn't loaded. */
    suspend fun get(sourceId: Long): Set<SourceFeature>? {
        cache[sourceId]?.let { return it }
        val source = sourceManager.get(sourceId) ?: return null
        return get(source)
    }

    fun invalidate(sourceId: Long) {
        cache.remove(sourceId)
    }

    fun invalidateAll() {
        cache.clear()
    }
}
