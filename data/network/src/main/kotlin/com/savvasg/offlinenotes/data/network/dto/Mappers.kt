package com.savvasg.offlinenotes.data.network.dto

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.model.OpType
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.sync.OpOutcome
import com.savvasg.offlinenotes.domain.sync.RemoteChangePage

fun FrozenOutboxOp.toOutgoingOp() = OutgoingOp(
    opId = opId,
    type = opType.toWire(),
    noteId = noteId,
    baseVersion = baseVersion,
    title = title,
    body = body,
)

fun OpType.toWire() = when (this) {
    OpType.CREATE -> WireOpType.CREATE
    OpType.UPDATE -> WireOpType.UPDATE
    OpType.DELETE -> WireOpType.DELETE
}

fun OpResult.toOpOutcome(): OpOutcome = when (this) {
    is OpResult.Applied -> OpOutcome.Applied(opId, newVersion)
    is OpResult.Rejected -> OpOutcome.Rejected(opId, code, message)
    is OpResult.Conflict -> OpOutcome.Conflicted(
        opId = opId,
        conflict = ConflictPayload(
            baseVersion = baseVersion,
            baseTitle = baseTitle,
            baseBody = baseBody,
            remoteVersion = remoteVersion,
            remoteTitle = remoteTitle,
            remoteBody = remoteBody,
        ),
    )
}

fun DeltaPage.toRemoteChangePage() = RemoteChangePage(
    notes = notes.map { it.toRemoteNoteChange() },
    nextSyncToken = nextSyncToken,
    hasMore = hasMore,
)

fun RemoteNote.toRemoteNoteChange() = RemoteNoteChange(
    noteId = noteId,
    version = version,
    isDeleted = deleted,
    title = title,
    body = body,
)
