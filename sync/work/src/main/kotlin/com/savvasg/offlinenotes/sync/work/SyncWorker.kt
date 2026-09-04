package com.savvasg.offlinenotes.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.savvasg.offlinenotes.core.sync.SyncEngine
import com.savvasg.offlinenotes.core.sync.SyncOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val syncTrigger: WorkManagerSyncTrigger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncEngine.sync()) {
        SyncOutcome.Done -> Result.success()
        SyncOutcome.PendingResidue -> {
            syncTrigger.rescheduleAfterResidue()
            Result.success()
        }
        SyncOutcome.TransportFailure -> Result.retry()
    }
}
