package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteSyncOperations
import com.savvasg.offlinenotes.core.data.OpAcknowledgement
import com.savvasg.offlinenotes.core.network.dto.OpResult
import com.savvasg.offlinenotes.core.network.dto.SyncTokenExpiredException
import com.savvasg.offlinenotes.core.network.SyncTransport
import com.savvasg.offlinenotes.core.network.dto.TransportException
import javax.inject.Inject

sealed interface SyncOutcome {
    data object Done : SyncOutcome

    data object PendingResidue : SyncOutcome

    data object TransportFailure : SyncOutcome
}

class SyncEngine @Inject constructor(
    private val syncOperations: NoteSyncOperations,
    private val transport: SyncTransport,
) {
    suspend fun sync(): SyncOutcome {
        syncOperations.resetStaleInFlight()

        val frozen = syncOperations.freezeAndDrain()
        if (frozen.isNotEmpty()) {
            val results = try {
                transport.pushOps(frozen.map { it.toOutgoingOp() })
            } catch (e: TransportException) {
                return SyncOutcome.TransportFailure
            }
            applyResults(results)
        }

        pullDelta()

        return if (syncOperations.hasPendingOps()) SyncOutcome.PendingResidue else SyncOutcome.Done
    }

    private suspend fun applyResults(results: List<OpResult>) {
        for (result in results) {
            when (result) {
                is OpResult.Applied -> syncOperations.applyResult(result.opId, OpAcknowledgement.Applied(result.newVersion))
                is OpResult.Rejected -> syncOperations.applyResult(result.opId, OpAcknowledgement.Rejected)
                is OpResult.Conflict -> syncOperations.stageConflict(result.opId, result.toConflictPayload())
            }
        }
    }

    private suspend fun pullDelta() {
        var since = syncOperations.currentSyncToken()
        while (true) {
            val page = try {
                transport.pullDelta(since)
            } catch (e: SyncTokenExpiredException) {
                bootstrap()
                return
            }
            syncOperations.applyDelta(page.notes.map { it.toRemoteNoteChange() }, page.nextSyncToken)
            if (!page.hasMore) return
            since = page.nextSyncToken
        }
    }

    private suspend fun bootstrap() {
        var since: String? = null
        while (true) {
            val page = transport.pullDelta(since)
            syncOperations.applyBootstrap(page.notes.map { it.toRemoteNoteChange() }, page.nextSyncToken)
            if (!page.hasMore) return
            since = page.nextSyncToken
        }
    }
}
