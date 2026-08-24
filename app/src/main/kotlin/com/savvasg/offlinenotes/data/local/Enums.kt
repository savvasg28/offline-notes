package com.savvasg.offlinenotes.data.local

/** Derived from the outbox: whether [NoteEntity] content currently matches the server. */
enum class SyncState { SYNCED, PENDING, CONFLICT }

/**
 * Determined by whether [NoteEntity.serverVersion] is null at write time, not by edit count —
 * a note edited many times before its first successful push is still a CREATE.
 */
enum class OpType { CREATE, UPDATE, DELETE }

enum class OutboxStatus { PENDING, IN_FLIGHT, PARKED }
