package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.MangaTag
import tachiyomi.domain.manga.repository.MangaTagRepository

/**
 * MIKO — SQLDelight implementation of [MangaTagRepository] on top of `manga_tags`.
 *
 * Name comparisons are case-insensitive (`COLLATE NOCASE` in the queries plus the unique index
 * `idx_manga_tags_manga_name`), so [insert] returning `null` means "this manga already has that
 * tag" rather than a real failure.
 */
class MangaTagRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaTagRepository {

    override fun subscribeAll(): Flow<List<MangaTag>> {
        return handler.subscribeToList { manga_tagsQueries.getAll(::mapMangaTag) }
    }

    override fun subscribeByMangaId(mangaId: Long): Flow<List<MangaTag>> {
        return handler.subscribeToList { manga_tagsQueries.getByMangaId(mangaId, ::mapMangaTag) }
    }

    override suspend fun getByMangaId(mangaId: Long): List<MangaTag> {
        return handler.awaitList { manga_tagsQueries.getByMangaId(mangaId, ::mapMangaTag) }
    }

    override suspend fun getAllNames(): List<String> {
        return handler.awaitList { manga_tagsQueries.getAllNames() }
    }

    override suspend fun insert(mangaId: Long, name: String, namespace: String?): Long? {
        return try {
            handler.awaitOneOrNullExecutable(inTransaction = true) {
                manga_tagsQueries.insert(
                    mangaId = mangaId,
                    name = name,
                    namespace = namespace,
                    createdAt = System.currentTimeMillis(),
                )
                manga_tagsQueries.selectLastInsertedRowId()
            }
        } catch (e: Exception) {
            // Unique index violation (the manga already has that tag) or a dangling manga id. The
            // concrete exception depends on the SQLite helper factory in use (requery/SQLCipher),
            // so catch broadly and let callers treat null as "not inserted".
            logcat(LogPriority.DEBUG, e) { "Could not insert tag '$name' on manga $mangaId" }
            null
        }
    }

    override suspend fun delete(id: Long) {
        handler.await { manga_tagsQueries.delete(id) }
    }

    override suspend fun deleteByName(mangaId: Long, name: String) {
        handler.await { manga_tagsQueries.deleteByName(mangaId, name) }
    }

    override suspend fun rename(oldName: String, newName: String) {
        handler.await { manga_tagsQueries.rename(newName = newName, oldName = oldName) }
    }

    override suspend fun replaceAll(mangaId: Long, names: List<String>) {
        val createdAt = System.currentTimeMillis()
        val tags = names.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.splitNamespace() }
            // The unique index is (manga_id, name NOCASE) regardless of namespace: keep the first of
            // any same-name pair so a restore can never trip the constraint mid-transaction.
            .distinctBy { (_, name) -> name.lowercase() }
            .toList()
        handler.await(inTransaction = true) {
            manga_tagsQueries.deleteByMangaId(mangaId)
            tags.forEach { (namespace, name) ->
                manga_tagsQueries.insert(
                    mangaId = mangaId,
                    name = name,
                    namespace = namespace,
                    createdAt = createdAt,
                )
            }
        }
    }
}

private fun mapMangaTag(
    id: Long,
    mangaId: Long,
    name: String,
    namespace: String?,
    createdAt: Long,
): MangaTag = MangaTag(
    id = id,
    mangaId = mangaId,
    name = name,
    namespace = namespace,
    createdAt = createdAt,
)

/**
 * Inverse of [MangaTag.displayName]: `"ns:tag"` becomes `("ns", "tag")`. Only a colon with no
 * surrounding whitespace and non-empty halves counts as a namespace separator, so ordinary names
 * such as `"Vol: 3"` survive untouched. Used when restoring a flat list of names from a backup.
 */
private fun String.splitNamespace(): Pair<String?, String> {
    val index = indexOf(':')
    if (index <= 0 || index == lastIndex) return null to this
    val namespace = substring(0, index)
    val name = substring(index + 1)
    if (namespace.last().isWhitespace() || name.first().isWhitespace() || name.isBlank()) {
        return null to this
    }
    return namespace to name
}
