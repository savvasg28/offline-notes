package com.savvasg.offlinenotes.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The single WorkManager unique work driving [SyncEngine.sync] (§6) — every trigger
 * ([SyncScheduler]) ultimately enqueues this. `ExistingWorkPolicy.KEEP`/`REPLACE` (in
 * [SyncScheduler]) is what actually implements "subsequent edits are no-ops" — this worker just
 * runs one pipeline pass and reports the outcome.
 */
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
            // Live-editing residue: re-enqueue with delay W rather than retrying this attempt
            // immediately, so a continuously-typing user isn't hammered (§6 step 5).
            syncScheduler.rescheduleAfterResidue()
            Result.success()
        }
        SyncOutcome.TransportFailure -> Result.retry()
    }
}
