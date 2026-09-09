package com.savvasg.offlinenotes.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WireOpType { CREATE, UPDATE, DELETE }

@Serializable
data class OutgoingOp(
    val opId: String,
    val type: WireOpType,
    val noteId: String,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)

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
