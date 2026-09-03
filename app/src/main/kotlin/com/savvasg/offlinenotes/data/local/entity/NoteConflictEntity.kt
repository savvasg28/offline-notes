package com.savvasg.offlinenotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * No local columns: local *is* the current [NoteEntity] row. The three merge inputs live in three
 * places by design, one source of truth each. The remote side is refreshed in place by later delta
 * pulls while the conflict is unresolved.
 */
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
