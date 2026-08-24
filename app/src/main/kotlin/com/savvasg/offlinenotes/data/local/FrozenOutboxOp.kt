package com.savvasg.offlinenotes.data.local

/**
 * A [OutboxEntity] snapshotted at the freeze linearization point (§5), with baseVersion resolved
 * from [NoteEntity.serverVersion] at that instant. Immutable once produced — the payload it was
 * built from cannot change while its op is IN_FLIGHT.
 */
data class FrozenOutboxOp(
    val opId: String,
    val noteId: String,
    val opType: OpType,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)

/** The three merge inputs staged by a CONFLICT apply result (§5, §8). */
data class ConflictPayload(
    val baseVersion: String?,
    val baseTitle: String?,
    val baseBody: String?,
    val remoteVersion: String,
    val remoteTitle: String,
    val remoteBody: String,
)

/** One note's entry in a delta pull page (§6, §7) — a tombstone when [isDeleted] is true. */
data class RemoteNoteChange(
    val noteId: String,
    val version: String,
    val isDeleted: Boolean,
    val title: String?,
    val body: String?,
)
