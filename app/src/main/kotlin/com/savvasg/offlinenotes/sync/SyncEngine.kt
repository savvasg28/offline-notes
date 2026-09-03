package com.savvasg.offlinenotes.sync

import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.SyncTokenExpiredException
import com.savvasg.offlinenotes.data.remote.SyncTransport
import com.savvasg.offlinenotes.data.remote.dto.TransportException
import javax.inject.Inject

sealed interface SyncOutcome {
    data object Done : SyncOutcome

    /**
     * PENDING ops survived a full round, which can only be live-editing residue: one freeze grabs
     * every currently-PENDING op atomically, so nothing can predate it and survive a successful
     * push+apply. The caller re-enqueues with a delay so a continuously-typing user is not hammered.
     */
    data object PendingResidue : SyncOutcome

    /**
     * Push failed at the transport level, so the whole batch is retried with the same opIds. The ops
     * stay IN_FLIGHT; the next run's [NoteSyncOperations.resetStaleInFlight] frees them for re-freezing.
     */
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

    /**
     * Push before pull, deliberately: draining first shrinks the set of notes the pull has to skip,
     * and routes conflicts through the push response, the only path that carries base content.
     */
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

    /** Token expired: drop the cursor and re-apply a full current-state snapshot. */
    private suspend fun bootstrap() {
        var since: String? = null
        while (true) {
            val page = transport.pullDelta(since) // null (bootstrap) never itself expires
            syncOperations.applyBootstrap(page.notes.map { it.toRemoteNoteChange() }, page.nextSyncToken)
            if (!page.hasMore) return
            since = page.nextSyncToken
        }
    }
}
