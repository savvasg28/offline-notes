package com.savvasg.offlinenotes.domain.repository

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange

sealed interface OpAcknowledgement {
    data class Applied(val newVersion: String) : OpAcknowledgement
    data object Rejected : OpAcknowledgement
}

/**
 * The sync engine drives these directly and must **never** call [NoteRepository]'s UI-facing
 * methods — [saveNote][NoteRepository.saveNote] would generate a fresh outbox op from pulled data
 * and echo the server's own changes back at it. UI writes are intents; these are acknowledgements.
 */
interface NoteSyncOperations {
    /** The linearization point. Returns the ops a push should send this round. */
    suspend fun freezeAndDrain(): List<FrozenOutboxOp>

    suspend fun applyResult(opId: String, ack: OpAcknowledgement)

    suspend fun stageConflict(opId: String, conflict: ConflictPayload)

    /** One delta-pull page and the advanced token, applied atomically. */
    suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    /**
     * Same skip rule as [applyDelta], plus purging previously-synced notes absent from the snapshot
     * with no live op protecting them.
     */
    suspend fun applyBootstrap(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    /** Crash recovery: stale IN_FLIGHT ops go back to PENDING. */
    suspend fun resetStaleInFlight()

    /** The opaque cursor for the next pull; null before the first sync ever runs. */
    suspend fun currentSyncToken(): String?

    suspend fun hasPendingOps(): Boolean
}
