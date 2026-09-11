package com.savvasg.offlinenotes.domain.model

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val syncState: SyncState,
    val isDeleted: Boolean,
    val createdAt: Long,
    val lastUpdated: Long,
    val rejection: RejectionReason? = null,
)

data class RejectionReason(
    val code: String,
    val message: String,
)

data class NoteSummary(
    val id: String,
    val title: String,
    val bodySnippet: String,
    val lastUpdated: Long,
    val syncState: SyncState,
)
