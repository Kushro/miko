package exh.favorites

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.system.toast
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.eh.EHentaiUpdateWorker
import exh.log.ResettableLogger
import exh.log.safeXLogTag
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.ExhPreferences
import exh.source.isEhBasedManga
import exh.util.ThrottleManager
import exh.util.createPartialWakeLock
import exh.util.createWifiLock
import exh.util.ignore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.miko.MKMR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds

// TODO only apply database changes after sync
class FavoritesSyncHelper(val context: Context) {
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()

    private val exhPreferences: ExhPreferences by injectLazy()

    private val exh by lazy {
        Injekt.get<SourceManager>().get(EXH_SOURCE_ID) as? EHentai
            ?: EHentai(0, true, context)
    }

    private val storage by lazy { LocalFavoritesStorage() }

    private val galleryAdder by lazy { GalleryAdder() }

    private val throttleManager by lazy { ThrottleManager() }

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // KMK -->
    private val logger = ResettableLogger { safeXLogTag() }
    // KMK <--

    val status: MutableStateFlow<FavoritesSyncStatus> = MutableStateFlow(FavoritesSyncStatus.Idle)

    @Synchronized
    fun runSync(scope: CoroutineScope) {
        if (status.value !is FavoritesSyncStatus.Idle) {
            return
        }

        status.value = FavoritesSyncStatus.Initializing

        scope.launch(Dispatchers.IO) { beginSync() }
    }

