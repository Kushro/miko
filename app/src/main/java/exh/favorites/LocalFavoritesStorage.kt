package exh.favorites

import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.EXH_SOURCE_ID
import exh.source.isEhBasedManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.InsertFavoriteEntries
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LocalFavoritesStorage(
    private val getFavorites: GetFavorites = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val deleteFavoriteEntries: DeleteFavoriteEntries = Injekt.get(),
    private val getFavoriteEntries: GetFavoriteEntries = Injekt.get(),
    private val insertFavoriteEntries: InsertFavoriteEntries = Injekt.get(),
) {

    // MIKO -->
    // Everything below works in the "upstream slot" space of the current [EhFavoritesSyncPlan]:
    // only slots that are mapped, enabled and non-empty upstream take part in a sync run, in both
    // the local and the remote direction, and in the persisted snapshot.
    suspend fun getChangedDbEntries(plan: EhFavoritesSyncPlan) = getFavorites.await()
        .asFlow()
        .loadDbCategories(plan)
        .parseToFavoriteEntries()
        .getChangedEntries(plan)

    suspend fun getChangedRemoteEntries(
        entries: List<EHentai.ParsedManga>,
        plan: EhFavoritesSyncPlan,
    ) = filterEntriesForPlan(plan, entries) { it.fav }
        .asFlow()
        .map {
            it.fav to it.manga.toDomainManga(EXH_SOURCE_ID).copy(
                favorite = true,
                dateAdded = System.currentTimeMillis(),
            )
        }
        .parseToFavoriteEntries()
        .getChangedEntries(plan)

    suspend fun snapshotEntries(plan: EhFavoritesSyncPlan) {
        val dbMangas = getFavorites.await()
            .asFlow()
            .loadDbCategories(plan)
            .parseToFavoriteEntries()

        // Delete old snapshot
        deleteFavoriteEntries.await()

        // Insert new snapshots (active slots only)
        insertFavoriteEntries.await(dbMangas.toList())
    }
    // MIKO <--

    suspend fun clearSnapshots() {
        deleteFavoriteEntries.await()
    }

    private suspend fun Flow<FavoriteEntry>.getChangedEntries(plan: EhFavoritesSyncPlan): ChangeSet {
        val terminated = toList()

        // MIKO --> entries of slots outside this run must not produce additions/removals
        val databaseEntries = filterEntriesForPlan(plan, getFavoriteEntries.await()) { it.category }
        // MIKO <--

        val added = terminated.groupBy { it.gid to it.token }
            .filter { (_, values) ->
                values.all { queryListForEntry(databaseEntries, it) == null }
            }
            .map { it.value.first() }

        val removed = databaseEntries
            .groupBy { it.gid to it.token }
            .filter { (_, values) ->
                values.all { queryListForEntry(terminated, it) == null }
            }
            .map { it.value.first() }

        return ChangeSet(added, removed)
    }

    private fun FavoriteEntry.urlEquals(other: FavoriteEntry) = (gid == other.gid && token == other.token) ||
        (otherGid != null && otherToken != null && (otherGid == other.gid && otherToken == other.token)) ||
        (other.otherGid != null && other.otherToken != null && (gid == other.otherGid && token == other.otherToken)) ||
        (
            otherGid != null &&
                otherToken != null &&
                other.otherGid != null &&
                other.otherToken != null &&
                otherGid == other.otherGid &&
                otherToken == other.otherToken
            )

    private fun queryListForEntry(list: List<FavoriteEntry>, entry: FavoriteEntry) =
        list.find { it.urlEquals(entry) && it.category == entry.category }

    // MIKO -->
    /**
     * Resolves the upstream slot of every local gallery through the mapping: its first category
     * that is mapped to an active slot. Galleries in unmapped/inactive categories are dropped.
     */
    private suspend fun Flow<Manga>.loadDbCategories(plan: EhFavoritesSyncPlan): Flow<Pair<Int, Manga>> {
        return filter(::validateDbManga).mapNotNull { manga ->
            val categoryIds = getCategories.await(manga.id).map { it.id }
            val slot = slotOfLocalCategories(plan, categoryIds) ?: return@mapNotNull null
            slot to manga
        }
    }

    private fun Flow<Pair<Int, Manga>>.parseToFavoriteEntries() =
        filter { (_, manga) ->
            validateDbManga(manga)
        }.map { (slot, manga) ->
            FavoriteEntry(
                title = manga.ogTitle,
                gid = EHentaiSearchMetadata.galleryId(manga.url),
                token = EHentaiSearchMetadata.galleryToken(manga.url),
                category = slot,
            )
        }
    // MIKO <--

    private fun validateDbManga(manga: Manga) =
        manga.favorite && manga.isEhBasedManga()
}

data class ChangeSet(
    val added: List<FavoriteEntry>,
    val removed: List<FavoriteEntry>,
)
