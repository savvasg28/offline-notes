package com.savvasg.offlinenotes.sync

import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import com.savvasg.offlinenotes.sync.transport.OpResult
import com.savvasg.offlinenotes.sync.transport.SyncTokenExpiredException
import com.savvasg.offlinenotes.sync.transport.SyncTransport
import com.savvasg.offlinenotes.sync.transport.TransportException
import javax.inject.Inject

/** What the caller (the WorkManager worker) should do next, per §6 step 5. */
sealed interface SyncOutcome {
    /** Nothing left to do — no PENDING ops survived the round. */
    data object Done : SyncOutcome

    /**
     * PENDING ops exist after a full round. Under this engine's design that can only be
     * live-editing residue — a single freeze (§5) atomically grabs every currently-PENDING op,
     * so nothing can "predate" it and survive a successful push+apply; the only way a PENDING
     * row exists afterward is a fresh edit landing *during* that round. Caller re-enqueues with
     * delay W (§6), not immediately, so a continuously-typing user isn't hammered.
     */
    data object PendingResidue : SyncOutcome

    /** Push failed at the transport level (§7: retry the whole batch, same opIds). The ops stay
     * IN_FLIGHT; the next run's step 0 ([NoteSyncOperations.resetStaleInFlight]) frees them for
     * re-freezing. Caller maps this to `Result.retry()`. */
    data object TransportFailure : SyncOutcome
}

/**
 * The sync engine (§6): one full pipeline run — crash recovery, freeze, push, apply, pull, loop
 * check. Every trigger (§6) funnels into [sync]; only *when* it's called differs.
 */
class SyncEngine @Inject constructor(
    private val syncOperations: NoteSyncOperations,
    private val transport: SyncTransport,
) {
    suspend fun sync(): SyncOutcome {
        syncOperations.resetStaleInFlight() // step 0: crash recovery

        val frozen = syncOperations.freezeAndDrain() // step 1: freeze txn, the linearization point
        if (frozen.isNotEmpty()) {
            val results = try {
                transport.pushOps(frozen.map { it.toOutgoingOp() }) // step 2
            } catch (e: TransportException) {
                return SyncOutcome.TransportFailure
            }
            applyResults(results) // step 3: apply txn, one per op
        }

        pullDelta() // step 4

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
     * Push before pull, deliberately (§6): draining first shrinks the set of notes the pull must
     * skip, and routes conflicts through the push response — the only path carrying base content.
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

    /** §7: token expired → clear it and re-bootstrap from a full current-state snapshot. */
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