    private suspend fun beginSync() {
        // Check if logged in
        if (!exhPreferences.enableExhentai().get()) {
            status.value = FavoritesSyncStatus.SyncError.NotLoggedInSyncError
            return
        }

        // MIKO -->
        // The sync only ever touches upstream slots the user explicitly mapped to a local category
        // and enabled (see docs/features/favorites-sync/en.md).
        val config = exhPreferences.exhFavoritesSyncConfig().get()
        if (!config.isConfigured) {
            status.value = FavoritesSyncStatus.SyncError.NotConfigured
            return
        }
        // MIKO <--

        // Download remote favorites
        val favorites = try {
            status.value = FavoritesSyncStatus.Processing.DownloadingFavorites
            // MIKO --> also parses the upstream slot names + counts of the favorites page
            exh.fetchFavoritesWithSlots()
            // MIKO <--
        } catch (e: Exception) {
            status.value = FavoritesSyncStatus.SyncError.FailedToFetchFavorites
            logger()?.e(context.stringResource(SYMR.strings.favorites_sync_could_not_fetch), e)
            return
        }

        // MIKO -->
        // Resolve the mapping against the categories that exist right now
        val localCategories = getCategories.await()
            .filterNot(Category::isSystemCategory)
        val plan = EhFavoritesSyncPlan.build(
            config = config,
            localCategories = localCategories.map { it.id to it.name },
            upstreamSlotOfEachGallery = favorites.first.map { it.fav },
        )

        if (plan.missingCategoryNames.isNotEmpty()) {
            status.value = FavoritesSyncStatus.SyncError.MappingCategoriesMissing(plan.missingCategoryNames)
            return
        }

        // Refresh the display-only cache of upstream slot names/counts used by the settings dialog
        status.value = FavoritesSyncStatus.Processing.RefreshingUpstreamSlots
        exhPreferences.exhFavoritesUpstreamSlots().set(
            favorites.second.map { slot ->
                slot.copy(count = maxOf(slot.count, plan.upstreamCounts[slot.slot] ?: 0))
            },
        )

        if (plan.activeSlots.isEmpty()) {
            // Every enabled slot is empty upstream: nothing to pull and nothing to push
            withUIContext {
                context.toast(MKMR.strings.eh_favorites_sync_nothing_active)
            }
            status.value = FavoritesSyncStatus.Idle
            return
        }

        // Validate library state: a gallery may live in at most one ACTIVE mapped category
        status.value = FavoritesSyncStatus.Processing.VerifyingLibrary
        val activeCategoryIds = plan.activeSlots.mapNotNull(plan::categoryIdFor).toSet()
        getLibraryManga.await().forEach { libraryManga ->
            val manga = libraryManga.manga
            if (!manga.isEhBasedManga()) return@forEach

            val mappedCategoryIds = libraryManga.categories.filter { it in activeCategoryIds }
            if (mappedCategoryIds.size > 1) {
                status.value = FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories(
                    manga.id,
                    manga.title,
                    localCategories.filter { it.id in mappedCategoryIds }.map { it.name },
                )

                logger()?.w(context.stringResource(SYMR.strings.favorites_sync_gallery_multiple_categories_error, manga.id))
                return
            }
        }
        // MIKO <--

        val errorList = mutableListOf<FavoritesSyncStatus.SyncError.GallerySyncError>()

        try {
            // Take wake + wifi locks
            ignore { wakeLock?.release() }
            wakeLock = ignore { context.createPartialWakeLock("teh:ExhFavoritesSyncWakelock") }
            ignore { wifiLock?.release() }
            wifiLock = ignore { context.createWifiLock("teh:ExhFavoritesSyncWifi") }

            // Do not update galleries while syncing favorites
            EHentaiUpdateWorker.cancelBackground(context)

            status.value = FavoritesSyncStatus.Processing.CalculatingRemoteChanges
            // MIKO --> every diff is restricted to the active slots of the plan
            val remoteChanges = storage.getChangedRemoteEntries(favorites.first, plan)
            val localChanges = if (exhPreferences.exhReadOnlySync().get()) {
                null // Do not build local changes if they are not going to be applied
            } else {
                status.value = FavoritesSyncStatus.Processing.CalculatingLocalChanges
                storage.getChangedDbEntries(plan)
            }

            // Local category names/order are never touched by the sync anymore

            // Apply change sets
            applyChangeSetToLocal(errorList, remoteChanges, plan)
            if (localChanges != null) {
                applyChangeSetToRemote(errorList, localChanges)
            }

            status.value = FavoritesSyncStatus.Processing.CleaningUp
            storage.snapshotEntries(plan)

            withUIContext {
                context.toast(SYMR.strings.favorites_sync_complete)
                if (plan.skippedEmptySlots.isNotEmpty()) {
                    context.toast(
                        context.stringResource(
                            MKMR.strings.eh_favorites_sync_skipped_empty,
                            plan.skippedEmptySlots.size,
                        ),
                    )
                }
            }
            if (plan.skippedEmptySlots.isNotEmpty()) {
                logger()?.w("Skipped empty upstream slots: ${plan.skippedEmptySlots.joinToString()}")
            }
            // MIKO <--
        } catch (e: IgnoredException) {
            // Do not display error as this error has already been reported
            logger()?.w(context.stringResource(SYMR.strings.favorites_sync_ignoring_exception), e)
            return
        } catch (e: Exception) {
            status.value = FavoritesSyncStatus.SyncError.UnknownSyncError(e.message.orEmpty())
            logger()?.e(context.stringResource(SYMR.strings.favorites_sync_sync_error), e)
            return
        } finally {
            // Release wake + wifi locks
            ignore {
                wakeLock?.release()
                wakeLock = null
            }
            ignore {
                wifiLock?.release()
                wifiLock = null
            }

            // Update galleries again!
            EHentaiUpdateWorker.scheduleBackground(context)
        }

        if (errorList.isEmpty()) {
            status.value = FavoritesSyncStatus.Idle
        } else {
            status.value = FavoritesSyncStatus.CompleteWithErrors(errorList)
        }
    }

    private suspend fun addGalleryRemote(errorList: MutableList<FavoritesSyncStatus.SyncError.GallerySyncError>, gallery: FavoriteEntry) {
        val url = "${exh.baseUrl}/gallerypopups.php?gid=${gallery.gid}&t=${gallery.token}&act=addfav"

        val request = POST(
            url = url,
            body = FormBody.Builder()
                .add("favcat", gallery.category.toString())
                .add("favnote", "")
                .add("apply", "Add to Favorites")
                .add("update", "1")
                .build(),
        )

        if (!explicitlyRetryExhRequest(10, request)) {
            val error = FavoritesSyncStatus.SyncError.GallerySyncError.UnableToAddGalleryToRemote(
                gallery.title,
                gallery.gid,
            )

            if (exhPreferences.exhLenientSync().get()) {
                errorList += error
            } else {
                status.value = error
                throw IgnoredException(error)
            }
        }
    }

