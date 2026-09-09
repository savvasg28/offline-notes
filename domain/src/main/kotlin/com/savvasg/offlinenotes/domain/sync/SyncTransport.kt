package com.savvasg.offlinenotes.domain.sync

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange

/**
 * What the sync engine needs from a server, stated in this app's own terms. Implementations own
 * the wire format entirely — nothing about JSON, HTTP or status codes appears here.
 */
interface SyncTransport {
    suspend fun pushOps(ops: List<FrozenOutboxOp>): List<OpOutcome>

    suspend fun pullDelta(since: String?): RemoteChangePage
}

/** Per-op result of a push. Independent of every other op in the same batch. */
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

/**
 * The cursor the server no longer recognises: the client must drop it and re-bootstrap. Not a
 * [TransportException] — it's a valid answer that demands a different pull, not a failed call.
 */
class SyncTokenExpiredException : Exception("sync token expired; client must clear it and re-bootstrap")

/** Call-level failures. The whole batch is retried later with the same opIds. */
sealed class TransportException(message: String) : Exception(message)

class AuthFailureException : TransportException("auth failure")

class RateLimitException : TransportException("rate limited")

class NetworkException(message: String) : TransportException(message)
