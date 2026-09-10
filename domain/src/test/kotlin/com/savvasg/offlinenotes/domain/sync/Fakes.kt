package com.savvasg.offlinenotes.domain.sync

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.OpType
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement

class RecordingSyncOperations(
    private var frozen: List<FrozenOutboxOp> = emptyList(),
    var pendingAfterDrain: Boolean = false,
    var startingToken: String? = null,
) : NoteSyncOperations {

    val acknowledgements = mutableListOf<Pair<String, OpAcknowledgement>>()
    val stagedConflicts = mutableListOf<Pair<String, ConflictPayload>>()
    val appliedDeltas = mutableListOf<Pair<List<RemoteNoteChange>, String>>()
    val appliedSnapshots = mutableListOf<Pair<List<RemoteNoteChange>, String>>()
    var staleResets = 0

    override suspend fun freezeAndDrain(): List<FrozenOutboxOp> = frozen.also { frozen = emptyList() }

    override suspend fun applyResult(opId: String, ack: OpAcknowledgement) {
        acknowledgements += opId to ack
    }

    override suspend fun stageConflict(opId: String, conflict: ConflictPayload) {
        stagedConflicts += opId to conflict
    }

    override suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String) {
        appliedDeltas += remoteNotes to newSyncToken
    }

    override suspend fun applySnapshot(remoteNotes: List<RemoteNoteChange>, newSyncToken: String) {
        appliedSnapshots += remoteNotes to newSyncToken
    }

    override suspend fun resetStaleInFlight() {
        staleResets++
    }

    override suspend fun currentSyncToken(): String? = startingToken

    override suspend fun hasPendingOps(): Boolean = pendingAfterDrain
}

class ScriptedTransport : SyncTransport {
    val pushed = mutableListOf<List<FrozenOutboxOp>>()
    val pullCursors = mutableListOf<String?>()

    var pushOutcome: (List<FrozenOutboxOp>) -> List<OpOutcome> = { ops ->
        ops.map { OpOutcome.Applied(it.opId, "1") }
    }
    var pages: MutableList<RemoteChangePage> = mutableListOf()
    var failPushWith: TransportException? = null
    var expireTokenOnce = false

    override suspend fun push(ops: List<FrozenOutboxOp>): List<OpOutcome> {
        pushed += ops
        failPushWith?.let { throw it }
        return pushOutcome(ops)
    }

    override suspend fun pull(since: String?): RemoteChangePage {
        pullCursors += since
        if (expireTokenOnce && since != null) {
            expireTokenOnce = false
            throw SyncTokenExpiredException()
        }
        return if (pages.isNotEmpty()) pages.removeAt(0) else emptyPage(since)
    }

    private fun emptyPage(since: String?) =
        RemoteChangePage(notes = emptyList(), nextSyncToken = since ?: "0", hasMore = false)
}

class RecordingMonitor : SyncMonitor {
    val transportFailures = mutableListOf<TransportException>()
    var tokenExpiries = 0
    val outcomes = mutableListOf<SyncOutcome>()

    override fun onTransportFailure(cause: TransportException) {
        transportFailures += cause
    }

    override fun onSyncTokenExpired(cause: SyncTokenExpiredException) {
        tokenExpiries++
    }

    override fun onSyncFinished(outcome: SyncOutcome) {
        outcomes += outcome
    }
}

fun op(opId: String, noteId: String = "note-1") =
    FrozenOutboxOp(opId, noteId, OpType.UPDATE, baseVersion = "1", title = "T", body = "B")
