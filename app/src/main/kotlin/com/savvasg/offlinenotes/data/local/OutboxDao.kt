package com.savvasg.offlinenotes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    // Room's generated @Upsert resolves conflicts via INSERT OR IGNORE, so a would-be violation
    // of idx_outbox_one_pending here no-ops silently rather than throwing. That's fine only
    // because callers (NoteTransactions) always check-then-act inside the same db.withTransaction,
    // so the invariant is never actually attempted to be violated through this method.
    @Upsert
    suspend fun upsert(op: OutboxEntity)

    /** At most one row per note by construction (idx_outbox_one_pending). */
    @Query("SELECT * FROM outbox WHERE noteId = :noteId AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingForNote(noteId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE opId = :opId")
    suspend fun getById(opId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingOpsOrderedByCreatedAt(): List<OutboxEntity>

    @Query("UPDATE outbox SET status = :status WHERE opId = :opId")
    suspend fun updateStatus(opId: String, status: OutboxStatus)

    /** An op row is deleted only when its data is on the server, or the user superseded it. */
    @Query("DELETE FROM outbox WHERE opId = :opId")
    suspend fun delete(opId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE noteId = :noteId")
    suspend fun countForNote(noteId: String): Int

    /** Crash recovery (§6 pipeline step 0). Safe because opIds are stable and replay dedupes. */
    @Query("UPDATE outbox SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInFlightToPending()

    /** Backs the debug-panel outbox inspector (§10). */
    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutboxEntity>>
}
