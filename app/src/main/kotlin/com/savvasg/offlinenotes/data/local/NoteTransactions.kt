package com.savvasg.offlinenotes.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
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
    private val syncMetaDao: SyncMetaDao,
) {
    private fun newOpId(): String = UUID.randomUUID().toString()

    /**
     * `androidx.room.withTransaction` assumes a `SupportSQLiteOpenHelper`, which doesn't exist
     * once the database is built with `setDriver(BundledSQLiteDriver())` (see
     * [OfflineNotesDatabase.CALLBACK]'s kdoc for why bundled SQLite is required at all) — it
     * throws "Cannot return a SupportSQLiteOpenHelper" at runtime. This is the driver-based
     * replacement: acquire the writer connection and start a transaction on it directly.
     */
    private suspend fun <R> transaction(block: suspend () -> R): R =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

    /**
     * Write txn (§5): a user edit. Upserts the note and coalesces into the note's existing
     * PENDING op if one exists (same opId, opType unchanged — a CREATE stays a CREATE), or
     * creates a new op otherwise. opType is decided from `serverVersion IS NULL` at that moment,
     * never from edit count.
     */
    suspend fun writeTxn(noteId: String, title: String, body: String, now: Long) {
        transaction {
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
        transaction {
            val existing = noteDao.getNote(noteId) ?: return@transaction
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
    suspend fun freezeTxn(): List<FrozenOutboxOp> = transaction {
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
        transaction {
            val op = outboxDao.getById(opId) ?: return@transaction
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
        transaction {
            val op = outboxDao.getById(opId) ?: return@transaction
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
    suspend fun applyRejectedTxn(opId: String): OutboxEntity? = transaction {
        val op = outboxDao.getById(opId) ?: return@transaction null
        outboxDao.delete(opId)
        op
    }

    /**
     * Delta-pull page apply (§6): the skip rule read-path half of invariant 2. Safe to overwrite
     * blindly when no op exists *because* the write txn guarantees any user edit has a
     * corresponding op — the outbox isn't just a queue, it's what makes blind overwrite correct.
     * The whole page and the advanced [newSyncToken] land in one transaction: advancing the
     * cursor outside of it would leave a silent permanent gap on crash.
     */
    suspend fun applyDeltaTxn(remoteNotes: List<RemoteNoteChange>, newSyncToken: String, now: Long) {
        transaction {
            applyRemoteNotesLocked(remoteNotes, now)
            syncMetaDao.setSyncToken(SyncMetaEntity(syncToken = newSyncToken))
        }
    }

    /**
     * Bootstrap / re-bootstrap after token expiry (§7): applies the full current-state snapshot
     * the same way [applyDeltaTxn] applies a page, *plus* one extra pass a plain delta page
     * doesn't need — a bootstrap snapshot is exhaustive, so a previously-synced local note
     * (`serverVersion != null`) that's simply absent from it, with no live op protecting it, was
     * deleted server-side and must be purged locally. A local note with `serverVersion == null`
     * needs no such handling: by invariant it always has a live (PENDING/IN_FLIGHT) op, which
     * already exempts it via the ordinary skip rule.
     */
    suspend fun applyBootstrapTxn(remoteNotes: List<RemoteNoteChange>, newSyncToken: String, now: Long) {
        transaction {
            applyRemoteNotesLocked(remoteNotes, now)

            val remoteIds = remoteNotes.mapTo(mutableSetOf()) { it.noteId }
            for (local in noteDao.getAllSyncedNotes()) {
                if (local.id in remoteIds) continue
                if (outboxDao.hasPendingOrInFlight(local.id) || outboxDao.hasParked(local.id)) continue
                noteDao.purge(local.id)
            }

            syncMetaDao.setSyncToken(SyncMetaEntity(syncToken = newSyncToken))
        }
    }

    private suspend fun applyRemoteNotesLocked(remoteNotes: List<RemoteNoteChange>, now: Long) {
        for (remote in remoteNotes) {
            when {
                outboxDao.hasPendingOrInFlight(remote.noteId) -> Unit // SKIP entirely
                outboxDao.hasParked(remote.noteId) -> conflictDao.refreshRemote(
                    remote.noteId,
                    remote.version,
                    remote.title.orEmpty(),
                    remote.body.orEmpty(),
                )
                else -> {
                    val local = noteDao.getNote(remote.noteId)
                    when {
                        local?.serverVersion == remote.version -> Unit // no-op, avoid churn
                        remote.isDeleted -> if (local != null) noteDao.purge(remote.noteId)
                        else -> noteDao.upsert(
                            NoteEntity(
                                id = remote.noteId,
                                title = remote.title.orEmpty(),
                                body = remote.body.orEmpty(),
                                serverVersion = remote.version,
                                syncState = SyncState.SYNCED,
                                isDeleted = false,
                                createdAt = local?.createdAt ?: now,
                                lastUpdated = now,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Conflict resolution (§8): one transaction — delete the parked op, delete the conflict row,
     * write the resolved content, and enqueue a fresh op (new opId, baseVersion = remoteVersion)
     * so the next drain ships it cleanly. There is no special "resolve" wire call (§8) — this is
     * an ordinary write, just seeded from the conflict's remote version instead of the note's own
     * (possibly stale) `serverVersion`.
     */
    suspend fun resolveConflictTxn(noteId: String, title: String, body: String, now: Long) {
        transaction {
            val conflict = conflictDao.getForNote(noteId) ?: return@transaction
            outboxDao.delete(conflict.opId)
            conflictDao.delete(noteId)

            noteDao.upsert(
                NoteEntity(
                    id = noteId,
                    title = title,
                    body = body,
                    serverVersion = conflict.remoteVersion,
                    syncState = SyncState.PENDING,
                    isDeleted = false,
                    createdAt = noteDao.getNote(noteId)?.createdAt ?: now,
                    lastUpdated = now,
                ),
            )
            outboxDao.upsert(
                OutboxEntity(
                    opId = newOpId(),
                    noteId = noteId,
                    opType = OpType.UPDATE,
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
