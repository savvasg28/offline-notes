package com.savvasg.offlinenotes.domain.model

data class ConflictPayload(
    val baseVersion: String?,
    val baseTitle: String?,
    val baseBody: String?,
    val remoteVersion: String,
    val remoteTitle: String,
    val remoteBody: String,
)
