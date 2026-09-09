package com.savvasg.offlinenotes.domain.sync

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange

interface SyncTransport {
    suspend fun pushOps(ops: List<FrozenOutboxOp>): List<OpOutcome>

    suspend fun pullDelta(since: String?): RemoteChangePage
}

sealed interface OpOutcome {
    val opId: String

    data class Applied(override val opId: String, val newVersion: String) : OpOutcome

    data class Conflicted(override val opId: String, val conflict: ConflictPayload) : OpOutcome

    data class Rejected(override val opId: String, val code: String, val message: String) : OpOutcome
}

data class RemoteChangePage(
    val notes: List<RemoteNoteChange>,
    val nextSyncToken: String,
    val hasMore: Boolean,
)

class SyncTokenExpiredException : Exception("sync token expired; client must clear it and re-bootstrap")

sealed class TransportException(message: String) : Exception(message)

class AuthFailureException : TransportException("auth failure")

class RateLimitException : TransportException("rate limited")

class NetworkException(message: String) : TransportException(message)
