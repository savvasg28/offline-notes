package com.savvasg.offlinenotes.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_conflicts",
    foreignKeys = [
        ForeignKey(
            entity = OutboxEntity::class,
            parentColumns = ["opId"],
            childColumns = ["opId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["opId"])],
)
data class NoteConflictEntity(
    @PrimaryKey val noteId: String,
    val opId: String,
    val baseVersion: String?,
    val baseTitle: String?,
    val baseBody: String?,
    val remoteVersion: String,
    val remoteTitle: String,
    val remoteBody: String,
    val detectedAt: Long,
)
