package com.savvasg.offlinenotes.data.repository

import com.savvasg.offlinenotes.data.local.ConflictPayload
import com.savvasg.offlinenotes.data.local.FrozenOutboxOp
import com.savvasg.offlinenotes.data.local.NoteDao
import com.savvasg.offlinenotes.data.local.NoteEntity
import com.savvasg.offlinenotes.data.local.NoteTransactions
import com.savvasg.offlinenotes.data.local.OutboxDao
import com.savvasg.offlinenotes.data.local.RemoteNoteChange
import com.savvasg.offlinenotes.data.local.SyncMetaDao
import com.savvasg.offlinenotes.data.local.searchActiveNotes
import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.NoteListItem
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import com.savvasg.offlinenotes.sync.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one implementation behind both repository interfaces (§3). Splitting the interfaces, not
 * the implementation, is what lets [freezeAndDrain]/[applyResult]/[stageConflict]/[applyDelta]/
 * [resetStaleInFlight] share the exact same DAOs and [NoteTransactions] as the UI-facing side
 * without either side being able to accidentally call into the other's intent.
 */
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val syncMetaDao: SyncMetaDao,
    private val transactions: NoteTransactions,
    private val syncScheduler: SyncScheduler,
) : NoteRepository, NoteSyncOperations {

    // ---- NoteRepository (UI-facing) ----

    override fun observeNotes(): Flow<List<NoteListItem>> =
        noteDao.observeActiveNotes().map { notes -> notes.map { it.toListItem() } }

    override fun observeNote(id: String): Flow<Note?> =
        noteDao.observeNote(id).map { it?.toDomain() }

    override suspend fun saveNote(id: String, title: String, body: String) {
        transactions.writeTxn(id, title, body, now = System.currentTimeMillis())
        syncScheduler.scheduleDebouncedSync() // first edit since idle (§6); KEEP makes the rest no-ops
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

    // ---- NoteSyncOperations (sync-facing) ----

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

/**
 * Builds a safe, prefix-matching FTS5 MATCH expression from raw search-box input: each token is
 * quoted (escaping embedded quotes) so FTS5 query-syntax operators typed by the user can't leak
 * through, then suffixed with `*` for prefix matching. Null for blank input — "no filter" rather
 * than an invalid empty MATCH.
 */
internal fun buildFtsMatchQuery(raw: String): String? {
    val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(" ") { token -> "\"${token.replace("\"", "\"\"")}\"*" }
}
