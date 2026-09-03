package com.savvasg.offlinenotes.domain.model

/**
 * [baseTitle]/[baseBody] are null when the server's history no longer reaches [baseVersion]; the
 * UI degrades to a two-way local-vs-remote view in that case.
 */
data class NoteConflict(
    val noteId: String,
    val localTitle: String,
    val localBody: String,
    val baseVersion: String?,
    val baseTitle: String?,
    val baseBody: String?,
    val remoteVersion: String,
    val remoteTitle: String,
    val remoteBody: String,
)
