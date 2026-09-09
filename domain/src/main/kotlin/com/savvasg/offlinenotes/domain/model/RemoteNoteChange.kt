package com.savvasg.offlinenotes.domain.model

data class RemoteNoteChange(
    val noteId: String,
    val version: String,
    val isDeleted: Boolean,
    val title: String?,
    val body: String?,
)
