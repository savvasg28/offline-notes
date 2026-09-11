package com.savvasg.offlinenotes.domain.repository

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange

sealed interface OpAcknowledgement {
    data class Applied(val newVersion: String) : OpAcknowledgement
    data class Rejected(val code: String, val message: String) : OpAcknowledgement
}

interface NoteSyncOperations {
    suspend fun freezeAndDrain(): List<FrozenOutboxOp>

    suspend fun applyResult(opId: String, ack: OpAcknowledgement)

    suspend fun stageConflict(opId: String, conflict: ConflictPayload)

    suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    suspend fun applySnapshotPage(remoteNotes: List<RemoteNoteChange>)

    suspend fun completeSnapshot(presentNoteIds: Set<String>, newSyncToken: String)

    suspend fun resetStaleInFlight()

    suspend fun currentSyncToken(): String?

    suspend fun hasPendingOps(): Boolean
}
