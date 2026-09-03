package com.savvasg.offlinenotes.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.savvasg.offlinenotes.data.local.database.NoteTransactions
import com.savvasg.offlinenotes.data.local.entity.OutboxEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    // Room's generated @Upsert resolves conflicts with INSERT OR IGNORE, so a would-be violation of
    // idx_outbox_one_pending no-ops silently here rather than throwing. That is only acceptable
    // because NoteTransactions always check-then-acts within one transaction, so the invariant is
    // never actually attempted through this method.
    @Upsert
    suspend fun upsert(op: OutboxEntity)

    /** At most one row per note by construction. */
    @Query("SELECT * FROM outbox WHERE noteId = :noteId AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingForNote(noteId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE opId = :opId")
    suspend fun getById(opId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingOpsOrderedByCreatedAt(): List<OutboxEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE status = 'PENDING')")
    suspend fun hasAnyPending(): Boolean

    @Query("UPDATE outbox SET status = :status WHERE opId = :opId")
    suspend fun updateStatus(opId: String, status: OutboxStatus)

    @Query("DELETE FROM outbox WHERE opId = :opId")
    suspend fun delete(opId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE noteId = :noteId")
    suspend fun countForNote(noteId: String): Int

    /** Skip rule, first half: a note with a live op is never overwritten by a pull. */
    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE noteId = :noteId AND status IN ('PENDING', 'IN_FLIGHT'))")
    suspend fun hasPendingOrInFlight(noteId: String): Boolean

    /** Skip rule, second half: a parked conflict only gets its remote side refreshed. */
    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE noteId = :noteId AND status = 'PARKED')")
    suspend fun hasParked(noteId: String): Boolean

    /** Safe because opIds are stable and a replayed op is deduped by the server. */
    @Query("UPDATE outbox SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInFlightToPending()

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutboxEntity>>
}
