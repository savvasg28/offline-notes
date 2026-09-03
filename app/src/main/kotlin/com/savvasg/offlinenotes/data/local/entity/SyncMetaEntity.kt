package com.savvasg.offlinenotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The delta-pull cursor lives in this database, not in a separate store, so that it can be
 * advanced in the same transaction as the page it advances past.
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val syncToken: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
