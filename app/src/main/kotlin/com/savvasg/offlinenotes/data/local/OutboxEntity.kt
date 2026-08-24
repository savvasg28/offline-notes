package com.savvasg.offlinenotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `baseVersion` is deliberately NOT a column here — it is resolved from [NoteEntity.serverVersion]
 * at freeze time (see NoteTransactions.freezeTxn), not stamped at op-creation time. Storing it
 * early would ship a stale base after a prior op's ack bumps the version, producing a phantom
 * self-conflict.
 *
 * The [OutboxStatus.PENDING] partial unique index on `noteId` — the coalescing invariant — is
 * created via raw SQL in [OfflineNotesDatabase]'s creation callback, since @Index cannot express
 * a WHERE clause. IN_FLIGHT and PARKED rows are exempt by construction: an edit arriving while an
 * op is in flight legitimately creates a second row.
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
