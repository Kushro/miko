package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.chargingStateFlow
import eu.kanade.tachiyomi.util.system.isCharging
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.miko.MKMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 */
class DownloadJob(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val downloadManager: DownloadManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        // MIKO -->
        val force = inputData.getBoolean(FORCE_KEY, false)

        val initialBlocker = evaluateConditions(currentConditions(), force)
        if (initialBlocker != null) {
            stopWithReason(initialBlocker)
            return if (rearmIfNeeded(initialBlocker)) Result.success() else Result.failure()
        }

        // Shared between the watcher coroutine and the polling loop below (Dispatchers.Default is
        // multi-threaded), hence an atomic instead of a captured var.
        val blocker = AtomicReference<DownloadBlocker?>(null)
        var active = downloadManager.downloaderStart()

        if (!active) {
            return Result.failure()
        }

        setForegroundSafely()

        coroutineScope {
            val watcher = blockerFlow(force)
                .distinctUntilChanged()
                .onEach { current ->
                    blocker.set(current)
                    if (current != null) {
                        stopWithReason(current)
                    }
                }
                .launchIn(this)

            // Keep the worker running while there's something to download and nothing blocking it.
            while (active) {
                delay(ACTIVE_POLL_INTERVAL_MS)
                active = !isStopped && downloadManager.isRunning && blocker.get() == null
            }

            watcher.cancel()
        }

        rearmIfNeeded(blocker.get())

        return Result.success()
        // MIKO <--
    }

    // MIKO -->
    private fun currentConditions(): DownloadConditions {
        val networkState = applicationContext.activeNetworkState()
        return DownloadConditions(
            isOnline = networkState.isOnline,
            isWifi = networkState.isWifi,
            isCharging = applicationContext.isCharging(),
            isOffPeak = !downloadPreferences.downloadOffPeakEnabled().get() || isOffPeakNow(),
        )
    }

    private fun isOffPeakNow(): Boolean {
        return isWithinWindow(
            nowMinutesOfDay(),
            downloadPreferences.downloadOffPeakStartMinutes().get(),
            downloadPreferences.downloadOffPeakEndMinutes().get(),
        )
    }

    private fun evaluateConditions(conditions: DownloadConditions, force: Boolean): DownloadBlocker? {
        return checkDownloadConditions(
            conditions = conditions,
            requireWifi = downloadPreferences.downloadOnlyOverWifi().get(),
            requireCharging = downloadPreferences.downloadOnlyWhenCharging().get(),
            requireOffPeak = downloadPreferences.downloadOffPeakEnabled().get(),
            force = force,
        )
    }

    /**
     * Folds every input the restrictions depend on — network, charger, off-peak window and the
     * preferences themselves — into the blocker currently in effect (`null` = downloads may run).
     */
    private fun blockerFlow(force: Boolean): Flow<DownloadBlocker?> {
        return combine(
            applicationContext.networkStateFlow()
                .onStart { emit(applicationContext.activeNetworkState()) },
            applicationContext.chargingStateFlow(),
            offPeakFlow(),
            downloadPreferences.downloadOnlyOverWifi().changes(),
            downloadPreferences.downloadOnlyWhenCharging().changes(),
        ) { networkState, charging, offPeak, requireWifi, requireCharging ->
            checkDownloadConditions(
                conditions = DownloadConditions(
                    isOnline = networkState.isOnline,
                    isWifi = networkState.isWifi,
                    isCharging = charging,
                    isOffPeak = offPeak,
                ),
                requireWifi = requireWifi,
                requireCharging = requireCharging,
                requireOffPeak = downloadPreferences.downloadOffPeakEnabled().get(),
                force = force,
            )
        }
    }

    /**
     * Emits whether we're inside the off-peak window, re-evaluating on every window boundary (and
     * at least every [WINDOW_MIN_RECHECK_MS]) and restarting whenever the window preferences change.
     * Emits a constant `true` while the window is disabled.
     */
    private fun offPeakFlow(): Flow<Boolean> {
        return combine(
            downloadPreferences.downloadOffPeakEnabled().changes(),
            downloadPreferences.downloadOffPeakStartMinutes().changes(),
            downloadPreferences.downloadOffPeakEndMinutes().changes(),
        ) { enabled, start, end -> Triple(enabled, start, end) }
            .flatMapLatest { (enabled, start, end) ->
                flow {
                    if (!enabled) {
                        emit(true)
                        return@flow
                    }
                    while (true) {
                        val now = nowMinutesOfDay()
                        val inside = isWithinWindow(now, start, end)
                        emit(inside)
                        val minutes = if (inside) {
                            minutesUntilWindowEnd(now, start, end)
                        } else {
                            minutesUntilWindowStart(now, start, end)
                        }
                        delay(max(minutes * MILLIS_PER_MINUTE, WINDOW_MIN_RECHECK_MS))
                    }
                }
            }
            .distinctUntilChanged()
    }

    private fun stopWithReason(blocker: DownloadBlocker) {
        val reason = when (blocker) {
            DownloadBlocker.NO_NETWORK -> applicationContext.getString(R.string.download_notifier_no_network)
            DownloadBlocker.NO_WIFI -> applicationContext.getString(R.string.download_notifier_text_only_wifi)
            DownloadBlocker.NOT_CHARGING ->
                applicationContext.stringResource(MKMR.strings.download_notifier_text_only_charging)
            DownloadBlocker.OFF_PEAK -> applicationContext.stringResource(
                MKMR.strings.download_notifier_text_off_peak,
                formatMinutes(downloadPreferences.downloadOffPeakStartMinutes().get()),
                formatMinutes(downloadPreferences.downloadOffPeakEndMinutes().get()),
            )
        }
        downloadManager.downloaderStop(reason)
    }

    /**
     * Schedules the automatic resumption after a charging/off-peak stop. The request is appended to
     * this same unique work so enqueueing it from inside [doWork] doesn't cancel the running worker
     * (a [ExistingWorkPolicy.REPLACE] here would), and it carries the charging constraint and/or the
     * delay until the window opens, courtesy of [start].
     *
     * Manual pauses never reach this: they stop the downloader without a reason, which cancels the
     * unique work (and with it any pending re-arm).
     */
    private fun rearmIfNeeded(blocker: DownloadBlocker?): Boolean {
        if (blocker != DownloadBlocker.NOT_CHARGING && blocker != DownloadBlocker.OFF_PEAK) return false
        if (downloadManager.queueState.value.isEmpty()) return false
        start(applicationContext, force = false, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)
        return true
    }
    // MIKO <--

    companion object {
        private const val TAG = "Downloader"

        // MIKO -->
        private const val FORCE_KEY = "force"
        private const val ACTIVE_POLL_INTERVAL_MS = 500L
        private const val WINDOW_MIN_RECHECK_MS = 30_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        // MIKO <--

        fun start(
            context: Context,
            // MIKO -->
            force: Boolean = false,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
            // MIKO <--
        ) {
            val request = OneTimeWorkRequestBuilder<DownloadJob>()
                .addTag(TAG)
                // MIKO -->
                .setInputData(workDataOf(FORCE_KEY to force))
                .apply {
                    if (force) return@apply

                    val downloadPreferences = Injekt.get<DownloadPreferences>()
                    if (downloadPreferences.downloadOnlyWhenCharging().get()) {
                        setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                    }
                    if (downloadPreferences.downloadOffPeakEnabled().get()) {
                        val minutes = minutesUntilWindowStart(
                            nowMinutesOfDay(),
                            downloadPreferences.downloadOffPeakStartMinutes().get(),
                            downloadPreferences.downloadOffPeakEndMinutes().get(),
                        )
                        if (minutes > 0) {
                            setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                        }
                    }
                }
                // MIKO <--
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, /* MIKO --> */ policy /* MIKO <-- */, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
