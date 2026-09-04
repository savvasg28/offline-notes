package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.model.ConflictPayload
import com.savvasg.offlinenotes.core.model.FrozenOutboxOp
import com.savvasg.offlinenotes.core.model.RemoteNoteChange

sealed interface OpAcknowledgement {
    data class Applied(val newVersion: String) : OpAcknowledgement
    data object Rejected : OpAcknowledgement
}

interface NoteSyncOperations {
    suspend fun freezeAndDrain(): List<FrozenOutboxOp>

    suspend fun applyResult(opId: String, ack: OpAcknowledgement)

    suspend fun stageConflict(opId: String, conflict: ConflictPayload)

    suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    suspend fun applyBootstrap(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    suspend fun resetStaleInFlight()

    suspend fun currentSyncToken(): String?

    suspend fun hasPendingOps(): Boolean
}
