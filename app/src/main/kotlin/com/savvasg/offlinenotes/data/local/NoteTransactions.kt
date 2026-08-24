package com.savvasg.offlinenotes.data.local

import androidx.room.withTransaction
import java.util.UUID
import javax.inject.Inject

/**
 * The three transaction types from §5 — the ground every sync correctness claim in this app
 * reduces to. Each is exactly one `BEGIN…COMMIT`, spanning `notes` + `outbox` (+ `note_conflicts`
 * for the conflict apply result), so atomicity (invariant 4) comes free with the transaction
 * rather than being something callers have to get right.
 */
class NoteTransactions @Inject constructor(
    private val db: OfflineNotesDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao,
) {
    private fun newOpId(): String = UUID.randomUUID().toString()

    /**
     * Write txn (§5): a user edit. Upserts the note and coalesces into the note's existing
     * PENDING op if one exists (same opId, opType unchanged — a CREATE stays a CREATE), or
     * creates a new op otherwise. opType is decided from `serverVersion IS NULL` at that moment,
     * never from edit count.
     */
    suspend fun writeTxn(noteId: String, title: String, body: String, now: Long) {
        db.withTransaction {
            val existing = noteDao.getNote(noteId)
            noteDao.upsert(
                NoteEntity(
                    id = noteId,
                    title = title,
                    body = body,
                    serverVersion = existing?.serverVersion,
                    syncState = SyncState.PENDING,
                    isDeleted = false,
                    createdAt = existing?.createdAt ?: now,
                    lastUpdated = now,
                ),
            )

            val pendingOp = outboxDao.getPendingForNote(noteId)
            if (pendingOp != null) {
                outboxDao.upsert(pendingOp.copy(title = title, body = body))
            } else {
                outboxDao.upsert(
                    OutboxEntity(
                        opId = newOpId(),
                        noteId = noteId,
                        opType = if (existing?.serverVersion == null) OpType.CREATE else OpType.UPDATE,
                        title = title,
                        body = body,
                        status = OutboxStatus.PENDING,
                        createdAt = now,
                        attemptCount = 0,
                    ),
                )
            }
        }
    }

    /**
     * Delete txn (§5 family, §8 delete/edit race): tombstones the note locally and coalesces into
     * the note's PENDING op the same way [writeTxn] does, but as opType DELETE with no payload.
     * The row is purged only after the server acks the delete (§4) — never here.
     */
    suspend fun deleteTxn(noteId: String, now: Long) {
        db.withTransaction {
            val existing = noteDao.getNote(noteId) ?: return@withTransaction
            noteDao.upsert(existing.copy(syncState = SyncState.PENDING, isDeleted = true, lastUpdated = now))

            val pendingOp = outboxDao.getPendingForNote(noteId)
            if (pendingOp != null) {
                outboxDao.upsert(pendingOp.copy(opType = OpType.DELETE, title = null, body = null))
            } else {
                outboxDao.upsert(
                    OutboxEntity(
                        opId = newOpId(),
                        noteId = noteId,
                        opType = OpType.DELETE,
                        title = null,
                        body = null,
                        status = OutboxStatus.PENDING,
                        createdAt = now,
                        attemptCount = 0,
                    ),
                )
            }
        }
    }

    /**
     * Freeze txn (§5): drain start, the linearization point. PENDING → IN_FLIGHT for every
     * drainable op, resolving each one's baseVersion from `notes.serverVersion` right here so a
     * concurrently-created op can never ship a stale base. The returned payloads are immutable
     * from this point on.
     */
    suspend fun freezeTxn(): List<FrozenOutboxOp> = db.withTransaction {
        outboxDao.getPendingOpsOrderedByCreatedAt().map { op ->
            val note = requireNotNull(noteDao.getNote(op.noteId)) {
                "outbox op ${op.opId} references missing note ${op.noteId}"
            }
            outboxDao.updateStatus(op.opId, OutboxStatus.IN_FLIGHT)
            FrozenOutboxOp(
                opId = op.opId,
                noteId = op.noteId,
                opType = op.opType,
                baseVersion = note.serverVersion,
                title = op.title,
                body = op.body,
            )
        }
    }

    /**
     * Apply txn (§5): APPLIED result. Only the sync watermark moves — content is never touched,
     * so edits made after the freeze (already sitting in a fresh PENDING op) are preserved
     * untouched. syncState only returns to SYNCED once no outbox rows remain for the note.
     */
    suspend fun applyAppliedTxn(opId: String, newVersion: String) {
        db.withTransaction {
            val op = outboxDao.getById(opId) ?: return@withTransaction
            noteDao.updateServerVersion(op.noteId, newVersion)
            outboxDao.delete(opId)
            val remaining = outboxDao.countForNote(op.noteId)
            noteDao.updateSyncState(op.noteId, if (remaining == 0) SyncState.SYNCED else SyncState.PENDING)
        }
    }

    /**
     * Apply txn (§5): CONFLICT result. Parks the op and stages all three merge inputs — local
     * stays in `notes`, base+remote land in `note_conflicts` — never auto-applying remote content
     * (invariant 2; see §7's "no MERGED result").
     */
    suspend fun applyConflictTxn(opId: String, conflict: ConflictPayload, detectedAt: Long) {
        db.withTransaction {
            val op = outboxDao.getById(opId) ?: return@withTransaction
            outboxDao.updateStatus(opId, OutboxStatus.PARKED)
            conflictDao.upsert(
                NoteConflictEntity(
                    noteId = op.noteId,
                    opId = opId,
                    baseVersion = conflict.baseVersion,
                    baseTitle = conflict.baseTitle,
                    baseBody = conflict.baseBody,
                    remoteVersion = conflict.remoteVersion,
                    remoteTitle = conflict.remoteTitle,
                    remoteBody = conflict.remoteBody,
                    detectedAt = detectedAt,
                ),
            )
            noteDao.updateSyncState(op.noteId, SyncState.CONFLICT)
        }
    }

    /**
     * Apply txn (§5): REJECTED result. The op is a poison pill — deleted, never retried. Returns
     * the deleted row so the caller can surface the error; the transaction itself is bookkeeping
     * only.
     */
    suspend fun applyRejectedTxn(opId: String): OutboxEntity? = db.withTransaction {
        val op = outboxDao.getById(opId) ?: return@withTransaction null
        outboxDao.delete(opId)
        op
    }
}
