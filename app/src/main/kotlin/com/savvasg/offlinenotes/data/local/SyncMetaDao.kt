package com.savvasg.offlinenotes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncMetaDao {
    @Query("SELECT syncToken FROM sync_meta WHERE id = ${SyncMetaEntity.SINGLETON_ID}")
    suspend fun getSyncToken(): String?

    @Upsert
    suspend fun setSyncToken(meta: SyncMetaEntity)

    /** Bootstrap / token-expiry re-bootstrap (§7) clears the cursor. */
    @Query("DELETE FROM sync_meta")
    suspend fun clear()
}
