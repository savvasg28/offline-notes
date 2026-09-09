package com.savvasg.offlinenotes.domain.model

enum class SyncState { SYNCED, PENDING, CONFLICT }

enum class OpType { CREATE, UPDATE, DELETE }

data class FrozenOutboxOp(
    val opId: String,
    val noteId: String,
    val opType: OpType,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)

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