    private suspend fun explicitlyRetryExhRequest(retryCount: Int, request: Request): Boolean {
        var success = false

        for (i in 1..retryCount) {
            try {
                val resp = withIOContext { exh.client.newCall(request).await() }

                if (resp.isSuccessful) {
                    success = true
                    break
                }
            } catch (e: Exception) {
                logger()?.w(context.stringResource(SYMR.strings.favorites_sync_network_error), e)
            }
        }

        return success
    }

    private suspend fun applyChangeSetToRemote(
        errorList: MutableList<FavoritesSyncStatus.SyncError.GallerySyncError>,
        changeSet: ChangeSet,
    ) {
        // Apply removals
        if (changeSet.removed.isNotEmpty()) {
            status.value = FavoritesSyncStatus.Processing.RemovingRemoteGalleries(changeSet.removed.size)

            val formBody = FormBody.Builder()
                .add("ddact", "delete")
                .add("apply", "Apply")

            // Add change set to form
            changeSet.removed.forEach {
                formBody.add("modifygids[]", it.gid)
            }

            val request = POST(
                url = "https://exhentai.org/favorites.php",
                body = formBody.build(),
            )

            if (!explicitlyRetryExhRequest(10, request)) {
                if (exhPreferences.exhLenientSync().get()) {
                    errorList += FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote
                } else {
                    status.value = FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote
                    throw IgnoredException(FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote)
                }
            }
        }

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing.AddingGalleryToRemote(
                index = index + 1,
                total = changeSet.added.size,
                isThrottling = needWarnThrottle(),
                title = it.title,
            )

            throttleManager.throttle()

            addGalleryRemote(errorList, it)
        }
    }

    private suspend fun applyChangeSetToLocal(
        errorList: MutableList<FavoritesSyncStatus.SyncError.GallerySyncError>,
        changeSet: ChangeSet,
        // MIKO --> slot → local category id resolution
        plan: EhFavoritesSyncPlan,
        // MIKO <--
    ) {
        val removedManga = mutableListOf<Manga>()

        // Apply removals
        changeSet.removed.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing.RemovingGalleryFromLocal(
                index = index + 1,
                total = changeSet.removed.size,
            )
            val url = it.getUrl()

            // Consider both EX and EH sources
            listOf(
                EXH_SOURCE_ID,
                EH_SOURCE_ID,
            ).forEach {
                val manga = getManga.await(url, it)

                if (manga?.favorite == true) {
                    updateManga.awaitUpdateFavorite(manga.id, false)
                    removedManga += manga
                }
            }
        }

        // Can't do too many DB OPs in one go
        removedManga.forEach {
            setMangaCategories.await(it.id, emptyList())
        }

        val insertedMangaCategories = mutableListOf<Pair<Long, Manga>>()

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing.AddingGalleryToLocal(
                index = index + 1,
                total = changeSet.added.size,
                isThrottling = needWarnThrottle(),
                title = it.title,
            )

            throttleManager.throttle()

            // Import using gallery adder
            val result = galleryAdder.addGallery(
                context = context,
                url = "${exh.baseUrl}${it.getUrl()}",
                fav = true,
                forceSource = exh,
                throttleFunc = throttleManager::throttle,
                retry = 3,
            )

            if (result is GalleryAddEvent.Fail) {
                if (result is GalleryAddEvent.Fail.NotFound) {
                    logger()?.e(context.stringResource(SYMR.strings.favorites_sync_remote_not_exist, it.getUrl()))
                    // Skip this gallery, it no longer exists
                    return@forEachIndexed
                }

                val error = when (result) {
                    is GalleryAddEvent.Fail.Error -> FavoritesSyncStatus.SyncError.GallerySyncError.GalleryAddFail(it.title, result.logMessage)
                    is GalleryAddEvent.Fail.UnknownType -> FavoritesSyncStatus.SyncError.GallerySyncError.InvalidGalleryFail(it.title, result.galleryUrl)
                    is GalleryAddEvent.Fail.UnknownSource -> FavoritesSyncStatus.SyncError.GallerySyncError.InvalidGalleryFail(it.title, result.galleryUrl)
                }

                if (exhPreferences.exhLenientSync().get()) {
                    errorList += error
                } else {
                    status.value = error
                    throw IgnoredException(error)
                }
            } else if (result is GalleryAddEvent.Success) {
                // MIKO --> the slot decides the local category, never the category order
                val categoryId = plan.categoryIdFor(it.category)
                if (categoryId == null) {
                    logger()?.w("Upstream slot ${it.category} is not mapped to a local category, skipping ${it.title}")
                    return@forEachIndexed
                }
                insertedMangaCategories += categoryId to result.manga
                // MIKO <--
            }
        }

        // Can't do too many DB OPs in one go
        insertedMangaCategories.forEach { (category, manga) ->
            setMangaCategories.await(manga.id, listOf(category))
        }
    }

    private fun needWarnThrottle() =
        throttleManager.throttleTime >= THROTTLE_WARN

    class IgnoredException(message: FavoritesSyncStatus.SyncError.GallerySyncError) : RuntimeException(message.toString())

    companion object {
        private val THROTTLE_WARN = 1.seconds
    }
}

