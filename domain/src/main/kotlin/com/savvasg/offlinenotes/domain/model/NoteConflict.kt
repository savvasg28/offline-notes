package com.savvasg.offlinenotes.domain.model

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
