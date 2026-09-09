package com.savvasg.offlinenotes.domain.model

data class FrozenOutboxOp(
    val opId: String,
    val noteId: String,
    val opType: OpType,
    val baseVersion: String?,
    val title: String?,
    val body: String?,
)
