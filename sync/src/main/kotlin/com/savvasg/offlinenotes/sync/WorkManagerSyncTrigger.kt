package com.savvasg.offlinenotes.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncTrigger @Inject constructor(private val workManager: WorkManager) : SyncTrigger {

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(NETWORK_CONSTRAINT)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun rescheduleForRemainingWork() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(SYNC_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(NETWORK_CONSTRAINT)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun ensurePeriodicSync() {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(NETWORK_CONSTRAINT)
                .build()
        workManager.enqueueUniquePeriodicWork(
            SYNC_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val SYNC_WORK_NAME = "offline-notes-sync"
        const val SYNC_PERIODIC_WORK_NAME = "offline-notes-sync-periodic"

        const val SYNC_DELAY_MILLIS = 30_000L

        private const val PERIODIC_INTERVAL_HOURS = 6L

        private val NETWORK_CONSTRAINT = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
