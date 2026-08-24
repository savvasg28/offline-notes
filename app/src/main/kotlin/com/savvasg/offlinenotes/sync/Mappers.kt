package com.savvasg.offlinenotes.sync

import com.savvasg.offlinenotes.data.local.ConflictPayload
import com.savvasg.offlinenotes.data.local.FrozenOutboxOp
import com.savvasg.offlinenotes.data.local.OpType as LocalOpType
import com.savvasg.offlinenotes.data.local.RemoteNoteChange
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OpType as WireOpType
import com.savvasg.offlinenotes.servercore.OutgoingOp
import com.savvasg.offlinenotes.servercore.RemoteNote

/** `data.local` and `server-core` each define their own copy of this contract (§9) — map between them. */

fun FrozenOutboxOp.toOutgoingOp() = OutgoingOp(
    opId = opId,
    type = opType.toWireOpType(),
    noteId = noteId,
    baseVersion = baseVersion,
    title = title,
    body = body,
)

fun LocalOpType.toWireOpType(): WireOpType = when (this) {
    LocalOpType.CREATE -> WireOpType.CREATE
    LocalOpType.UPDATE -> WireOpType.UPDATE
    LocalOpType.DELETE -> WireOpType.DELETE
}

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
