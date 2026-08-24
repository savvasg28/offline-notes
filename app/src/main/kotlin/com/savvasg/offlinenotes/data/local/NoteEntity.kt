package com.savvasg.offlinenotes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [serverVersion] is a sync watermark — the last version the server acked, and the base for the
 * next push — never a causality marker. It is only ever compared for equality (CAS), which is
 * what lets a content hash and a monotonic counter both satisfy it. Content is *supposed* to run
 * ahead of it while the user types; [syncState] is the flag that says whether they currently
 * coincide. NULL means never synced, i.e. the pending op type is CREATE.
 */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["isDeleted", "lastUpdated"], orders = [Index.Order.ASC, Index.Order.DESC])],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val serverVersion: String?,
    val syncState: SyncState,
    val isDeleted: Boolean,
    val createdAt: Long,
    val lastUpdated: Long,
)
