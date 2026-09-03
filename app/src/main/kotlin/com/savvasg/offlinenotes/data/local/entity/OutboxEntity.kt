package com.savvasg.offlinenotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.savvasg.offlinenotes.data.local.database.OfflineNotesDatabase
import com.savvasg.offlinenotes.domain.model.OpType

/**
 * `baseVersion` is deliberately NOT a column: it is resolved from [NoteEntity.serverVersion] at
 * freeze time rather than stamped at op creation. Storing it early would ship a stale base once a
 * prior op's ack bumped the version, producing a phantom self-conflict.
 *
 * The partial unique index that allows at most one PENDING op per note is created in raw SQL by
 * [OfflineNotesDatabase]. IN_FLIGHT and PARKED rows are exempt by construction: an edit arriving
 * while an op is in flight legitimately creates a second row.
 */
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
