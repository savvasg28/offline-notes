package com.savvasg.offlinenotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val syncToken: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
