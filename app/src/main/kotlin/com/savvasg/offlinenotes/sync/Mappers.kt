package com.savvasg.offlinenotes.sync

import com.savvasg.offlinenotes.data.local.ConflictPayload
import com.savvasg.offlinenotes.data.local.FrozenOutboxOp
import com.savvasg.offlinenotes.data.local.RemoteNoteChange
import com.savvasg.offlinenotes.sync.transport.OpResult
import com.savvasg.offlinenotes.sync.transport.OutgoingOp
import com.savvasg.offlinenotes.sync.transport.RemoteNote

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
