package com.savvasg.offlinenotes.data.repository

import com.savvasg.offlinenotes.data.local.dao.ConflictDao
import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.data.local.dao.NoteDao
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.data.local.database.NoteTransactions
import com.savvasg.offlinenotes.data.local.dao.OutboxDao
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.data.local.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.local.dao.searchActiveNotes
import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.model.NoteListItem
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import com.savvasg.offlinenotes.sync.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao,
    private val syncMetaDao: SyncMetaDao,
    private val transactions: NoteTransactions,
    private val syncScheduler: SyncScheduler,
) : NoteRepository, NoteSyncOperations {

    override fun observeNotes(): Flow<List<NoteListItem>> =
        noteDao.observeActiveNotes().map { notes -> notes.map { it.toListItem() } }

    override fun observeNote(id: String): Flow<Note?> =
        noteDao.observeNote(id).map { it?.toDomain() }

    override suspend fun saveNote(id: String, title: String, body: String) {
        transactions.writeTxn(id, title, body, now = System.currentTimeMillis())
        syncScheduler.scheduleDebouncedSync()
    }

    override suspend fun deleteNote(id: String) {
        transactions.deleteTxn(id, now = System.currentTimeMillis())
        syncScheduler.scheduleDebouncedSync()
    }

    override suspend fun resolveConflict(noteId: String, title: String, body: String) {
        transactions.resolveConflictTxn(noteId, title, body, now = System.currentTimeMillis())
        syncScheduler.scheduleDebouncedSync()
    }

    override fun searchNotes(query: String): Flow<List<NoteListItem>> {
        val matchQuery = buildFtsMatchQuery(query) ?: return observeNotes()
        return noteDao.searchActiveNotes(matchQuery).map { notes -> notes.map { it.toListItem() } }
    }

    override fun observeConflict(noteId: String): Flow<NoteConflict?> =
        combine(noteDao.observeNote(noteId), conflictDao.observeForNote(noteId)) { note, conflict ->
            if (note == null || conflict == null) return@combine null
            NoteConflict(
                noteId = noteId,
                localTitle = note.title,
                localBody = note.body,
                baseVersion = conflict.baseVersion,
                baseTitle = conflict.baseTitle,
                baseBody = conflict.baseBody,
                remoteVersion = conflict.remoteVersion,
                remoteTitle = conflict.remoteTitle,
                remoteBody = conflict.remoteBody,
            )
        }


    override suspend fun freezeAndDrain(): List<FrozenOutboxOp> = transactions.freezeTxn()

    override suspend fun applyResult(opId: String, ack: OpAcknowledgement) {
        when (ack) {
            is OpAcknowledgement.Applied -> transactions.applyAppliedTxn(opId, ack.newVersion)
            OpAcknowledgement.Rejected -> transactions.applyRejectedTxn(opId)
        }
    }

    override suspend fun stageConflict(opId: String, conflict: ConflictPayload) {
        transactions.applyConflictTxn(opId, conflict, detectedAt = System.currentTimeMillis())
    }

    override suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String) {
        transactions.applyDeltaTxn(remoteNotes, newSyncToken, now = System.currentTimeMillis())
    }

    override suspend fun applyBootstrap(remoteNotes: List<RemoteNoteChange>, newSyncToken: String) {
        transactions.applyBootstrapTxn(remoteNotes, newSyncToken, now = System.currentTimeMillis())
    }

    override suspend fun resetStaleInFlight() {
        outboxDao.resetInFlightToPending()
    }

    override suspend fun currentSyncToken(): String? = syncMetaDao.getSyncToken()

    override suspend fun hasPendingOps(): Boolean = outboxDao.hasAnyPending()
}

private fun NoteEntity.toDomain() = Note(
    id = id,
    title = title,
    body = body,
    syncState = syncState,
    isDeleted = isDeleted,
    createdAt = createdAt,
    lastUpdated = lastUpdated,
)

private fun NoteEntity.toListItem() = NoteListItem(
    id = id,
    title = title,
    bodySnippet = body.take(140),
    lastUpdated = lastUpdated,
    syncState = syncState,
)

internal fun buildFtsMatchQuery(raw: String): String? {
    val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { token -> "\"${token.replace("\"", "\"\"")}\"*" }
}
