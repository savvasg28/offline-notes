package com.savvasg.offlinenotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.savvasg.offlinenotes.data.local.database.OfflineNotesDatabase
import com.savvasg.offlinenotes.domain.model.OpType

@Entity(
    tableName = "outbox",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["status", "createdAt"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey val opId: String,
    val noteId: String,
    val opType: OpType,
    val title: String?,
    val body: String?,
    val status: OutboxStatus,
    val createdAt: Long,
    val attemptCount: Int,
)
