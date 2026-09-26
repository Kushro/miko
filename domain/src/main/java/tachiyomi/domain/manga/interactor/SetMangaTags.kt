package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaTagRepository

/** MIKO — writes local (user-defined) tags. */
class SetMangaTags(
    private val repository: MangaTagRepository,
) {

    /** Adds a tag; returns false when the manga already had it. Blank names are ignored. */
    suspend fun add(mangaId: Long, name: String, namespace: String? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return repository.insert(mangaId, trimmed, namespace?.trim()?.ifEmpty { null }) != null
    }

    suspend fun remove(mangaId: Long, name: String) = repository.deleteByName(mangaId, name.trim())

    suspend fun remove(id: Long) = repository.delete(id)

    suspend fun rename(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        repository.rename(oldName, trimmed)
    }

    suspend fun replaceAll(mangaId: Long, names: List<String>) =
        repository.replaceAll(mangaId, names.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
}
