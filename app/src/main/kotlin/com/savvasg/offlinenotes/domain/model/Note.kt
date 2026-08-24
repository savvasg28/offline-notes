package com.savvasg.offlinenotes.domain.model

import com.savvasg.offlinenotes.data.local.SyncState

/** Full note, for the editor screen. */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val syncState: SyncState,
    val isDeleted: Boolean,
    val createdAt: Long,
    val lastUpdated: Long,
)

/**
 * List-row projection (§10): the list screen never observes full bodies, only this. Matches the
 * `(isDeleted, lastUpdated DESC)` index (§4) that backs the underlying query.
 */
data class NoteListItem(
    val id: String,
    val title: String,
    val bodySnippet: String,
    val lastUpdated: Long,
    val syncState: SyncState,
)
