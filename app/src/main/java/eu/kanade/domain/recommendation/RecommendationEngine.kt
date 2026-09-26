package eu.kanade.domain.recommendation

import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.manga.model.Manga
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

// MIKO -->

/**
 * Runs the suggestion systems the user configured ([RecommendationPreferences]) for one entry and
 * streams their groups to the caller. This is the single call site behind
 * `MangaScreenModel.fetchRelatedMangasFromSource`; the screen model never talks to a provider.
 *
 * - [RecommendationMode.SINGLE]: the one selected provider.
 * - [RecommendationMode.MULTI]: every enabled provider, all at once (`supervisorScope`, one
 *   failing provider does not stop the others). Groups are pushed as they arrive — the screen
 *   orders them by [activeProviderIds] and de-dupes in that order, so the higher-priority provider
 *   keeps a title found twice.
 *
 * A provider's run is taken from [RecommendationCache] when present and stored there only when it
 * finished without any error (so failures are retried on the next visit).
 */
class RecommendationEngine(
    private val preferences: RecommendationPreferences,
    private val providers: List<RecommendationProvider>,
    private val cache: RecommendationCache,
) {

    /** Providers that will run right now, highest priority first — the order the UI must use. */
    fun activeProviderIds(): List<RecommendationProviderId> = preferences.settings().get().activeProviders

    suspend fun fetch(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    ) {
        val active = activeProviderIds()
            .mapNotNull { id -> providers.firstOrNull { it.id == id } }
            .filter { it.isAvailable(source, manga) }
        if (active.isEmpty()) return

        supervisorScope {
            active.forEach { provider ->
                launch {
                    val key = RecommendationCache.Key(provider.id, source.id, manga.url)
                    cache.get(key)?.let { cached ->
                        cached.forEach { push(it) }
                        return@launch
                    }
                    // Providers may push from several coroutines at once.
                    val collected = Collections.synchronizedList(mutableListOf<RecommendationGroup>())
                    val failed = AtomicBoolean(false)
                    try {
                        provider.recommend(
                            source = source,
                            manga = manga,
                            onError = { e ->
                                failed.set(true)
                                onError(e)
                            },
                        ) { group ->
                            collected += group
                            push(group)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        failed.set(true)
                        onError(e)
                    }
                    if (!failed.get()) cache.put(key, collected.toList())
                }
            }
        }
    }
}

// MIKO <--
