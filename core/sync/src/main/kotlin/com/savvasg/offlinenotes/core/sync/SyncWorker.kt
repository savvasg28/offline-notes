package com.savvasg.offlinenotes.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncEngine.sync()) {
        SyncOutcome.Done -> Result.success()
        SyncOutcome.PendingResidue -> {
            syncScheduler.rescheduleAfterResidue()
            Result.success()
        }
        SyncOutcome.TransportFailure -> Result.retry()
    }
}
