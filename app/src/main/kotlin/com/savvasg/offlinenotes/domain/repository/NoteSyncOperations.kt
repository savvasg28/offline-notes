package com.savvasg.offlinenotes.domain.repository

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange

/** Result of a pushed op that isn't a conflict — see [NoteSyncOperations.applyResult]. */
sealed interface OpAcknowledgement {
    data class Applied(val newVersion: String) : OpAcknowledgement
    data object Rejected : OpAcknowledgement
}

/**
 * Sync-facing half of the repository (§3). The sync engine drives these directly; it must
 * **never** call [NoteRepository]'s UI-facing methods — [saveNote][NoteRepository.saveNote]
 * would generate a fresh outbox op from pulled data and echo the server's own changes back at
 * it. UI writes are intents; sync writes here are acknowledgements.
 */
interface NoteSyncOperations {
    /** Freeze txn (§5): the linearization point. Returns the ops a push should send this round. */
    suspend fun freezeAndDrain(): List<FrozenOutboxOp>

    /** Apply txn (§5), APPLIED or REJECTED branch. */
    suspend fun applyResult(opId: String, ack: OpAcknowledgement)

    /** Apply txn (§5), CONFLICT branch — parks the op and stages the three merge inputs. */
    suspend fun stageConflict(opId: String, conflict: ConflictPayload)

    /** One delta-pull page, applied with the skip rule (§6) and the advanced token, atomically. */
    suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    /**
     * A full current-state snapshot (§7 bootstrap / token-expiry re-bootstrap): same skip rule as
     * [applyDelta], plus purging previously-synced local notes that are absent from the snapshot
     * with no live op protecting them (deleted server-side while this client was disconnected).
     */
    suspend fun applyBootstrap(remoteNotes: List<RemoteNoteChange>, newSyncToken: String)

    /** Crash recovery (§6 pipeline step 0): stale IN_FLIGHT ops go back to PENDING. */
    suspend fun resetStaleInFlight()

    /** The opaque cursor to pass as `since` on the next pull; null before the first sync ever runs. */
    suspend fun currentSyncToken(): String?

    /** Loop check (§6 step 5): any PENDING op left after a drain round is live-editing residue. */
    suspend fun hasPendingOps(): Boolean
}
