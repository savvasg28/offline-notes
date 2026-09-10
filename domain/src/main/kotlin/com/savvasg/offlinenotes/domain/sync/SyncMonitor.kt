package com.savvasg.offlinenotes.domain.sync

interface SyncMonitor {
    fun onTransportFailure(cause: TransportException)

    fun onSyncTokenExpired(cause: SyncTokenExpiredException)

    fun onSyncFinished(outcome: SyncOutcome)
}
