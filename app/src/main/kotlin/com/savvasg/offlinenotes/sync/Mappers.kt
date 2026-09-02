package com.savvasg.offlinenotes.sync

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
import com.savvasg.offlinenotes.data.remote.dto.RemoteNote

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
