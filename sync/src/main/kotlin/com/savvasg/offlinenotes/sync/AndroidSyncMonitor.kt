package com.savvasg.offlinenotes.sync

import android.util.Log
import com.savvasg.offlinenotes.domain.sync.SyncMonitor
import com.savvasg.offlinenotes.domain.sync.SyncOutcome
import com.savvasg.offlinenotes.domain.sync.SyncTokenExpiredException
import com.savvasg.offlinenotes.domain.sync.TransportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSyncMonitor @Inject constructor() : SyncMonitor {

    override fun onTransportFailure(cause: TransportException) {
        Log.w(TAG, "sync push/pull failed; the batch will be retried with the same opIds", cause)
    }

    override fun onSyncTokenExpired(cause: SyncTokenExpiredException) {
        Log.i(TAG, "sync token rejected by the server; re-bootstrapping from a full snapshot", cause)
    }

    override fun onSyncFinished(outcome: SyncOutcome) {
        when (outcome) {
            SyncOutcome.Done -> Log.d(TAG, "sync finished, outbox drained")
            SyncOutcome.MoreWorkPending -> Log.d(TAG, "sync finished with residue; another pass scheduled")
            SyncOutcome.TransportFailure -> Log.w(TAG, "sync did not complete; WorkManager will retry")
        }
    }

    private companion object {
        const val TAG = "OfflineNotesSync"
    }
}
