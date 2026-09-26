package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.PointF
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.app.ActivityCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * Implementation of a [Viewer] to display pages with a [RecyclerView].
 */
class WebtoonViewer(
    val activity: ReaderActivity,
    val isContinuous: Boolean = true,
    private val tapByPage: Boolean = false,
    // KMK -->
    @param:ColorInt private val seedColor: Int? = null,
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    // KMK <--
) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * Recycler view used by this viewer.
     */
    val recycler = WebtoonRecyclerView(activity)

    /**
     * Frame containing the recycler view.
     */
    private val frame = WebtoonFrame(activity)

    /**
     * Distance to scroll when the user taps on one side of the recycler view.
     */
    private val scrollDistance = activity.resources.displayMetrics.heightPixels * 3 / 4

    /**
     * Layout manager of the recycler view.
     */
    private val layoutManager = WebtoonLayoutManager(activity, scrollDistance)

    /**
     * Configuration used by this viewer, like allow taps, or crop image borders.
     */
    val config = WebtoonConfig(scope)

    /**
     * Adapter of the recycler view.
     */
    private val adapter = WebtoonAdapter(
        this,
        // KMK -->
        seedColor = seedColor,
        // KMK <--
    )

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    /* [EXH] private */
    var currentPage: Any? = null

    // MIKO -->
    /**
     * Page whose bookmarked scroll position is still waiting to be restored, with the fraction to
     * restore. Cleared once applied, or as soon as the user scrolls themselves — a slow-decoding
     * page must never yank someone who already started reading.
     */
    private var pendingScroll: Pair<ReaderPage, Float>? = null
    // MIKO <--

    private val threshold: Int =
        // KMK -->
        readerPreferences
            // KMK <--
            .readerHideThreshold()
            .get()
            .threshold

    init {
        recycler.setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        recycler.isVisible = false // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    onScrolled()

                    // MIKO --> The user took over by dragging; the tap and volume-key paths cancel
                    // in scrollUp()/scrollDown(), which never reach this state.
                    if (pendingScroll != null && recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        cancelPendingScroll()
                    }
                    // MIKO <--

                    if ((dy > threshold || dy < -threshold) && activity.viewModel.state.value.menuVisible) {
                        activity.hideMenu()
                    }

                    if (dy < 0) {
                        val firstIndex = layoutManager.findFirstVisibleItemPosition()
                        val firstItem = adapter.items.getOrNull(firstIndex)
                        if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                            activity.requestPreloadChapter(firstItem.to)
                        }
                    }

                    val lastIndex = layoutManager.findLastEndVisibleItemPosition()
                    val lastItem = adapter.items.getOrNull(lastIndex)
                    if (lastItem is ChapterTransition.Next && lastItem.to == null) {
                        activity.showMenu()
                    }
                }
            },
        )
        recycler.tapListener = { event ->
            val viewPosition = IntArray(2)
            recycler.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            recycler.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / recycler.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / recycler.originalHeight,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
                NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
            }
        }
        recycler.longTapListener = f@{ event ->
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val child = recycler.findChildViewUnder(event.x, event.y)
                if (child != null) {
                    val position = recycler.getChildAdapterPosition(child)
                    val item = adapter.items.getOrNull(position)
                    if (item is ReaderPage) {
                        // MIKO --> where inside the page the user pressed, so the bookmark's
                        // thumbnail/preview can centre on that point later.
                        val focusFraction = child.height.takeIf { it > 0 }
                            ?.let { ((event.y - child.top) / it.toFloat()).coerceIn(0f, 1f) }
                        activity.onPageLongTap(item, focusFraction = focusFraction)
                        // MIKO <--
                        return@f true
                    }
                }
            }
            false
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.themeChangedListener = {
            ActivityCompat.recreate(activity)
        }

        config.doubleTapZoomChangedListener = {
            frame.doubleTapZoom = it
        }

        // KMK -->
        config.pinchToZoomChangedListener = {
            frame.pinchToZoom = it
        }

        config.webtoonScaleTypeChangedListener = f@{ scaleType ->
            if (!isContinuous && !readerPreferences.longStripGapSmartScale().get()) return@f

            recycler.post {
                recycler.doOnLayout doOnLayout@{
                    val currentWidth = recycler.width
                    val currentHeight = recycler.originalHeight
                    if (currentWidth <= 0 || currentHeight <= 0) return@doOnLayout

                    if (scaleType == ReaderPreferences.WebtoonScaleType.FIT) {
                        recycler.scaleTo(1f)
                        return@doOnLayout
                    }

                    val desiredRatio = scaleType.ratio
                    val screenRatio = currentWidth.toFloat() / currentHeight
                    val desiredWidth = currentHeight * desiredRatio
                    val desiredScale = desiredWidth / currentWidth

                    if (screenRatio > desiredRatio) {
                        recycler.scaleTo(desiredScale)
                    } else {
                        recycler.scaleTo(1f)
                    }
                }
            }
        }
        // KMK <--

        config.zoomPropertyChangedListener = {
            frame.zoomOutDisabled = it
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
        val nextChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as? ReaderPage)?.chapter

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            nextChapter -> true
            else -> false
        }
    }

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return frame
    }

    /**
     * Destroys this viewer. Called when leaving the reader or swapping viewers.
     */
    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    /**
     * Called from the RecyclerView listener when a [page] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val nextItem = adapter.items.getOrNull(adapter.items.size - 1)
            val transitionChapter = (nextItem as? ChapterTransition.Next)?.to ?: (nextItem as?ReaderPage)?.chapter
            if (transitionChapter != null) {
                logcat { "Requesting to preload chapter ${transitionChapter.chapter.chapter_number}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called from the RecyclerView listener when a [transition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active.
     */
    override fun setChapters(chapters: ViewerChapters) {
        val forceTransition = config.alwaysShowChapterTransition || currentPage is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        if (recycler.isGone) {
            logcat { "Recycler first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(
                pages[min(chapters.currChapter.requestedPage, pages.lastIndex)],
                // MIKO --> set when the reader was opened from a page bookmark that recorded how
                // far into the page it was; consumed so it only applies to this first positioning.
                scrollFraction = activity.viewModel.consumePendingScrollFraction(),
                // MIKO <--
            )
            recycler.isVisible = true
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) = moveToPage(page, scrollFraction = null)

    // MIKO -->
    /**
     * Moves to [page] and, when [scrollFraction] is given, restores how far into that page the
     * reader was when a page bookmark was taken (see `PageBookmark.scrollFraction`).
     *
     * The offset can't be applied here: a page's item is `WRAP_CONTENT` and only reaches its real
     * height once the image has been decoded, so at this point it is still the placeholder's. The
     * fraction is parked in [pendingScroll] and applied from [onPageImageDecoded].
     */
    fun moveToPage(page: ReaderPage, scrollFraction: Float?) {
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            pendingScroll = scrollFraction
                ?.takeIf { it > 0f }
                ?.let { page to it.coerceIn(0f, 1f) }
            layoutManager.scrollToPositionWithOffset(position, 0)
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                onScrolled(pos = position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    /**
     * How far into [page] the viewer currently is, as a fraction (0..1) of that page's height, or
     * null when the page isn't laid out (so nothing can be measured).
     *
     * Read from layout coordinates, which the pinch-to-zoom `scaleX`/`scaleY` applied to the whole
     * recycler does not affect — so the value means the same thing zoomed in or out.
     */
    fun getScrollFraction(page: ReaderPage): Float? {
        val position = adapter.items.indexOf(page).takeIf { it != -1 } ?: return null
        val child = layoutManager.findViewByPosition(position) ?: return null
        val height = child.height.takeIf { it > 0 } ?: return null
        // child.top is the item's top edge relative to the viewport: it goes negative as the page
        // scrolls past the top. Still positive means the page hasn't reached the top yet.
        val scrolledPast = -child.top
        return if (scrolledPast <= 0) 0f else (scrolledPast.toFloat() / height).coerceIn(0f, 1f)
    }

    /**
     * Where the centre of the viewport currently falls inside [page], as a fraction (0..1) of that
     * page's height, or null when the page isn't laid out. Used by the reader top bar's bookmark
     * toggle, which has no touch point of its own to record (unlike the long-press sheet).
     */
    fun getViewportCentreFraction(page: ReaderPage): Float? {
        val position = adapter.items.indexOf(page).takeIf { it != -1 } ?: return null
        val child = layoutManager.findViewByPosition(position) ?: return null
        val height = child.height.takeIf { it > 0 } ?: return null
        return ((recycler.originalHeight / 2f - child.top) / height).coerceIn(0f, 1f)
    }

    /**
     * Bounds of [page]'s laid-out item, in window coordinates, intersected with the recycler's own
     * visible viewport — so a page only partially scrolled into view yields just the on-screen
     * slice. Read via [android.view.View.getLocationInWindow], which (unlike the layout coordinates
     * [getScrollFraction] uses) follows the pinch-to-zoom `scaleX`/`scaleY`/pan the recycler may
     * carry — but only for the ORIGIN: view sizes stay in layout units, so both extents are scaled
     * by the recycler's factors manually. That keeps the rect matching what
     * [android.view.PixelCopy] actually sees on screen even while zoomed — the point of the C20
     * persistent bookmark capture this feeds (see `PageCapture.capturePageVisible`).
     * Returns null when the page isn't laid out or the intersection with the viewport is empty.
     */
    fun getPageVisibleRect(page: ReaderPage): Rect? {
        val position = adapter.items.indexOf(page).takeIf { it != -1 } ?: return null
        val child = layoutManager.findViewByPosition(position) ?: return null
        if (child.width <= 0 || child.height <= 0) return null

        val scaleX = recycler.scaleX
        val scaleY = recycler.scaleY
        val childLocation = IntArray(2)
        child.getLocationInWindow(childLocation)
        val childRect = Rect(
            childLocation[0],
            childLocation[1],
            childLocation[0] + (child.width * scaleX).roundToInt(),
            childLocation[1] + (child.height * scaleY).roundToInt(),
        )

        val recyclerLocation = IntArray(2)
        recycler.getLocationInWindow(recyclerLocation)
        val recyclerRect = Rect(
            recyclerLocation[0],
            recyclerLocation[1],
            recyclerLocation[0] + (recycler.width * scaleX).roundToInt(),
            recyclerLocation[1] + (recycler.height * scaleY).roundToInt(),
        )

        return childRect.takeIf { it.intersect(recyclerRect) }
    }

    /**
     * Called by [WebtoonPageHolder] once a page's image finished decoding — the first moment its
     * item can reach a real height — so a bookmark's scroll position can finally be restored.
     */
    fun onPageImageDecoded(page: ReaderPage) {
        if (pendingScroll?.first != page) return
        val position = adapter.items.indexOf(page).takeIf { it != -1 } ?: return
        val child = layoutManager.findViewByPosition(position) ?: return
        // Decoding settles the image, but the item still has to re-measure from the placeholder's
        // height to the image's. Waiting on the *item* is what gives that: doOnLayout runs straight
        // away unless a layout is pending on that view — and decoding just requested one. Waiting on
        // the recycler instead would be a no-op, since it was laid out long before any image
        // decoded.
        child.doOnLayout doOnLayout@{
            val (target, fraction) = pendingScroll ?: return@doOnLayout
            if (target != page) return@doOnLayout
            // This page had its chance: consume the request whatever happens below, so a stale
            // entry can't fire later against a chapter the reader has since moved on from.
            pendingScroll = null
            val currentPosition = adapter.items.indexOf(page).takeIf { it != -1 } ?: return@doOnLayout
            val height = layoutManager.findViewByPosition(currentPosition)?.height?.takeIf { it > 0 }
                ?: return@doOnLayout
            layoutManager.scrollToPositionWithOffset(currentPosition, -(height * fraction).toInt())
            onScrolled(pos = currentPosition)
        }
    }

    /**
     * Forgets a bookmark scroll position that hasn't been applied yet, because the user has moved
     * on. Every way of moving through the chapter has to call this, not just dragging: tapping the
     * edges and the volume keys scroll programmatically, which never reports
     * [RecyclerView.SCROLL_STATE_DRAGGING], and a page that decodes slowly would otherwise yank the
     * reader backwards long after they had started reading somewhere else.
     */
    private fun cancelPendingScroll() {
        pendingScroll = null
    }
    // MIKO <--

    fun onScrolled(pos: Int? = null) {
        val position = pos ?: layoutManager.findLastEndVisibleItemPosition()
        val item = adapter.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Scrolls up by [scrollDistance].
     */
    private fun scrollUp() {
        // MIKO -->
        cancelPendingScroll()
        // MIKO <--
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /**
     * Scrolls one screen over a period of time
     */
    fun linearScroll(duration: Duration) {
        // MIKO -->
        cancelPendingScroll()
        // MIKO <--
        recycler.smoothScrollBy(
            0,
            activity.resources.displayMetrics.heightPixels,
            LinearInterpolator(),
            duration.inWholeMilliseconds.toInt(),
        )
    }

    /**
     * Scrolls down by [scrollDistance].
     */
    /* [EXH] private */
    fun scrollDown() {
        // MIKO -->
        cancelPendingScroll()
        // MIKO <--
        // SY -->
        if (!isContinuous && tapByPage) {
            val currentPage = currentPage
            if (currentPage is ReaderPage) {
                val position = adapter.items.indexOf(currentPage)
                val nextItem = adapter.items.getOrNull(position + 1)
                if (nextItem is ReaderPage) {
                    if (config.usePageTransitions) {
                        recycler.smoothScrollToPosition(position + 1)
                    } else {
                        recycler.scrollToPosition(position + 1)
                    }
                    return
                }
            }
        }
        scrollDownBy()
    }

    private fun scrollDownBy() {
        // SY <--
        if (config.usePageTransitions) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollDown() else scrollUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) scrollUp() else scrollDown()
                }
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    // KMK --> Recreate visible page views (re-runs the enhancement pipeline) for mode/param changes.
    override fun reloadPages() {
        refreshAdapter()
    }
    // KMK <--

    /**
     * Notifies adapter of changes around the current page to trigger a relayout in the recycler.
     * Used when an image configuration is changed.
     */
    private fun refreshAdapter() {
        val position = layoutManager.findLastEndVisibleItemPosition()
        adapter.refresh()
        adapter.notifyItemRangeChanged(
            max(0, position - 3),
            min(position + 3, adapter.itemCount - 1),
        )
    }
}

// Double the cache size to reduce rebinds/recycles incurred by the extra layout space on scroll direction changes
private const val RECYCLER_VIEW_CACHE_SIZE = 4
