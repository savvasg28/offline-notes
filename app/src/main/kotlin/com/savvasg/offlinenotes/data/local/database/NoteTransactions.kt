package com.savvasg.offlinenotes.data.local.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.savvasg.offlinenotes.data.local.dao.ConflictDao
import com.savvasg.offlinenotes.data.local.dao.NoteDao
import com.savvasg.offlinenotes.data.local.dao.OutboxDao
import com.savvasg.offlinenotes.data.local.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.local.entity.NoteConflictEntity
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxStatus
import com.savvasg.offlinenotes.data.local.entity.SyncMetaEntity
import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.OpType
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.model.SyncState
import java.util.UUID
import javax.inject.Inject

/**
 * Each function here is exactly one BEGIN...COMMIT spanning notes + outbox (+ note_conflicts), so
 * callers get atomicity by calling one of them rather than by sequencing writes correctly.
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
     * `androidx.room.withTransaction` assumes a `SupportSQLiteOpenHelper`, which does not exist once
     * the database is built with `setDriver(BundledSQLiteDriver())` (see [OfflineNotesDatabase]) — it
     * throws "Cannot return a SupportSQLiteOpenHelper" at runtime. This is the driver-based form.
     */
    private suspend fun <R> transaction(block: suspend () -> R): R =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

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

    /** Tombstones locally; the row is purged only once the server acks the delete, never here. */
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
     * Drain start, and the linearization point. baseVersion is resolved here rather than at enqueue,
     * so an op created concurrently with a drain cannot ship a stale base.
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

    /** Moves only the sync watermark, so edits made after the freeze (now in a fresh op) survive. */
    suspend fun applyAppliedTxn(opId: String, newVersion: String) {
        transaction {
            val op = outboxDao.getById(opId) ?: return@transaction
            noteDao.updateServerVersion(op.noteId, newVersion)
            outboxDao.delete(opId)
            val remaining = outboxDao.countForNote(op.noteId)
            noteDao.updateSyncState(op.noteId, if (remaining == 0) SyncState.SYNCED else SyncState.PENDING)
        }
    }

    /** Stages base+remote alongside the untouched local content; remote is never auto-applied. */
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

    /** A rejected op is a poison pill: deleted rather than retried, and returned so the caller can surface it. */
    suspend fun applyRejectedTxn(opId: String): OutboxEntity? = transaction {
        val op = outboxDao.getById(opId) ?: return@transaction null
        outboxDao.delete(opId)
        op
    }

    /**
     * Overwriting blindly when no op exists is only correct because the write txn guarantees every
     * user edit has one. The page and the advanced token share a transaction — advancing the cursor
     * separately would leave a permanent silent gap on crash.
     */
    suspend fun applyDeltaTxn(remoteNotes: List<RemoteNoteChange>, newSyncToken: String, now: Long) {
        transaction {
            applyRemoteNotesLocked(remoteNotes, now)
            syncMetaDao.setSyncToken(SyncMetaEntity(syncToken = newSyncToken))
        }
    }

    /**
     * A bootstrap snapshot is exhaustive, so unlike a delta page it also has to purge: a
     * previously-synced local note absent from the snapshot, with no live op protecting it, was
     * deleted server-side. Notes with no serverVersion always have a live op, so the ordinary
     * skip rule already exempts them.
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
                outboxDao.hasPendingOrInFlight(remote.noteId) -> Unit
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
     * Seeds the fresh op's base from the conflict's remoteVersion rather than the note's own
     * (stale) serverVersion, which is what lets an ordinary write settle a conflict — there is no
     * separate resolve call in the wire contract.
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
