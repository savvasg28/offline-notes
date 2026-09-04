package com.savvasg.offlinenotes.core.sync.fake

import com.savvasg.offlinenotes.core.network.SyncTransport
import com.savvasg.offlinenotes.core.network.dto.AuthFailureException
import com.savvasg.offlinenotes.core.network.dto.DeltaPage
import com.savvasg.offlinenotes.core.network.dto.OpResult
import com.savvasg.offlinenotes.core.network.dto.OutgoingOp
import java.util.concurrent.atomic.AtomicLong

class FakeTransport : SyncTransport {

    private val versionCounter = AtomicLong(0)
    private var forcedException: (() -> Nothing)? = null
    private val conflictOverrideByNoteId = mutableMapOf<String, OpResult.Conflict>()
    private var nextPullPage: DeltaPage? = null

    override suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> {
        throwForcedIfQueued()
        return ops.map { op ->
            conflictOverrideByNoteId.remove(op.noteId)?.copy(opId = op.opId)
                ?: OpResult.Applied(op.opId, versionCounter.incrementAndGet().toString())
        }
    }

    override suspend fun pullDelta(since: String?): DeltaPage {
        throwForcedIfQueued()
        nextPullPage?.let {
            nextPullPage = null
            return it
        }
        return DeltaPage(notes = emptyList(), nextSyncToken = since ?: "0", hasMore = false)
    }

    private fun throwForcedIfQueued() {
        forcedException?.let {
            forcedException = null
            it()
        }
    }

    fun forceConflictOnNextPush(
        noteId: String,
        remoteTitle: String = "Edited elsewhere",
        remoteBody: String = "This edit came from another device.",
    ) {
        conflictOverrideByNoteId[noteId] = OpResult.Conflict(
            opId = "",
            baseVersion = null,
            baseTitle = null,
            baseBody = null,
            remoteVersion = versionCounter.incrementAndGet().toString(),
            remoteTitle = remoteTitle,
            remoteBody = remoteBody,
        )
    }

    fun forceAuthFailureOnNextCall() {
        forcedException = { throw AuthFailureException() }
    }

    fun queueNextPullPage(page: DeltaPage) {
        nextPullPage = page
    }
}
