package com.savvasg.offlinenotes.domain.model

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val syncState: SyncState,
    val isDeleted: Boolean,
    val createdAt: Long,
    val lastUpdated: Long,
)

/** List-row projection: the list screen never observes full bodies. */
data class NoteListItem(
    val id: String,
    val title: String,
    val bodySnippet: String,
    val lastUpdated: Long,
    val syncState: SyncState,
)
