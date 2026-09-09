package com.savvasg.offlinenotes.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.savvasg.offlinenotes.data.database.entity.OutboxEntity
import com.savvasg.offlinenotes.data.database.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Upsert
    suspend fun upsert(op: OutboxEntity)

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

    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE noteId = :noteId AND status IN ('PENDING', 'IN_FLIGHT'))")
    suspend fun hasPendingOrInFlight(noteId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE noteId = :noteId AND status = 'PARKED')")
    suspend fun hasParked(noteId: String): Boolean

    @Query("UPDATE outbox SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInFlightToPending()

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutboxEntity>>
}
