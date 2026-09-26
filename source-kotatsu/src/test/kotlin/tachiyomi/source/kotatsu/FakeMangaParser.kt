// SPDX-License-Identifier: GPL-3.0-or-later
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@file:OptIn(InternalParsersApi::class)

package tachiyomi.source.kotatsu

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Favicons
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.util.LinkResolver
import java.util.EnumSet

/**
 * In-memory [MangaParser] with fixed data, used to exercise the mappers without any network or
 * Android dependency.
 *
 * The whole interface has to be implemented (including the deprecated members and the
 * `okhttp3.Interceptor` it extends) because the library declares no default implementations outside
 * of `AbstractMangaParser`, which is not usable here — it needs a real `MangaLoaderContext`.
 */
class FakeMangaParser(
    override val source: MangaParserSource = MangaParserSource.entries.first(),
    override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY),
    override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
        isMultipleTagsSupported = true,
        isTagsExclusionSupported = true,
        isSearchSupported = true,
        isSearchWithFiltersSupported = true,
    ),
) : MangaParser {

    override val searchQueryCapabilities: MangaSearchQueryCapabilities = MangaSearchQueryCapabilities()

    override val config: MangaSourceConfig = FakeSourceConfig

    override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(DOMAIN)

    override val domain: String
        get() = DOMAIN

    val tags: List<MangaTag> = listOf(
        MangaTag(title = "Action", key = "action", source = source),
        MangaTag(title = "Comedy", key = "comedy", source = source),
        MangaTag(title = "Drama", key = "drama", source = source),
    )

    val filterOptions: MangaListFilterOptions = MangaListFilterOptions(
        availableTags = LinkedHashSet(tags),
        availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
    )

    val mangas: List<Manga> = listOf(
        manga(url = "/manga/first", title = "First", state = MangaState.ONGOING),
        manga(url = "/manga/second", title = "Second", state = MangaState.FINISHED),
    )

    /** Ascending, the order the library normalises every parser to. */
    val chapters: List<MangaChapter> = listOf(
        chapter(url = "/manga/first/1", title = null, number = 1f, volume = 0),
        chapter(url = "/manga/first/2", title = "The second one", number = 2f, volume = 1),
        chapter(url = "/manga/first/3", title = null, number = 3.5f, volume = 2),
    )

    val pages: List<MangaPage> = listOf(
        MangaPage(id = 1L, url = "/page/1", preview = null, source = source),
        MangaPage(id = 2L, url = "/page/2", preview = null, source = source),
    )

    /** Every offset [getList] was called with, in order. */
    val requestedOffsets: MutableList<Int> = mutableListOf()

    override suspend fun getList(query: MangaSearchQuery): List<Manga> = mangas

    override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        requestedOffsets += offset
        return mangas.drop(offset)
    }

    override suspend fun getDetails(manga: Manga): Manga = manga.copy(chapters = chapters)

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = pages

    override suspend fun getPageUrl(page: MangaPage): String = "https://$DOMAIN${page.url}"

    override suspend fun getFilterOptions(): MangaListFilterOptions = filterOptions

    override suspend fun getFavicons(): Favicons = Favicons.EMPTY

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        keys.add(configKeyDomain)
    }

    override suspend fun getRelatedManga(seed: Manga): List<Manga> = mangas

    override fun getRequestHeaders(): Headers = Headers.headersOf("User-Agent", "FakeAgent")

    override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? = null

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())

    private fun manga(url: String, title: String, state: MangaState) = Manga(
        id = url.hashCode().toLong(),
        title = title,
        altTitles = setOf("$title (alt)"),
        url = url,
        publicUrl = "https://$DOMAIN$url",
        rating = RATING_UNKNOWN,
        contentRating = ContentRating.SAFE,
        coverUrl = "https://$DOMAIN$url/cover.jpg",
        tags = LinkedHashSet(tags.take(2)),
        state = state,
        authors = linkedSetOf("Author One", "Author Two", "Author Three"),
        largeCoverUrl = "https://$DOMAIN$url/cover-large.jpg",
        description = "Description of $title",
        chapters = null,
        source = source,
    )

    private fun chapter(url: String, title: String?, number: Float, volume: Int) = MangaChapter(
        id = url.hashCode().toLong(),
        title = title,
        number = number,
        volume = volume,
        url = url,
        scanlator = "Scans",
        uploadDate = 1_700_000_000_000L + number.toLong(),
        branch = "English",
        source = source,
    )

    private object FakeSourceConfig : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }

    companion object {
        const val DOMAIN = "fake.test"
    }
}
