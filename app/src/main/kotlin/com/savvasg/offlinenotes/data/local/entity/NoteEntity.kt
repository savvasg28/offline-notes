package com.savvasg.offlinenotes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.savvasg.offlinenotes.domain.model.SyncState

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
