package eu.kanade.tachiyomi.ui.favorites

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.bookmark.PageBookmarkPreviewCodec
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import logcat.LogPriority
import okio.buffer
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetPageBookmarkPreviews
import tachiyomi.domain.manga.interactor.SetPageBookmarkPreview
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.max

/**
 * MIKO — C15: state behind the full-screen preview of one bookmarked page (Favorites → Pages →
 * long-press). One instance per opened bookmark, remembered by the host with
 * `rememberScreenModel(tag = "preview-<bookmarkId>") { PageBookmarkPreviewScreenModel(item) }`,
 * the same way `MangaScreen` owns a `MangaCoverScreenModel` for the cover viewer.
 *
 * Owns: resolving the page image (cache → downloaded chapter → source fetch, see [load]),
 * save/share of that image, and the preview's own sub-dialogs (note editor / delete
 * confirmation) — the Pages tab's single `dialog` slot is already taken by `Dialog.Preview`.
 * Does **not** own note/delete writes: the host calls `PageBookmarksScreenModel.updateNote` /
 * `delete`, and the live row (note text, existence) flows back through `PageBookmarksScreenModel.State.items`.
 *
 * @param item the bookmark as it was when the preview opened; only its immutable identity fields
 * (ids, page index, image URL, manga title, chapter name) are read here.
 */
