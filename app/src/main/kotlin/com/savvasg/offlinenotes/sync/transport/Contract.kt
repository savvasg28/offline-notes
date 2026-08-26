package com.savvasg.offlinenotes.sync.transport

import com.savvasg.offlinenotes.data.local.OpType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire contract [SyncTransport] implementations speak. This repo doesn't own backend
 * semantics — the app is a client against a contract, full stop. These types describe that
 * contract; nothing in this module implements it. [FakeTransport][com.savvasg.offlinenotes.sync.transport.fake.FakeTransport]
 * is a scriptable stub for local dev/tests, and `HttpTransport` talks to whatever real (or
 * Mockoon-mocked) server honours this shape.
 */
@Serializable
data class OutgoingOp(
    val opId: String,
    val type: OpType,
    val noteId: String,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)

/**
 * Per-op push result. Conflict and Rejected are results, not transport errors.
 *
 * `@SerialName` on each variant pins the wire discriminator to a short, stable string —
 * without it, kotlinx.serialization defaults to the fully-qualified Kotlin class name, which
 * would leak internal package structure into the HTTP wire format and break the moment a class
 * gets renamed or moved.
 */
@Serializable
sealed interface OpResult {
    val opId: String

    @Serializable
    @SerialName("Applied")
    data class Applied(override val opId: String, val newVersion: String) : OpResult

    @Serializable
    @SerialName("Conflict")
    data class Conflict(
        override val opId: String,
        val baseVersion: String?,
        val baseTitle: String?,
        val baseBody: String?,
        val remoteVersion: String,
        val remoteTitle: String,
        val remoteBody: String,
    ) : OpResult

    @Serializable
    @SerialName("Rejected")
    data class Rejected(override val opId: String, val code: String, val message: String) : OpResult
}

/** One note's entry in a delta page — a tombstone when [deleted] is true. */
@Serializable
data class RemoteNote(
    val noteId: String,
    val version: String,
    val deleted: Boolean,
    val title: String?,
    val body: String?,
)

@Serializable
data class DeltaPage(
    val notes: List<RemoteNote>,
    val nextSyncToken: String,
    val hasMore: Boolean,
)

/** syncToken no longer reaches far enough back into history — client clears it and re-bootstraps. */
class SyncTokenExpiredException : Exception("sync token expired; client must clear it and re-bootstrap")

/** Transport-level failures: retry the whole batch with the same opIds. */
sealed class TransportException(message: String) : Exception(message)
class AuthFailureException : TransportException("auth failure")
class RateLimitException : TransportException("rate limited")
class SimulatedNetworkException(message: String) : TransportException(message)
