package com.savvasg.offlinenotes.data.local

import kotlinx.serialization.Serializable

/** Derived from the outbox: whether [NoteEntity] content currently matches the server. */
enum class SyncState { SYNCED, PENDING, CONFLICT }

/**
 * Determined by whether [NoteEntity.serverVersion] is null at write time, not by edit count —
 * a note edited many times before its first successful push is still a CREATE. Also the wire
 * enum for [com.savvasg.offlinenotes.sync.transport.OutgoingOp] — this repo doesn't own backend
 * semantics (§ Rejected alternatives), so there's no separate server-side copy to keep in sync.
 */
@Serializable
enum class OpType { CREATE, UPDATE, DELETE }

enum class OutboxStatus { PENDING, IN_FLIGHT, PARKED }
