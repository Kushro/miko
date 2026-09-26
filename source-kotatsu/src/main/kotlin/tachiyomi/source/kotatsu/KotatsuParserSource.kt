// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.PreferenceScreen
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.preferenceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import tachiyomi.source.kotatsu.mapping.KotatsuPaging
import tachiyomi.source.kotatsu.mapping.absoluteUrl
import tachiyomi.source.kotatsu.mapping.buildFilterList
import tachiyomi.source.kotatsu.mapping.kotatsuCall
import tachiyomi.source.kotatsu.mapping.latestSortOrder
import tachiyomi.source.kotatsu.mapping.popularSortOrder
import tachiyomi.source.kotatsu.mapping.toManga
import tachiyomi.source.kotatsu.mapping.toMangaChapter
import tachiyomi.source.kotatsu.mapping.toMangaListFilter
import tachiyomi.source.kotatsu.mapping.toMangaPage
import tachiyomi.source.kotatsu.mapping.toPage
import tachiyomi.source.kotatsu.mapping.toSChapters
import tachiyomi.source.kotatsu.mapping.toSManga
import tachiyomi.source.kotatsu.network.KotatsuHeadersInterceptor
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exposes a single Kotatsu [MangaParser] as a regular Mihon [HttpSource].
 *
 * Extending `HttpSource` (and not just `CatalogueSource`) is a requirement, not a preference: the
 * reader's `HttpPageLoader`, the Coil cover fetcher and `SourceManager.getOnlineSources()` all
 * expect that type, so anything less would silently disappear from browse/migrate/global search and
 * lose the parser headers on image requests.
 *
 * All state lives in the instance: one adapter per `MangaParserSource`, created once by
 * [KotatsuSourceRegistry].
 *
 * MIKO — the class is `open` so a single site can get dedicated Miko behaviour without forking the
 * generic adapter: register a subclass factory in [KotatsuDedicatedParsers] and the registry builds
 * it instead of this one. Every `override` below is implicitly overridable; [loader] and [app] are
 * `protected` for the same reason. See `docs/features/sources/en.md`.
 */
