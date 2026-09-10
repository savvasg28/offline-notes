package com.savvasg.offlinenotes.domain.sync

import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import javax.inject.Inject

sealed interface SyncOutcome {
    data object Done : SyncOutcome

    data object MoreWorkPending : SyncOutcome

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
            val outcomes = try {
                transport.push(frozen)
            } catch (ignored: TransportException) {
                return SyncOutcome.TransportFailure
            }
            applyOutcomes(outcomes)
        }

        drainRemoteChanges()

        return if (syncOperations.hasPendingOps()) SyncOutcome.MoreWorkPending else SyncOutcome.Done
    }

    private suspend fun applyOutcomes(outcomes: List<OpOutcome>) {
        for (outcome in outcomes) {
            when (outcome) {
                is OpOutcome.Applied -> syncOperations.applyResult(outcome.opId, OpAcknowledgement.Applied(outcome.newVersion))
                is OpOutcome.Rejected -> syncOperations.applyResult(outcome.opId, OpAcknowledgement.Rejected)
                is OpOutcome.Conflicted -> syncOperations.stageConflict(outcome.opId, outcome.conflict)
            }
        }
    }

    private suspend fun drainRemoteChanges() {
        var since = syncOperations.currentSyncToken()
        while (true) {
            val page = try {
                transport.pull(since)
            } catch (ignored: SyncTokenExpiredException) {
                pullFullSnapshot()
                return
            }
            syncOperations.applyDelta(page.notes, page.nextSyncToken)
            if (!page.hasMore) return
            since = page.nextSyncToken
        }
    }

    private suspend fun pullFullSnapshot() {
        var since: String? = null
        while (true) {
            val page = transport.pull(since)
            syncOperations.applySnapshot(page.notes, page.nextSyncToken)
            if (!page.hasMore) return
            since = page.nextSyncToken
        }
    }
}
