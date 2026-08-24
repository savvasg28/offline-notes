package com.savvasg.offlinenotes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the opaque delta-pull cursor (§6, §7). Not one of the three tables
 * named in §4, but required by it: "token and page must commit together" (§6) can only be a
 * real guarantee if the token lives in the same Room database/transaction scope as the page it
 * advances past — a separate store (e.g. DataStore) couldn't commit atomically with it.
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
