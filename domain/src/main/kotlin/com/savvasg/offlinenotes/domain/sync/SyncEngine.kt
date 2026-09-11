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
    private val monitor: SyncMonitor,
) {
    suspend fun sync(): SyncOutcome = runSync().also(monitor::onSyncFinished)

    private suspend fun runSync(): SyncOutcome {
        syncOperations.resetStaleInFlight()

        try {
            val frozen = syncOperations.freezeAndDrain()
            if (frozen.isNotEmpty()) {
                applyOutcomes(transport.push(frozen))
            }
            drainRemoteChanges()
        } catch (failure: TransportException) {
            monitor.onTransportFailure(failure)
            return SyncOutcome.TransportFailure
        }

        return if (syncOperations.hasPendingOps()) SyncOutcome.MoreWorkPending else SyncOutcome.Done
    }

    private suspend fun applyOutcomes(outcomes: List<OpOutcome>) {
        for (outcome in outcomes) {
            when (outcome) {
                is OpOutcome.Applied ->
                    syncOperations.applyResult(outcome.opId, OpAcknowledgement.Applied(outcome.newVersion))

                is OpOutcome.Rejected ->
                    syncOperations.applyResult(outcome.opId, OpAcknowledgement.Rejected(outcome.code, outcome.message))

                is OpOutcome.Conflicted ->
                    syncOperations.stageConflict(outcome.opId, outcome.conflict)
            }
        }
    }

    private suspend fun drainRemoteChanges() {
        var since = syncOperations.currentSyncToken()
        while (true) {
            val page = try {
                transport.pull(since)
            } catch (expired: SyncTokenExpiredException) {
                monitor.onSyncTokenExpired(expired)
                pullFullSnapshot()
                return
            }
            syncOperations.applyDelta(page.notes, page.nextSyncToken)
            if (!page.hasMore) return
            since = page.nextSyncToken
        }
    }

    private suspend fun pullFullSnapshot() {
        val presentNoteIds = mutableSetOf<String>()
        var since: String? = null
        while (true) {
            val page = transport.pull(since)
            syncOperations.applySnapshotPage(page.notes)
            page.notes.mapTo(presentNoteIds) { it.noteId }
            if (!page.hasMore) {
                syncOperations.completeSnapshot(presentNoteIds, page.nextSyncToken)
                return
            }
            since = page.nextSyncToken
        }
    }
}
