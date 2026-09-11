package com.savvasg.offlinenotes.data.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.savvasg.offlinenotes.data.database.dao.ConflictDao
import com.savvasg.offlinenotes.data.database.dao.NoteDao
import com.savvasg.offlinenotes.data.database.dao.OutboxDao
import com.savvasg.offlinenotes.data.database.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.database.entity.NoteConflictEntity
import com.savvasg.offlinenotes.data.database.entity.NoteEntity
import com.savvasg.offlinenotes.data.database.entity.OutboxEntity
import com.savvasg.offlinenotes.data.database.entity.OutboxStatus
import com.savvasg.offlinenotes.data.database.entity.SyncMetaEntity
import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.OpType
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.model.SyncState
import java.util.UUID
import javax.inject.Inject

class NoteTransactions @Inject constructor(
    private val db: OfflineNotesDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao,
    private val syncMetaDao: SyncMetaDao,
) {
    private fun newOpId(): String = UUID.randomUUID().toString()

    private suspend fun <R> transaction(block: suspend () -> R): R =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

    private suspend fun recomputeSyncState(noteId: String) {
        val state = when {
            conflictDao.getForNote(noteId) != null -> SyncState.CONFLICT
            outboxDao.hasRejected(noteId) -> SyncState.REJECTED
            outboxDao.hasPendingOrInFlight(noteId) -> SyncState.PENDING
            else -> SyncState.SYNCED
        }
        noteDao.updateSyncState(noteId, state)
    }

    private suspend fun hasLiveOp(noteId: String): Boolean =
        outboxDao.hasPendingOrInFlight(noteId) ||
            outboxDao.hasParked(noteId) ||
            outboxDao.hasRejected(noteId)

    suspend fun write(noteId: String, title: String, body: String, now: Long) {
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

            outboxDao.deleteRejectedForNote(noteId)

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

            recomputeSyncState(noteId)
        }
    }

    suspend fun delete(noteId: String, now: Long) {
        transaction {
            val existing = noteDao.getNote(noteId) ?: return@transaction
            noteDao.upsert(existing.copy(syncState = SyncState.PENDING, isDeleted = true, lastUpdated = now))

            outboxDao.deleteRejectedForNote(noteId)

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

            recomputeSyncState(noteId)
        }
    }

    suspend fun freeze(): List<FrozenOutboxOp> = transaction {
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

    suspend fun markApplied(opId: String, newVersion: String) {
        transaction {
            val op = outboxDao.getById(opId) ?: return@transaction
            outboxDao.delete(opId)

            if (op.opType == OpType.DELETE && outboxDao.countForNote(op.noteId) == 0) {
                noteDao.purge(op.noteId)
                return@transaction
            }

            noteDao.updateServerVersion(op.noteId, newVersion)
            recomputeSyncState(op.noteId)
        }
    }

    suspend fun markRejected(opId: String, code: String, message: String, now: Long) {
        transaction {
            val op = outboxDao.getById(opId) ?: return@transaction
            outboxDao.markRejected(opId, code, message)

            if (op.opType == OpType.DELETE) {
                noteDao.updateIsDeleted(op.noteId, isDeleted = false, now = now)
            }

            recomputeSyncState(op.noteId)
        }
    }

    suspend fun discardRejected(noteId: String) {
        transaction {
            outboxDao.deleteRejectedForNote(noteId)
            recomputeSyncState(noteId)
        }
    }

    suspend fun stageConflict(opId: String, conflict: ConflictPayload, detectedAt: Long) {
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
            recomputeSyncState(op.noteId)
        }
    }

    suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String, now: Long) {
        transaction {
            applyRemoteNotes(remoteNotes, now)
            syncMetaDao.setSyncToken(SyncMetaEntity(syncToken = newSyncToken))
        }
    }

    suspend fun applySnapshotPage(remoteNotes: List<RemoteNoteChange>, now: Long) {
        transaction {
            applyRemoteNotes(remoteNotes, now)
        }
    }

    suspend fun completeSnapshot(presentNoteIds: Set<String>, newSyncToken: String) {
        transaction {
            for (local in noteDao.getAllSyncedNotes()) {
                val absentFromSnapshot = local.id !in presentNoteIds
                if (absentFromSnapshot && !hasLiveOp(local.id)) {
                    noteDao.purge(local.id)
                }
            }

            syncMetaDao.setSyncToken(SyncMetaEntity(syncToken = newSyncToken))
        }
    }

    private suspend fun applyRemoteNotes(remoteNotes: List<RemoteNoteChange>, now: Long) {
        for (remote in remoteNotes) {
            applyRemoteNote(remote, now)
        }
    }

    private suspend fun applyRemoteNote(remote: RemoteNoteChange, now: Long) {
        if (outboxDao.hasPendingOrInFlight(remote.noteId) || outboxDao.hasRejected(remote.noteId)) return
        if (outboxDao.hasParked(remote.noteId)) {
            conflictDao.refreshRemote(
                remote.noteId,
                remote.version,
                remote.title.orEmpty(),
                remote.body.orEmpty(),
            )
            return
        }

        val local = noteDao.getNote(remote.noteId)
        when {
            local?.serverVersion == remote.version -> Unit
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

    suspend fun resolveConflict(noteId: String, title: String, body: String, now: Long) {
        transaction {
            val conflict = conflictDao.getForNote(noteId) ?: return@transaction
            outboxDao.delete(conflict.opId)
            outboxDao.getPendingForNote(noteId)?.let { outboxDao.delete(it.opId) }
            outboxDao.deleteRejectedForNote(noteId)
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

            recomputeSyncState(noteId)
        }
    }
}