open class KotatsuParserSource(
    override val parserSource: MangaParserSource,
    protected val loader: KotatsuLoaderContext,
    protected val app: Application,
) : HttpSource(), ConfigurableSource, KotatsuSource {

    /**
     * Created on first use: the registry builds ~1200 adapters at startup and instantiating every
     * parser eagerly would be pure waste.
     */
    val parser: MangaParser by lazy { loader.newParserInstance(parserSource) }

    override val id: Long = KotatsuSourceIds.idOf(parserSource)

    override val name: String = parserSource.title

    override val lang: String = parserSource.locale.ifEmpty { LANG_ALL }.replace('_', '-')

    override val baseUrl: String
        get() = "https://${parser.domain}"

    override val supportsLatest: Boolean
        get() = SortOrder.UPDATED in parser.availableSortOrders

    override val client: OkHttpClient
        get() = loader.httpClient

    override val isNsfw: Boolean
        get() = parserSource.contentType == ContentType.HENTAI

    // MIKO -->
    /**
     * Ratings the parser reported for the manga we listed/detailed, keyed by relative url. Mihon's
     * `SManga` has no rating field, so this side channel is the only way to show the site's rating
     * next to the user's own. Bounded LRU so it never grows past [REMOTE_RATING_CACHE_SIZE].
     */
    private val remoteRatings = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
            size > REMOTE_RATING_CACHE_SIZE
    }

    override fun getRemoteRating(mangaUrl: String): Float? = synchronized(remoteRatings) { remoteRatings[mangaUrl] }

    private fun rememberRating(manga: Manga) {
        if (manga.rating != RATING_UNKNOWN && manga.rating in 0f..1f) {
            synchronized(remoteRatings) { remoteRatings[manga.url] = manga.rating }
        }
    }

    override val capabilities: KotatsuCapabilities by lazy {
        val filterCaps = parser.filterCapabilities
        val orders = parser.availableSortOrders
        val configKeys = mutableListOf<ConfigKey<*>>().also(parser::onCreateConfig)
        KotatsuCapabilities(
            search = filterCaps.isSearchSupported,
            searchWithFilters = filterCaps.isSearchWithFiltersSupported,
            multipleTags = filterCaps.isMultipleTagsSupported,
            tagExclusion = filterCaps.isTagsExclusionSupported,
            year = filterCaps.isYearSupported,
            yearRange = filterCaps.isYearRangeSupported,
            authorSearch = filterCaps.isAuthorSearchSupported,
            originalLocale = filterCaps.isOriginalLocaleSupported,
            multiLanguage = parserSource.locale.isEmpty(),
            latest = SortOrder.UPDATED in orders,
            extraSortOrders = orders.any { it != SortOrder.UPDATED && it != SortOrder.UPDATED_ASC },
            login = parser.authorizationProvider != null,
            configurable = configKeys.any { it !is ConfigKey.Domain },
            alternativeDomains = parser.configKeyDomain.presetValues.size > 1,
            nsfw = isNsfw,
        )
    }
    // MIKO <--

    override val supportsRelatedMangas: Boolean
        get() = true

    override val disableRelatedMangasBySearch: Boolean
        get() = false

    private val paging = KotatsuPaging()

    @Volatile
    private var filterOptions: MangaListFilterOptions? = null

    private val filterOptionsLoading = AtomicBoolean(false)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * The `X-Kotatsu-Source` header is what lets [KotatsuHeadersInterceptor] find the parser again
     * for requests Mihon builds itself (covers, WebView), where no request tag can be attached.
     */
    override fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        parser.getRequestHeaders().forEach { (headerName, headerValue) -> add(headerName, headerValue) }
        if (get("User-Agent").isNullOrEmpty()) {
            add("User-Agent", loader.getDefaultUserAgent())
        }
        add(KotatsuHeadersInterceptor.HEADER_SOURCE, parserSource.name)
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        loadList(popularSortOrder(parser), MangaListFilter.EMPTY, page)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        loadList(latestSortOrder(parser), MangaListFilter.EMPTY, page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        // The widgets in [filters] already carry their model objects, so no extra network call is
        // needed here even when the options cache is still empty.
        val (order, filter) = toMangaListFilter(parser, query, filters, filterOptions)
        return loadList(order, filter, page)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = kotatsuCall {
        if (!fetchDetails && !fetchChapters) {
            SMangaUpdate(manga, chapters)
        } else {
            // Kotatsu has a single call for both: details always come back with the chapter list.
            val details = parser.getDetails(manga.toManga(parser, baseUrl))
            rememberRating(details)
            SMangaUpdate(
                manga = if (fetchDetails) details.toSManga(initialized = true) else manga,
                chapters = if (fetchChapters) details.chapters.orEmpty().toSChapters() else chapters,
            )
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = kotatsuCall {
        parser.getPages(chapter.toMangaChapter(parser))
            .mapIndexed { index, page -> page.toPage(index) }
    }

    override suspend fun getImageUrl(page: Page): String = kotatsuCall {
        parser.getPageUrl(page.toMangaPage(parser))
    }

    /**
     * Tags the request with its [MangaSource] so [KotatsuHeadersInterceptor] can merge the parser
     * headers and run `MangaParser.intercept` on it.
     */
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)
        .newBuilder()
        .tag(MangaSource::class.java, parserSource)
        .build()

    override fun getMangaUrl(manga: SManga): String = absoluteUrl(baseUrl, manga.url)

    override fun getChapterUrl(chapter: SChapter): String = absoluteUrl(baseUrl, chapter.url)

    /**
     * `getFilterOptions()` hits the network but this method cannot suspend, so the first call
     * returns a stub list and kicks the load off in the background; reopening the dialog shows the
     * real thing.
     */
    override fun getFilterList(): FilterList {
        val options = filterOptions
        if (options == null && filterOptionsLoading.compareAndSet(false, true)) {
            scope.launch {
                try {
                    filterOptions = parser.getFilterOptions()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Ignored on purpose: the dialog keeps working with the sort filter alone.
                } finally {
                    filterOptionsLoading.set(false)
                }
            }
        }
        return buildFilterList(parser, options)
    }

    // MIKO -->
    override suspend fun awaitFilterList(): FilterList {
        if (filterOptions == null) {
            if (filterOptionsLoading.compareAndSet(false, true)) {
                try {
                    filterOptions = parser.getFilterOptions()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Same as getFilterList(): the sort-only list is still usable.
                } finally {
                    filterOptionsLoading.set(false)
                }
            } else {
                // The filter dialog already kicked the load off in the background: wait for it
                // instead of issuing a second identical request.
                withTimeoutOrNull(FILTER_OPTIONS_WAIT_MS) {
                    while (filterOptions == null && filterOptionsLoading.get()) delay(100)
                }
            }
        }
        return buildFilterList(parser, filterOptions)
    }
    // MIKO <--

    override suspend fun getRelatedMangaListByExtension(
        manga: SManga,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) {
        val related = try {
            parser.getRelatedManga(manga.toManga(parser, baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Related manga is a best-effort extra; a failure must not break the manga screen.
            emptyList()
        }
        if (related.isNotEmpty()) {
            pushResults(Pair("", related.map { it.toSManga() }), false)
        }
    }

    /**
     * Same `SharedPreferences` file the parser config reads through `MangaSourceConfig`, so the
     * settings screen below and the parser always see the same values.
     */
    override fun getSourcePreferences(): SharedPreferences =
        app.getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context
        val keys = mutableListOf<ConfigKey<*>>()
        parser.onCreateConfig(keys)
        for (configKey in keys) {
            val preference: Preference? = when (configKey) {
                is ConfigKey.Domain -> ListPreference(context).apply {
                    key = configKey.key
                    title = "Domain"
                    entries = configKey.presetValues.toCharSequenceArray()
                    entryValues = configKey.presetValues.toCharSequenceArray()
                    setDefaultValue(configKey.defaultValue)
                    summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    isIconSpaceReserved = false
                }
                is ConfigKey.UserAgent -> EditTextPreference(context).apply {
                    key = configKey.key
                    title = "User agent"
                    dialogTitle = "User agent"
                    setDefaultValue(configKey.defaultValue)
                    summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                    isIconSpaceReserved = false
                }
                is ConfigKey.PreferredImageServer -> {
                    val servers = configKey.presetValues.entries.toList()
                    ListPreference(context).apply {
                        key = configKey.key
                        title = "Image server"
                        entries = Array<CharSequence>(servers.size) { servers[it].value ?: "Default" }
                        entryValues = Array<CharSequence>(servers.size) { servers[it].key ?: "" }
                        setDefaultValue(configKey.defaultValue ?: "")
                        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        isIconSpaceReserved = false
                    }
                }
                is ConfigKey.ShowSuspiciousContent -> switchPreference(
                    context = context,
                    key = configKey.key,
                    title = "Show suspicious content",
                    defaultValue = configKey.defaultValue,
                )
                is ConfigKey.SplitByTranslations -> switchPreference(
                    context = context,
                    key = configKey.key,
                    title = "Split chapters by translation",
                    defaultValue = configKey.defaultValue,
                )
                is ConfigKey.DisableUpdateChecking -> switchPreference(
                    context = context,
                    key = configKey.key,
                    title = "Disable update checking",
                    defaultValue = configKey.defaultValue,
                )
                is ConfigKey.InterceptCloudflare -> switchPreference(
                    context = context,
                    key = configKey.key,
                    title = "Intercept CloudFlare challenges",
                    defaultValue = configKey.defaultValue,
                )
                // Defensive: the library may add config keys the module does not know about yet.
                else -> null
            }
            if (preference != null) {
                screen.addPreference(preference)
            }
        }
    }

    /**
     * Walks the parser forward until [page] is reached.
     *
     * Kotatsu takes an accumulated offset instead of a page number, so the only page whose offset is
     * known up front is the first one. Sequential browsing hits [KotatsuPaging.offsetFor] directly;
     * a jump (deep link, process death, restored paging state) replays the pages in between.
     */
    // MIKO — `protected` so a dedicated subclass can reuse the paging walk in its own overrides.
    protected suspend fun loadList(order: SortOrder, filter: MangaListFilter, page: Int): MangasPage = kotatsuCall {
        val key = "${order.name}|${filter.hashCode()}"
        var currentPage = if (paging.offsetFor(key, page) != null) page else paging.lastKnownPage(key, page)
        // Bound the catch-up walk: a jump farther than MAX_CATCH_UP_PAGES from the last known page
        // would mean one network round-trip per intermediate page. Refuse instead of hanging.
        if (page - currentPage > MAX_CATCH_UP_PAGES) {
            throw IOException(
                "Cannot jump to page $page of ${parserSource.title}: only page $currentPage is known. " +
                    "Reload the list from the start.",
            )
        }
        var offset = paging.offsetFor(key, currentPage) ?: 0
        var result: List<Manga> = emptyList()
        while (true) {
            result = parser.getList(offset, order, filter)
            paging.record(key, currentPage, result.size)
            if (currentPage >= page || result.isEmpty()) break
            currentPage++
            offset = paging.offsetFor(key, currentPage) ?: break
        }
        val mangas = if (currentPage == page) result else emptyList()
        mangas.forEach(::rememberRating)
        // Neither side reports a total count, so a non-empty page is the only "there may be more".
        MangasPage(mangas.map { it.toSManga() }, mangas.isNotEmpty())
    }

    private fun switchPreference(
        context: Context,
        key: String,
        title: String,
        defaultValue: Boolean,
    ): SwitchPreferenceCompat = SwitchPreferenceCompat(context).apply {
        this.key = key
        this.title = title
        setDefaultValue(defaultValue)
        isIconSpaceReserved = false
    }

    private fun Array<out String>.toCharSequenceArray(): Array<CharSequence> =
        Array<CharSequence>(size) { this[it] }

    private companion object {
        /** Upper bound for waiting on a filter-options load started by the filter dialog. */
        const val FILTER_OPTIONS_WAIT_MS = 10_000L

        const val LANG_ALL = "all"

        /** Max pages replayed sequentially to reach a page whose offset is unknown. */
        const val MAX_CATCH_UP_PAGES = 10
        const val REMOTE_RATING_CACHE_SIZE = 500
    }
}
