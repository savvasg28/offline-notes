package com.savvasg.offlinenotes.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.savvasg.offlinenotes.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
    @Query("SELECT syncToken FROM sync_meta WHERE id = ${SyncMetaEntity.SINGLETON_ID}")
    suspend fun getSyncToken(): String?

    /** Backs the debug panel's live "current sync token" display (§10). */
    @Query("SELECT syncToken FROM sync_meta WHERE id = ${SyncMetaEntity.SINGLETON_ID}")
    fun observeSyncToken(): Flow<String?>

    @Upsert
    suspend fun setSyncToken(meta: SyncMetaEntity)

    /** Bootstrap / token-expiry re-bootstrap (§7) clears the cursor. */
    @Query("DELETE FROM sync_meta")
    suspend fun clear()
}
