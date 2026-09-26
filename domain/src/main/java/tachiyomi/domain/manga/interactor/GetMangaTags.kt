package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaTag
import tachiyomi.domain.manga.repository.MangaTagRepository

/** MIKO — reads local (user-defined) tags. */
class GetMangaTags(
    private val repository: MangaTagRepository,
) {

    fun subscribe(mangaId: Long): Flow<List<MangaTag>> = repository.subscribeByMangaId(mangaId)

    fun subscribeAll(): Flow<List<MangaTag>> = repository.subscribeAll()

    suspend fun await(mangaId: Long): List<MangaTag> = repository.getByMangaId(mangaId)

    suspend fun awaitAllNames(): List<String> = repository.getAllNames()
}
