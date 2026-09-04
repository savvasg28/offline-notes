package com.savvasg.offlinenotes.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.savvasg.offlinenotes.core.database.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
    @Query("SELECT syncToken FROM sync_meta WHERE id = ${SyncMetaEntity.SINGLETON_ID}")
    suspend fun getSyncToken(): String?

    @Query("SELECT syncToken FROM sync_meta WHERE id = ${SyncMetaEntity.SINGLETON_ID}")
    fun observeSyncToken(): Flow<String?>

    @Upsert
    suspend fun setSyncToken(meta: SyncMetaEntity)

    @Query("DELETE FROM sync_meta")
    suspend fun clear()
}
