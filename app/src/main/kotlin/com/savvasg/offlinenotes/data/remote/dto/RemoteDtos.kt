package com.savvasg.offlinenotes.data.remote.dto

import com.savvasg.offlinenotes.data.remote.SyncTransport
import com.savvasg.offlinenotes.domain.model.OpType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire contract [SyncTransport] implementations speak. This repo is a client against that
 * contract; nothing here implements the server side of it.
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
 * `@SerialName` pins each variant's wire discriminator to a short, stable string. Without it
 * kotlinx.serialization defaults to the fully-qualified Kotlin class name, which would leak package
 * structure into the wire format and break the moment a class is renamed or moved.
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

/** The cursor no longer reaches far enough back into history; the client re-bootstraps. */
class SyncTokenExpiredException : Exception("sync token expired; client must clear it and re-bootstrap")

/** Transport-level failures: retry the whole batch with the same opIds. */
sealed class TransportException(message: String) : Exception(message)
class AuthFailureException : TransportException("auth failure")
class RateLimitException : TransportException("rate limited")
class NetworkException(message: String) : TransportException(message)