@Serializable
sealed class FavoritesSyncStatus {
    @Serializable
    sealed class SyncError : FavoritesSyncStatus() {
        @Serializable
        data object NotLoggedInSyncError : SyncError()

        @Serializable
        data object FailedToFetchFavorites : SyncError()

        // MIKO -->
        /** No upstream slot is mapped to a local category and enabled (or a category is mapped twice). */
        @Serializable
        data object NotConfigured : SyncError()

        /** Enabled slots point at local categories that no longer exist. */
        @Serializable
        data class MappingCategoriesMissing(val names: List<String>) : SyncError()
        // MIKO <--

        @Serializable
        data class UnknownSyncError(val message: String) : SyncError()

        @Serializable
        sealed class GallerySyncError : SyncError() {
            @Serializable
            data class UnableToAddGalleryToRemote(val title: String, val gid: String) : GallerySyncError()

            @Serializable
            data object UnableToDeleteFromRemote : GallerySyncError()

            @Serializable
            data class GalleryAddFail(val title: String, val reason: String) : GallerySyncError()

            @Serializable
            data class InvalidGalleryFail(val title: String, val url: String) : GallerySyncError()
        }
    }

    @Serializable
    data object Idle : FavoritesSyncStatus()

    @Serializable
    sealed class BadLibraryState : FavoritesSyncStatus() {
        @Serializable
        data class MangaInMultipleCategories(
            val mangaId: Long,
            val mangaTitle: String,
            val categories: List<String>,
        ) : BadLibraryState()
    }

    @Serializable
    data object Initializing : FavoritesSyncStatus()

    @Serializable
    sealed class Processing : FavoritesSyncStatus() {
        data object VerifyingLibrary : Processing()
        data object DownloadingFavorites : Processing()
        data object CalculatingRemoteChanges : Processing()
        data object CalculatingLocalChanges : Processing()
        // MIKO --> replaces SyncingCategoryNames: names/order are no longer pushed onto the library
        data object RefreshingUpstreamSlots : Processing()
        // MIKO <--
        data class RemovingRemoteGalleries(val galleryCount: Int) : Processing()
        data class AddingGalleryToRemote(
            val index: Int,
            val total: Int,
            val isThrottling: Boolean,
            val title: String,
        ) : Processing()
        data class RemovingGalleryFromLocal(
            val index: Int,
            val total: Int,
        ) : Processing()
        data class AddingGalleryToLocal(
            val index: Int,
            val total: Int,
            val isThrottling: Boolean,
            val title: String,
        ) : Processing()
        data object CleaningUp : Processing()
    }

    @Serializable
    data class CompleteWithErrors(val messages: List<SyncError.GallerySyncError>) : FavoritesSyncStatus()
}
