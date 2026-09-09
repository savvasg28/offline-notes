package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.model.ConflictPayload
import com.savvasg.offlinenotes.core.model.FrozenOutboxOp
import com.savvasg.offlinenotes.core.model.RemoteNoteChange
import com.savvasg.offlinenotes.core.network.dto.OpResult
import com.savvasg.offlinenotes.core.network.dto.OutgoingOp
import com.savvasg.offlinenotes.core.network.dto.RemoteNote

fun FrozenOutboxOp.toOutgoingOp() = OutgoingOp(
    opId = opId,
    type = opType,
    noteId = noteId,
    baseVersion = baseVersion,
    title = title,
    body = body,
)

fun OpResult.Conflict.toConflictPayload() = ConflictPayload(
    baseVersion = baseVersion,
    baseTitle = baseTitle,
    baseBody = baseBody,
    remoteVersion = remoteVersion,
    remoteTitle = remoteTitle,
    remoteBody = remoteBody,
)

fun RemoteNote.toRemoteNoteChange() = RemoteNoteChange(
    noteId = noteId,
    version = version,
    isDeleted = deleted,
    title = title,
    body = body,
)
