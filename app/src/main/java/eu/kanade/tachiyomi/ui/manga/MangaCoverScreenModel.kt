package eu.kanade.tachiyomi.ui.manga

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.waifu2x.ImageEnhancementCache
import eu.kanade.tachiyomi.util.waifu2x.RemoteUpscaler
import eu.kanade.tachiyomi.util.waifu2x.Waifu2x
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MangaCoverScreenModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    // MIKO -->
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    // MIKO <--

    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<Manga?>(null) {

    init {
        screenModelScope.launchIO {
            getManga.subscribe(mangaId)
                .collect { newManga ->
                    mutableState.update { newManga }
                    // MIKO --> Keep the on-disk cover size in sync with whatever manga state lands
                    refreshCoverSize()
                    // MIKO <--
                }
        }
    }

    fun saveCover(context: Context) {
        screenModelScope.launch {
            try {
                saveCoverInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context) {
        screenModelScope.launch {
            try {
                val uri = saveCoverInternal(context, temp = true) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save manga cover Bitmap to picture or temporary share directory.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveCoverInternal(context: Context, temp: Boolean): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(manga)
            .size(Size.ORIGINAL)
            .build()

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }
    }

    /**
     * Update cover with local file.
     *
     * @param context Context.
     * @param data uri of the cover resource.
     */
    fun editCover(context: Context, data: Uri) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    manga.editCover(Injekt.get(), it, updateManga, coverCache)
                    notifyCoverUpdated(context)
                    refreshCoverSize()
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomCover(context: Context) {
        val mangaId = state.value?.id ?: return
        screenModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(mangaId)
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notifyCoverUpdated(context)
                refreshCoverSize()
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    // MIKO --> "Enhance" (upscale) the cover
    /**
     * Two-step flow behind the cover viewer's "Enhance" action: pick a mode, then watch it run.
     *
     * This lives here and not in `MangaScreenModel.Dialog` on purpose: that state holds a single
     * `dialog` field, already taken by `FullCover`, so putting these there would *replace* the
     * cover viewer instead of layering on top of it.
     */
    sealed interface EnhanceDialog {
        /** @param remoteAvailable false when no host is configured or the server isn't ready. */
        data class ChooseMode(val remoteAvailable: Boolean?) : EnhanceDialog

        /**
         * @param status human-readable step, as reported by the upscaler (remote only).
         * @param percent 0..100 while the on-device model reports progress, null otherwise.
         */
        data class InProgress(val status: String?, val percent: Int?) : EnhanceDialog
    }

    private val _enhanceDialog = MutableStateFlow<EnhanceDialog?>(null)
    val enhanceDialog: StateFlow<EnhanceDialog?> = _enhanceDialog.asStateFlow()

    private var enhanceJob: Job? = null

    /** Opens the mode picker and probes the remote server in the background to enable/disable it. */
    fun showEnhanceDialog() {
        _enhanceDialog.value = EnhanceDialog.ChooseMode(remoteAvailable = null)
        screenModelScope.launchIO {
            val host = readerPreferences.remoteUpscalerHost().get()
            val port = readerPreferences.remoteUpscalerPort().get()
            val available = if (host.isBlank()) {
                false
            } else {
                // An explicit upscaler_ready == false means the server is up but can't upscale yet.
                RemoteUpscaler.checkStatus(host, port)?.let { it["upscaler_ready"] != false } == true
            }
            _enhanceDialog.update { current ->
                if (current is EnhanceDialog.ChooseMode) current.copy(remoteAvailable = available) else current
            }
        }
    }

    fun dismissEnhanceDialog() {
        enhanceJob?.cancel()
        enhanceJob = null
        _enhanceDialog.value = null
    }

    /**
     * Upscales the current cover and stores the result as this manga's custom cover.
     *
     * Cancelling stops the remote request outright; on-device the native pass can't be interrupted,
     * so it finishes on its own and the result is discarded.
     */
    fun enhanceCover(context: Context, remote: Boolean) {
        val manga = state.value ?: return
        enhanceJob?.cancel()
        _enhanceDialog.value = EnhanceDialog.InProgress(status = null, percent = null)
        enhanceJob = screenModelScope.launchIO {
            try {
                val source = loadCoverBitmap(context, manga)
                if (source == null) {
                    failEnhance(context)
                    return@launchIO
                }
                val enhanced = if (remote) {
                    upscaleRemote(source)
                } else {
                    upscaleOnDevice(context, source)
                }
                if (enhanced == null) {
                    failEnhance(context)
                    return@launchIO
                }
                // Covers are far below the texture/WebP ceilings even at 4x, but clamp anyway so a
                // huge source can't produce something undisplayable.
                val output = ImageEnhancementCache.clampToDisplayLimits(enhanced)
                val bytes = ByteArrayOutputStream().use { out ->
                    output.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, out)
                    out.toByteArray()
                }
                output.recycle()
                // Re-read instead of trusting the snapshot taken minutes ago: `editCover` stores
                // nothing, and reports nothing, for an entry that is neither local nor in the
                // library — so without this check a user who removed the entry mid-run would get a
                // "cover updated" message for work that was silently dropped.
                val target = state.value
                if (target == null || !(target.favorite || target.isLocal())) {
                    failEnhance(context)
                    return@launchIO
                }
                bytes.inputStream().use { target.editCover(Injekt.get(), it, updateManga, coverCache) }
                _enhanceDialog.value = null
                notifyCoverUpdated(context)
                refreshCoverSize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to enhance cover of manga $mangaId" }
                failEnhance(context)
            }
        }
    }

    private suspend fun failEnhance(context: Context) {
        _enhanceDialog.value = null
        withUIContext { context.toast(MKMR.strings.enhance_cover_failed) }
    }

    /** The cover as Coil resolves it today — custom cover, local file or network, whichever applies. */
    private suspend fun loadCoverBitmap(context: Context, manga: Manga): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(manga)
            .size(Size.ORIGINAL)
            // A HARDWARE bitmap lives in GPU memory and can't have its pixels read back, which is
            // exactly what both upscalers need to do. The same guard is why the cover viewer copies
            // its bitmap before handing it to the zoomable view.
            .allowHardware(false)
            // This asks for the full-size image; letting it into the shared memory cache would
            // evict the smaller entries the rest of the UI is actually using.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        return context.imageLoader.execute(request).image
            ?.asDrawable(context.resources)
            ?.getBitmapOrNull()
    }

    private suspend fun upscaleRemote(source: Bitmap): Bitmap? {
        val host = readerPreferences.remoteUpscalerHost().get()
        val port = readerPreferences.remoteUpscalerPort().get()
        return RemoteUpscaler.process(source, host, port) { status ->
            _enhanceDialog.update { current ->
                if (current is EnhanceDialog.InProgress) current.copy(status = status) else current
            }
        }
    }

    /**
     * Runs the configured on-device model, mirroring how the reader derives its parameters.
     *
     * The native call is blocking and reports progress only through a polled counter, so a second
     * coroutine samples it while the model runs. That counter has never been read anywhere else in
     * the app, so the dialog treats it as a bonus: it stays a working spinner if it never moves.
     *
     * Known limitation: the native side keeps one model loaded behind a single mutex, so if the
     * reader's own upscaling queue happens to be draining at this moment, initialising here with a
     * different configuration aborts the page it was working on. Nothing corrupts (the mutex sees to
     * that) and no error surfaces — that page simply isn't enhanced this time round.
     */
    private suspend fun upscaleOnDevice(context: Context, source: Bitmap): Bitmap? = coroutineScope {
        val model = readerPreferences.realCuganModel().get()
        val noise = readerPreferences.realCuganNoiseLevel().get()
        val scale = readerPreferences.realCuganScale().get()
        val perfMode = readerPreferences.realCuganPerformanceMode().get()
        val tileSleepMs = if (perfMode == 1 || perfMode == 2) 15 else 0
        val tileSize = when (perfMode) {
            1 -> 96
            2 -> 64
            else -> 128
        }
        // Nose and Waifu2x Upconv7 only do 2x.
        val effectiveScale = if (model == 3 || model == 5) 2 else scale

        val initialized = when (model) {
            0 -> Waifu2x.initRealCugan(context, noise, effectiveScale, isPro = false, tileSleepMs = tileSleepMs, tileSize = tileSize)
            1 -> Waifu2x.initRealCugan(context, noise, effectiveScale, isPro = true, tileSleepMs = tileSleepMs, tileSize = tileSize)
            2 -> Waifu2x.initRealESRGAN(context, effectiveScale, tileSleepMs = tileSleepMs, tileSize = tileSize)
            3 -> Waifu2x.initNose(context, tileSleepMs = tileSleepMs, tileSize = tileSize)
            4 -> Waifu2x.initWaifu2x(context, noise, effectiveScale, tileSleepMs = tileSleepMs, tileSize = tileSize)
            5 -> Waifu2x.initWaifu2xUpconv7(context, noise, effectiveScale, tileSleepMs = tileSleepMs, tileSize = tileSize)
            else -> Waifu2x.initRealCugan(context, noise, effectiveScale, tileSleepMs = tileSleepMs, tileSize = tileSize)
        }
        if (!initialized) return@coroutineScope null

        val poller = launch {
            while (isActive) {
                delay(PROGRESS_POLL_MS)
                if (Waifu2x.getProgressId() != COVER_PROGRESS_ID) continue
                val percent = Waifu2x.getProgressPercent().takeIf { it in 1..100 } ?: continue
                _enhanceDialog.update { current ->
                    if (current is EnhanceDialog.InProgress) current.copy(percent = percent) else current
                }
            }
        }
        val result = try {
            when (model) {
                0, 1 -> Waifu2x.processRealCugan(source, COVER_PROGRESS_ID)
                2 -> Waifu2x.processRealESRGAN(source, COVER_PROGRESS_ID)
                3 -> Waifu2x.processNose(source, COVER_PROGRESS_ID)
                4, 5 -> Waifu2x.processWaifu2x(source, COVER_PROGRESS_ID)
                else -> Waifu2x.processRealCugan(source, COVER_PROGRESS_ID)
            }
        } finally {
            poller.cancel()
        }
        // Cancelled while the native pass was running: it can't be interrupted, so it ran to
        // completion and handed back a bitmap nobody will ever use. coroutineScope is about to
        // discard this value and throw, so free it here or its native memory leaks.
        if (result != null && !isActive) {
            result.recycle()
            return@coroutineScope null
        }
        result
    }

    private companion object {
        const val COVER_JPEG_QUALITY = 95

        /** Distinguishes this job from the reader's, which tags progress with the page index. */
        const val COVER_PROGRESS_ID = 9_999
        const val PROGRESS_POLL_MS = 150L

        /** Quality used by [recompressCover]; matches [ImageEnhancementCache]'s own WebP saves. */
        const val RECOMPRESS_WEBP_QUALITY = 85

        /** Ceiling for the decoded ARGB bitmap in [decodeCoverBounded] — ~24 MP, well above any sane cover. */
        const val MAX_DECODE_BYTES = 96L * 1024 * 1024
    }
    // MIKO <--

    // MIKO --> Cover disk usage, redownload and recompression
    private val _coverSizeBytes = MutableStateFlow<Long?>(null)

    /** Bytes of whichever file currently backs the cover, or null while unknown/unresolvable. */
    val coverSizeBytes: StateFlow<Long?> = _coverSizeBytes.asStateFlow()

    /**
     * Recomputes [coverSizeBytes] off the main thread. Called by the UI once the cover image
     * finishes loading, and by every action here that can change what's on disk, so the figure
     * never lags behind what the cover viewer is actually displaying.
     */
    fun refreshCoverSize() {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            _coverSizeBytes.value = resolveCoverFileSize(manga)
        }
    }

    /**
     * The file backing today's cover, if one is already materialized on disk: a custom cover
     * takes priority, otherwise the network cover cached for a favorite (which may not exist
     * yet). Non-favorites, and favorites whose cover was never cached to [CoverCache], have no
     * such file — [resolveCoverFileSize] falls back to Coil's own disk cache for those.
     */
    private fun resolveCoverFile(manga: Manga): File? {
        return when {
            manga.hasCustomCover(coverCache) -> coverCache.getCustomCoverFile(manga.id)
            manga.favorite -> coverCache.getCoverFile(manga.thumbnailUrl)?.takeIf { it.exists() }
            else -> null
        }
    }

    private fun resolveCoverFileSize(manga: Manga): Long? {
        resolveCoverFile(manga)?.let { return it.length() }
        return coilDiskCacheCoverSize(manga)
    }

    /**
     * Sizes the cover straight out of Coil's disk cache, using the same key
     * `eu.kanade.tachiyomi.data.coil.MangaKeyer` would derive for this manga — thumbnail URL plus
     * last-modified stamp, no custom-cover branch, since this path is only reached when there's
     * no custom cover. Covers everything [CoverCache] doesn't have a file for yet: non-favorites,
     * and favorites added moments ago.
     */
    private fun coilDiskCacheCoverSize(manga: Manga): Long? {
        val context = Injekt.get<Application>()
        val diskCache = context.imageLoader.diskCache ?: return null
        val key = "${manga.thumbnailUrl};${manga.coverLastModified}"
        return diskCache.openSnapshot(key)?.use { snapshot ->
            diskCache.fileSystem.metadataOrNull(snapshot.data)?.size
        }
    }

    /**
     * Discards the current cover — custom override and any network file cached for it — and
     * forces a refetch from the source. Bumping `coverLastModified` changes the key Coil derives
     * for this manga, so the very next load simply misses Coil's disk cache and goes to network;
     * there's no need to touch Coil's cache directly.
     *
     * No-op unless the manga is a favorite and not local — the UI only shows this action then.
     */
    fun redownloadCover(context: Context) {
        val manga = state.value ?: return
        if (!manga.favorite || manga.isLocal()) return
        screenModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(manga.id)
                coverCache.deleteFromCache(manga, deleteCustomCover = false)
                // The cached network file wins over Coil's key outright: MangaCoverFetcher serves
                // `covers/<hash>` whenever it exists, timestamp or not. So if either delete quietly
                // failed, bumping the stamp would just re-serve the old image under a "cover
                // updated" message — verify the disk before claiming anything.
                val stillThere = coverCache.getCustomCoverFile(manga.id).exists() ||
                    coverCache.getCoverFile(manga.thumbnailUrl)?.exists() == true
                if (stillThere) error("Cover files of manga ${manga.id} could not be deleted")
                updateManga.awaitUpdateCoverLastModified(manga.id)
                notifyCoverUpdated(context)
                refreshCoverSize()
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    /**
     * Re-encodes the current cover file in place as lossy WebP, keeping whichever version turns
     * out smaller. Safe to do in place: [eu.kanade.tachiyomi.data.coil.MangaCoverFetcher] already
     * deletes and rewrites this same path when a cover refreshes, and these files carry no
     * extension to begin with — Coil sniffs the actual content, so swapping formats on disk
     * doesn't break anything.
     *
     * No-op unless the manga is a favorite and not local, with a file [resolveCoverFile] can find.
     */
    fun recompressCover(context: Context) {
        val manga = state.value ?: return
        if (!manga.favorite || manga.isLocal()) return
        screenModelScope.launchIO {
            val file = resolveCoverFile(manga)
            if (file == null) {
                notifyRecompressFailed(context)
                return@launchIO
            }
            val originalSize = file.length()
            val bitmap = decodeCoverBounded(file)
            if (bitmap == null) {
                notifyRecompressFailed(context)
                return@launchIO
            }
            val tempFile = File(file.parent, "${file.name}.tmp")
            try {
                val compressed = FileOutputStream(tempFile).use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, RECOMPRESS_WEBP_QUALITY, out)
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap.compress(Bitmap.CompressFormat.WEBP, RECOMPRESS_WEBP_QUALITY, out)
                    }
                }
                if (!compressed) {
                    notifyRecompressFailed(context)
                    return@launchIO
                }

                val newSize = tempFile.length()
                if (newSize >= originalSize) {
                    notifyRecompressNoGain(context)
                    return@launchIO
                }

                // Nothing locks these files: a library update refreshing this cover in the
                // background rewrites `covers/<hash>` in place while we were decoding it. If the
                // file changed under us, what we decoded may be a torn read — don't replace a
                // fresh cover with a re-encoding of half of the old one.
                if (file.length() != originalSize) {
                    notifyRecompressFailed(context)
                    return@launchIO
                }

                if (!tempFile.renameTo(file)) {
                    notifyRecompressFailed(context)
                    return@launchIO
                }
                updateManga.awaitUpdateCoverLastModified(manga.id)
                refreshCoverSize()
                notifyRecompressResult(context, originalSize, newSize)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to recompress cover of manga $mangaId" }
                notifyRecompressFailed(context)
            } finally {
                bitmap.recycle()
                // A no-op after a successful rename, since the temp path no longer exists there.
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    /**
     * Decodes the cover for re-encoding, halving it as many times as needed to keep the decoded
     * bitmap under [MAX_DECODE_BYTES]. A source cover — or a 4× enhanced one — can be large
     * enough that decoding it whole would eat a good part of the Java heap; and for a file that
     * big, "make it smaller" is exactly what the user pressed the button for, so shrinking it a
     * notch is in the spirit of the action rather than against it. Ordinary covers are far below
     * the limit and come out at their original dimensions.
     */
    private fun decodeCoverBounded(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth.toLong() * bounds.outHeight * 4 / (sampleSize.toLong() * sampleSize) > MAX_DECODE_BYTES) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun notifyRecompressResult(context: Context, originalSize: Long, newSize: Long) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(
                    MKMR.strings.recompress_cover_result,
                    Formatter.formatFileSize(context, originalSize),
                    Formatter.formatFileSize(context, newSize),
                ),
                withDismissAction = true,
            )
        }
    }

    private fun notifyRecompressNoGain(context: Context) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MKMR.strings.recompress_cover_no_gain),
                withDismissAction = true,
            )
        }
    }

    private fun notifyRecompressFailed(context: Context) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MKMR.strings.recompress_cover_failed),
                withDismissAction = true,
            )
        }
    }
    // MIKO <--

    private fun notifyCoverUpdated(context: Context) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.cover_updated),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedCoverUpdate(context: Context, e: Throwable) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.notification_cover_update_failed),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