class PageBookmarkPreviewScreenModel(
    val item: PageBookmarkWithRelations,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    // MIKO -->
    private val imageSaver: ImageSaver = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val getPageBookmarkPreviews: GetPageBookmarkPreviews = Injekt.get(),
    private val setPageBookmarkPreview: SetPageBookmarkPreview = Injekt.get(),
    private val pageResolver: PageBookmarkPageResolver = PageBookmarkPageResolver(),
    // MIKO <--
) : StateScreenModel<PageBookmarkPreviewScreenModel.State>(State()) {

    // MIKO -->
    init {
        load()
    }

    /**
     * C16 — true after the user pressed Retry at least once; picks the "determined" pose for the
     * next Loading state instead of "confused". Never reset: once she has insisted, she stays
     * determined for this preview.
     */
    var retriedOnce: Boolean = false
        private set

    private var winkJob: Job? = null

    /**
     * C16 — flashes the wink pose for a moment (after saving/sharing the image or saving the
     * note). Cancel-and-relaunch so rapid repeats just extend the wink.
     */
    fun flashWink() {
        winkJob?.cancel()
        winkJob = screenModelScope.launchIO {
            mutableState.update { it.copy(wink = true) }
            delay(WINK_MILLIS)
            if (isActive) mutableState.update { it.copy(wink = false) }
        }
    }
    // MIKO <--

    /**
     * Resolves the page image, in this order, and publishes [ImageState]:
     * 1. The bookmark's persisted capture blob (`page_bookmark_previews`, C20/D7) — the exact
     *    render the reader showed when it was bookmarked.
     * 2. [PageBookmarkPageResolver.resolveOpenStream]'s cascade: `ChapterCache` → downloaded
     *    chapter → source fetch.
     * Anything that throws → `Error(message)`; [retry] runs [load] again.
     */
    fun load() {
        // MIKO -->
        mutableState.update { it.copy(image = ImageState.Loading) }
        screenModelScope.launchIO {
            val result = try {
                withIOContext { resolveImage() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                ImageState.Error(e.message)
            }
            mutableState.update { it.copy(image = result) }
        }
        // MIKO <--
    }

    fun retry() {
        // MIKO --> C16: the mascot switches from "confused" to "determined" once the user insists
        retriedOnce = true
        // MIKO <--
        load()
    }

    // MIKO -->
    private suspend fun resolveImage(): ImageState {
        val (openStream, fromBlob) = resolveOpenStream() ?: return ImageState.Error(null)
        val isAnimated = openStream().use { stream ->
            ImageUtil.isAnimatedAndSupported(stream.source().buffer())
        }
        val isTall = openStream().use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, bounds)
            bounds.outHeight > bounds.outWidth * 2
        }
        return ImageState.Ready(openStream, isAnimated, isTall, fromBlob)
    }

    /**
     * D7 — the persisted capture blob wins first when present (exactly what the reader showed at
     * bookmark time); otherwise falls back to [PageBookmarkPageResolver]'s cascade. `null` maps to
     * [ImageState.Error] with no message, since there is nothing to retry. An actual failure
     * (network, disk) is left to throw and is caught by [load].
     */
    private suspend fun resolveOpenStream(): Pair<() -> InputStream, Boolean>? {
        val bookmark = item.bookmark
        getPageBookmarkPreviews.await(bookmark.id)?.let { bytes ->
            return { ByteArrayInputStream(bytes) } to true
        }
        val openStream = pageResolver.resolveOpenStream(bookmark) ?: return null
        return openStream to false
    }
    // MIKO <--

    /** `ImageSaver.save(Image.Page(stream, name, Location.Pictures))` + "Picture saved" snackbar/toast. */
    fun saveImage(context: Context) {
        // MIKO -->
        val ready = state.value.image as? ImageState.Ready ?: return
        screenModelScope.launchNonCancellable {
            try {
                val relativePath = if (readerPreferences.folderPerManga().get()) {
                    DiskUtil.buildValidFilename(item.mangaTitle)
                } else {
                    ""
                }
                imageSaver.save(
                    Image.Page(
                        inputStream = ready.openStream,
                        name = buildFilename(),
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                flashWink()
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.picture_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    e.message ?: context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
        // MIKO <--
    }

    /** `ImageSaver.save(Image.Page(stream, name, Location.Cache))` + `uri.toShareIntent(context)`. */
    fun shareImage(context: Context) {
        // MIKO -->
        val ready = state.value.image as? ImageState.Ready ?: return
        screenModelScope.launchNonCancellable {
            try {
                context.cacheImageDir.deleteRecursively()
                val uri = imageSaver.save(
                    Image.Page(
                        inputStream = ready.openStream,
                        name = buildFilename(),
                        location = Location.Cache,
                    ),
                )
                withUIContext { context.startActivity(uri.toShareIntent(context)) }
                flashWink()
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    e.message ?: context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
        // MIKO <--
    }

    // MIKO -->
    private fun buildFilename(): String = DiskUtil.buildValidFilename(
        "${item.mangaTitle} - ${item.chapterName} - ${item.bookmark.pageIndex + 1}",
    )

    /**
     * D15 — re-encodes the currently shown capture at [PageBookmarkPreviewCodec.RECOMPRESS_WEBP_QUALITY]
     * (downscaled to the screen's larger side) and reloads the preview from the smaller blob on a
     * win. Only meaningful while [ImageState.Ready.fromBlob] is true — the caller gates the action
     * on that (D15: nothing to recompress when the image came from the fallback cascade).
     */
    fun recompressCapture(context: Context) {
        val ready = state.value.image as? ImageState.Ready ?: return
        if (!ready.fromBlob) return
        screenModelScope.launchNonCancellable {
            try {
                val bytes = getPageBookmarkPreviews.await(item.bookmark.id) ?: return@launchNonCancellable
                val maxDimension = max(
                    context.resources.displayMetrics.widthPixels,
                    context.resources.displayMetrics.heightPixels,
                )
                val recompressed = PageBookmarkPreviewCodec.recompress(
                    bytes,
                    PageBookmarkPreviewCodec.RECOMPRESS_WEBP_QUALITY,
                    maxDimension,
                )
                if (recompressed == null) {
                    snackbarHostState.showSnackbar(
                        context.stringResource(MKMR.strings.recompress_moment_no_gain),
                        withDismissAction = true,
                    )
                    return@launchNonCancellable
                }
                setPageBookmarkPreview.upsert(item.bookmark.id, recompressed)
                snackbarHostState.showSnackbar(
                    context.stringResource(
                        MKMR.strings.recompress_moment_result,
                        Formatter.formatFileSize(context, bytes.size.toLong()),
                        Formatter.formatFileSize(context, recompressed.size.toLong()),
                    ),
                    withDismissAction = true,
                )
                load()
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MKMR.strings.recompress_moment_failed),
                    withDismissAction = true,
                )
            }
        }
    }
    // MIKO <--

    fun showEditNoteDialog() {
        mutableState.update { it.copy(dialog = Dialog.EditNote) }
    }

    fun showDeleteDialog() {
        mutableState.update { it.copy(dialog = Dialog.ConfirmDelete) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val image: ImageState = ImageState.Loading,
        val dialog: Dialog? = null,
        /** C16 — true while the transient wink reaction is showing (see [flashWink]). */
        val wink: Boolean = false,
    )

    /** How the page image is being obtained — the dialog renders one of these. */
    sealed interface ImageState {
        data object Loading : ImageState

        /**
         * @param openStream a fresh stream of the full-size page image, openable more than once
         * (the viewer, save and share each open their own).
         * @param isAnimated `ImageUtil.isAnimatedAndSupported` of that stream — the viewer picks the
         * animated path with it.
         * @param isTall `height > 2 * width` (decoded bounds only): the viewer opens a tall page
         * fit-to-width and centred on the focus point instead of fitting the whole strip.
         * @param fromBlob true when [openStream] reads the persisted capture blob (D7) rather than
         * the fallback cascade — gates the "compress this capture" action (D15).
         */
        data class Ready(
            val openStream: () -> InputStream,
            val isAnimated: Boolean,
            val isTall: Boolean,
            val fromBlob: Boolean,
        ) : ImageState

        /** [message] is the throwable's message, or null — the UI shows a generic text then. */
        data class Error(val message: String?) : ImageState
    }

    /** The preview's own dialogs, layered over the full-screen preview. */
    sealed interface Dialog {
        data object EditNote : Dialog
        data object ConfirmDelete : Dialog
    }
}

// MIKO --> C16
private const val WINK_MILLIS = 2500L
// MIKO <--
