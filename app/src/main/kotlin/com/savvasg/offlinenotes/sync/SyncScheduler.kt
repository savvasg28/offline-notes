package com.savvasg.offlinenotes.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The trigger layer (§6). Every trigger funnels into the same unique work — [SyncEngine.sync]
 * doesn't care why it was invoked — so this class only decides *when*.
 */
@Singleton
class SyncScheduler @Inject constructor(private val workManager: WorkManager) {

    /**
     * First edit → enqueue with initial delay [SYNC_DELAY_MILLIS] (§6's W, the bounded
     * data-loss window). `KEEP` makes every edit after the first a no-op: the delay counts from
     * the first edit, not the last, so continuous typing can't push it out indefinitely.
     */
    fun scheduleDebouncedSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Editor close / app background / app foreground (§6): expedited, no delay, flushing the
     * session tail or refreshing on resume. `REPLACE` overrides any still-waiting debounced
     * request from [scheduleDebouncedSync] — no reason to wait out W once this fires.
     */
    fun expediteSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(NETWORK_CONSTRAINT)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Residue reschedule (§6 step 5), called by [SyncWorker] itself while its own run is still
     * the unique work occupying [SYNC_WORK_NAME]. `KEEP` (used by [scheduleDebouncedSync]) would
     * see that "existing" run and silently drop this — WorkManager treats RUNNING as unfinished
     * work, so a KEEP enqueue from inside it is a no-op. `APPEND_OR_REPLACE` instead chains this
     * request to run right after the current one finishes.
     */
    fun rescheduleAfterResidue() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Long-interval backstop only (§6) — safe to call on every app start; `KEEP` no-ops after the first. */
    fun schedulePeriodicBackstop() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val SYNC_WORK_NAME = "offline-notes-sync"
        const val SYNC_PERIODIC_WORK_NAME = "offline-notes-sync-periodic"

        /** W (§6): the maximum span of typing that exists on only one device. Name it in code. */
        const val SYNC_DELAY_MILLIS = 30_000L

        private const val PERIODIC_INTERVAL_HOURS = 6L

        private val NETWORK_CONSTRAINT = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
