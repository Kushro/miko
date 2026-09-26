package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaTag

/** MIKO — persistence of user-defined tags (`manga_tags`). Names are compared case-insensitively. */
interface MangaTagRepository {

    /** Every local tag of every manga (library-wide search/filter). */
    fun subscribeAll(): Flow<List<MangaTag>>

    fun subscribeByMangaId(mangaId: Long): Flow<List<MangaTag>>

    suspend fun getByMangaId(mangaId: Long): List<MangaTag>

    /** Distinct tag names in use (for autocomplete), most used first. */
    suspend fun getAllNames(): List<String>

    /** Inserts and returns the new id, or null when the manga already has that tag. */
    suspend fun insert(mangaId: Long, name: String, namespace: String? = null): Long?

    suspend fun delete(id: Long)

    suspend fun deleteByName(mangaId: Long, name: String)

    /** Renames a tag across every manga. */
    suspend fun rename(oldName: String, newName: String)

    /** Replaces the whole local tag list of a manga (backup restore). */
    suspend fun replaceAll(mangaId: Long, names: List<String>)
}
