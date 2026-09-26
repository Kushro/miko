package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import kotlinx.coroutines.flow.update
import mihon.domain.manga.model.toDomainManga
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeepLinkScreenModel(
    query: String = "",
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
) : StateScreenModel<DeepLinkScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            // MIKO -->
            // Resolution order: (1) a `miko://entry` link, which names its source by id and
            // therefore needs no ResolvableSource; (2) the upstream ResolvableSource path;
            // (3) a host-to-baseUrl match against the installed online sources, which is the only
            // thing that can open a plain site URL on sources that are not ResolvableSource
            // (every built-in Kotatsu parser); (4) give up and let the screen search globally.
            val uri = runCatching { query.toUri() }.getOrNull()
            if (uri != null && MikoEntryLink.matches(uri)) {
                val link = MikoEntryLink.parse(uri)
                if (link == null) {
                    mutableState.update { State.InvalidLink }
                } else {
                    runCatching { resolveEntryLink(link) }
                        .onFailure { mutableState.update { State.NoResults } }
                }
                return@launchIO
            }
            if (runCatching { resolveWithResolvableSource(query) }.getOrDefault(false)) return@launchIO
            if (runCatching { resolveByHost(query) }.getOrDefault(false)) return@launchIO
            mutableState.update { State.NoResults }
            // MIKO <--
        }
    }

    // MIKO -->

    /**
     * Resolve an entry link without any help from the source: the id picks the source, and `u`
     * carries the raw `SManga.url` identifier, so the entry can be seeded locally and only
     * *enriched* over the network.
     */
    private suspend fun resolveEntryLink(link: MikoEntryLink) {
        val source = sourceManager.get(link.sourceId)
        if (source == null) {
            mutableState.update {
                State.SourceNotInstalled(
                    title = link.title,
                    sourceName = link.sourceName,
                    sourceId = link.sourceId,
                )
            }
            return
        }

        val seed = SManga.create().apply {
            url = link.mangaUrl
            title = link.title
            link.author?.let { author = it }
            link.genres.takeIf { it.isNotEmpty() }?.let { genre = it.joinToString(", ") }
            link.status?.let { status = it.toInt() }
        }
        // The fetched details are a *delta*: sources routinely return a partially filled SManga
        // that leaves `url`/`title` untouched, so merging keeps the identifiers from the link.
        val fetched = runCatching {
            source.getMangaUpdate(
                manga = seed,
                chapters = emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            ).manga
        }.getOrNull()
        if (fetched != null && fetched !== seed) {
            seed.mergeDescriptiveFieldsFrom(fetched)
        }
        seed.initialized = true

        val manga = networkToLocalManga(seed.toDomainManga(source.id))
        // The chapter is optional: a failure resolving it must not throw away the manga we
        // already resolved and persisted, so degrade to opening the manga instead.
        val chapter = link.chapterUrl?.let { runCatching { resolveChapter(manga, it) }.getOrNull() }

        mutableState.update {
            if (chapter == null) State.Result(manga) else State.Result(manga, chapter.id)
        }
    }

    private suspend fun resolveWithResolvableSource(query: String): Boolean {
        val source = sourceManager.getAll()
            .filterIsInstance<ResolvableSource>()
            .firstOrNull { it.getUriType(query) != UriType.Unknown }
            ?: return false

        val manga = source.getManga(query)
            ?.let { networkToLocalManga(it.toDomainManga(source.id)) }
            ?: return false

        val chapter = if (source.getUriType(query) == UriType.Chapter) {
            source.getChapter(query)?.let { getChapterFromSChapter(it, manga, source) }
        } else {
            null
        }

        mutableState.update {
            if (chapter == null) State.Result(manga) else State.Result(manga, chapter.id)
        }
        return true
    }

    /**
     * Last-resort resolution for a plain site URL: find an installed online source whose
     * [eu.kanade.tachiyomi.source.online.HttpSource.baseUrl] lives on the same host, and treat the
     * path (plus query) as the source-relative `SManga.url`. This is what makes site URLs openable
     * on the Kotatsu parsers, none of which implement `ResolvableSource`.
     *
     * @param preferredLang when the caller has a language hint (an entry link's `l`), a candidate
     * on that language wins over the rest.
     */
    private suspend fun resolveByHost(query: String, preferredLang: String? = null): Boolean {
        val uri = runCatching { query.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.let { normalizeHost(it) }?.takeIf { it.isNotEmpty() } ?: return false

        val candidates = sourceManager.getOnlineSources()
            .filter { source ->
                val sourceHost = runCatching { source.baseUrl.toUri().host }.getOrNull()
                sourceHost != null && normalizeHost(sourceHost) == host
            }
        if (candidates.isEmpty()) return false
        val source = candidates.firstOrNull { it.lang == preferredLang } ?: candidates.first()

        val relativeUrl = buildString {
            append(uri.encodedPath.orEmpty().ifEmpty { "/" })
            uri.encodedQuery?.takeIf { it.isNotEmpty() }?.let { append('?').append(it) }
        }
        val seed = SManga.create().apply {
            url = relativeUrl
            title = uri.lastPathSegment.orEmpty().ifEmpty { host }
        }
        val fetched = runCatching {
            source.getMangaUpdate(
                manga = seed,
                chapters = emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            ).manga
        }.getOrNull() ?: return false
        if (fetched !== seed) {
            seed.mergeDescriptiveFieldsFrom(fetched)
            // Unlike the entry-link path there is no trustworthy title in the URL, so the fetched
            // one wins here.
            runCatching { fetched.title }.getOrNull()?.takeIf { it.isNotBlank() }?.let { seed.title = it }
        }
        seed.initialized = true

        val manga = networkToLocalManga(seed.toDomainManga(source.id))
        mutableState.update { State.Result(manga) }
        return true
    }

    private suspend fun resolveChapter(manga: Manga, chapterUrl: String): Chapter? {
        return getChapterByUrlAndMangaId.await(chapterUrl, manga.id)
            ?: updateMangaFromRemote(manga = manga, fetchChapters = true)
                .getOrElse { return null }
                .newChapters
                .find { it.url == chapterUrl }
    }

    private fun normalizeHost(host: String): String = host.lowercase().removePrefix("www.")

    /**
     * Copy only the descriptive fields of [other] onto the receiver, never the `url`/`title`
     * identifiers: a source's details response is allowed to leave them empty.
     */
    private fun SManga.mergeDescriptiveFieldsFrom(other: SManga) {
        other.artist?.let { artist = it }
        other.author?.let { author = it }
        other.description?.let { description = it }
        other.genre?.let { genre = it }
        other.thumbnail_url?.let { thumbnail_url = it }
        other.status.takeIf { it != 0 }?.let { status = it }
        update_strategy = other.update_strategy
    }
    // MIKO <--

    private suspend fun getChapterFromSChapter(sChapter: SChapter, manga: Manga, source: Source): Chapter? {
        val localChapter = getChapterByUrlAndMangaId.await(sChapter.url, manga.id)

        return localChapter
            ?: updateMangaFromRemote(manga = manga, fetchChapters = true)
                .getOrElse { return null }
                .newChapters
                .find { it.url == sChapter.url }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        // MIKO -->
        @Immutable
        data object InvalidLink : State

        @Immutable
        data class SourceNotInstalled(
            val title: String,
            val sourceName: String?,
            val sourceId: Long,
        ) : State
        // MIKO <--

        @Immutable
        data class Result(val manga: Manga, val chapterId: Long? = null) : State
    }
}
