package com.savvasg.offlinenotes.data

import com.savvasg.offlinenotes.data.database.NoteTransactions
import com.savvasg.offlinenotes.data.database.dao.ConflictDao
import com.savvasg.offlinenotes.data.database.dao.NoteDao
import com.savvasg.offlinenotes.data.database.dao.OutboxDao
import com.savvasg.offlinenotes.data.database.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.database.dao.searchActiveNotes
import com.savvasg.offlinenotes.data.database.entity.NoteEntity
import com.savvasg.offlinenotes.data.database.entity.OutboxEntity
import com.savvasg.offlinenotes.domain.ApplicationScope
import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.model.NoteSummary
import com.savvasg.offlinenotes.domain.model.RejectionReason
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao,
    private val syncMetaDao: SyncMetaDao,
    private val transactions: NoteTransactions,
    private val syncTrigger: SyncTrigger,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : NoteRepository, NoteSyncOperations {

    private suspend fun <T> durable(block: suspend () -> T): T =
        applicationScope.async { block() }.await()

    override fun observeNotes(): Flow<List<NoteSummary>> =
        noteDao.observeActiveNotes().map { notes -> notes.map { it.toSummary() } }

    override fun observeNote(id: String): Flow<Note?> =
        combine(noteDao.observeNote(id), outboxDao.observeRejectedForNote(id)) { note, rejected ->
            note?.toDomain(rejected?.toRejectionReason())
        }

    override suspend fun saveNote(id: String, title: String, body: String) = durable {
        transactions.write(id, title, body, now = System.currentTimeMillis())
        syncTrigger.requestSync()
    }

    override suspend fun deleteNote(id: String) = durable {
        transactions.delete(id, now = System.currentTimeMillis())
        syncTrigger.requestSync()
    }

    override suspend fun resolveConflict(noteId: String, title: String, body: String) = durable {
        transactions.resolveConflict(noteId, title, body, now = System.currentTimeMillis())
        syncTrigger.requestSync()
    }

    override suspend fun discardRejectedChange(noteId: String) = durable {
        transactions.discardRejected(noteId)
        syncTrigger.requestSync()
    }

    override fun searchNotes(query: String): Flow<List<NoteSummary>> {
        val matchQuery = buildFtsMatchQuery(query) ?: return observeNotes()
        return noteDao.searchActiveNotes(matchQuery).map { notes -> notes.map { it.toSummary() } }
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

    override suspend fun freezeAndDrain(): List<FrozenOutboxOp> = transactions.freeze()

    override suspend fun applyResult(opId: String, ack: OpAcknowledgement) {
        when (ack) {
            is OpAcknowledgement.Applied -> transactions.markApplied(opId, ack.newVersion)
            is OpAcknowledgement.Rejected ->
                transactions.markRejected(opId, ack.code, ack.message, now = System.currentTimeMillis())
        }
    }

    override suspend fun stageConflict(opId: String, conflict: ConflictPayload) {
        transactions.stageConflict(opId, conflict, detectedAt = System.currentTimeMillis())
    }

    override suspend fun applyDelta(remoteNotes: List<RemoteNoteChange>, newSyncToken: String) {
        transactions.applyDelta(remoteNotes, newSyncToken, now = System.currentTimeMillis())
    }

    override suspend fun applySnapshotPage(remoteNotes: List<RemoteNoteChange>) {
        transactions.applySnapshotPage(remoteNotes, now = System.currentTimeMillis())
    }

    override suspend fun completeSnapshot(presentNoteIds: Set<String>, newSyncToken: String) {
        transactions.completeSnapshot(presentNoteIds, newSyncToken)
    }

    override suspend fun resetStaleInFlight() {
        outboxDao.resetInFlightToPending()
    }

    override suspend fun currentSyncToken(): String? = syncMetaDao.getSyncToken()

    override suspend fun hasPendingOps(): Boolean = outboxDao.hasAnyPending()
}

private fun NoteEntity.toDomain(rejection: RejectionReason?) = Note(
    id = id,
    title = title,
    body = body,
    syncState = syncState,
    isDeleted = isDeleted,
    createdAt = createdAt,
    lastUpdated = lastUpdated,
    rejection = rejection,
)

private fun OutboxEntity.toRejectionReason(): RejectionReason? {
    val code = rejectionCode ?: return null
    return RejectionReason(code = code, message = rejectionMessage.orEmpty())
}

private const val SNIPPET_LENGTH = 140

private fun NoteEntity.toSummary() = NoteSummary(
    id = id,
    title = title,
    bodySnippet = body.take(SNIPPET_LENGTH),
    lastUpdated = lastUpdated,
    syncState = syncState,
)

internal fun buildFtsMatchQuery(raw: String): String? {
    val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { token -> "\"${token.replace("\"", "\"\"")}\"*" }
}
