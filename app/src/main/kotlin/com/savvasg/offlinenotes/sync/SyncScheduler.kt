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

@Singleton
class SyncScheduler @Inject constructor(private val workManager: WorkManager) {

    /**
     * `KEEP` makes every edit after the first a no-op, so the delay counts from the first edit
     * rather than the last and continuous typing cannot push it out indefinitely.
     */
    fun scheduleDebouncedSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Flushes the session tail or refreshes on resume. `REPLACE` overrides any still-waiting
     * request from [scheduleDebouncedSync] — no reason to wait the delay out once this fires.
     */
    fun expediteSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(NETWORK_CONSTRAINT)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Called by [SyncWorker] from inside its own run, while that run still occupies
     * [SYNC_WORK_NAME]. `KEEP` would see the RUNNING work as existing and silently drop this;
     * `APPEND_OR_REPLACE` chains the request to start once the current run finishes.
     */
    fun rescheduleAfterResidue() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Safe to call on every app start; `KEEP` no-ops after the first. */
    fun schedulePeriodicBackstop() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val SYNC_WORK_NAME = "offline-notes-sync"
        const val SYNC_PERIODIC_WORK_NAME = "offline-notes-sync-periodic"

        /** The maximum span of typing that exists on only one device. */
        const val SYNC_DELAY_MILLIS = 30_000L

        private const val PERIODIC_INTERVAL_HOURS = 6L

        private val NETWORK_CONSTRAINT = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
