package com.savvasg.offlinenotes.domain.model

import kotlinx.serialization.Serializable

enum class SyncState { SYNCED, PENDING, CONFLICT }

/** Decided by whether the note has a `serverVersion` yet, never by edit count. */
@Serializable
enum class OpType { CREATE, UPDATE, DELETE }

/** `baseVersion` is resolved at the freeze point, not when the op was created. */
data class FrozenOutboxOp(
    val opId: String,
    val noteId: String,
    val opType: OpType,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)

/** Base and remote sides of a conflict; local is the `notes` row itself, so it is not copied here. */
data class ConflictPayload(
    val baseVersion: String?,
    val baseTitle: String?,
    val baseBody: String?,
    val remoteVersion: String,
    val remoteTitle: String,
    val remoteBody: String,
)

data class RemoteNoteChange(
    val noteId: String,
    val version: String,
    val isDeleted: Boolean,
    val title: String?,
    val body: String?,
)
