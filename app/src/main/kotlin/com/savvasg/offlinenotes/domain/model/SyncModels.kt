package com.savvasg.offlinenotes.domain.model

import kotlinx.serialization.Serializable

/** Whether a note's content currently matches the server. Derived from the outbox, not stored independently. */
enum class SyncState { SYNCED, PENDING, CONFLICT }

/**
 * Decided by whether the note has a `serverVersion` yet, never by edit count — a note edited
 * many times before its first successful push is still a CREATE.
 */
@Serializable
enum class OpType { CREATE, UPDATE, DELETE }

/**
 * An outbox op snapshotted at the freeze point, with `baseVersion` resolved from the note's
 * current `serverVersion` at that instant rather than when the op was created — an op created
 * before a prior ack would otherwise ship a stale base and self-conflict.
 */
data class FrozenOutboxOp(
    val opId: String,
    val noteId: String,
    val opType: OpType,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)

/** The base and remote sides of a conflict. Local is the `notes` row itself, so it isn't copied here. */
data class ConflictPayload(
    val baseVersion: String?,
    val baseTitle: String?,
    val baseBody: String?,
    val remoteVersion: String,
    val remoteTitle: String,
    val remoteBody: String,
)

/** One note's entry in a delta pull page — a tombstone when [isDeleted] is true. */
data class RemoteNoteChange(
    val noteId: String,
    val version: String,
    val isDeleted: Boolean,
    val title: String?,
    val body: String?,
)
