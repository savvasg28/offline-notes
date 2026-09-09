package com.savvasg.offlinenotes.data.testing

import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.sync.AuthFailureException
import com.savvasg.offlinenotes.domain.sync.OpOutcome
import com.savvasg.offlinenotes.domain.sync.RemoteChangePage
import com.savvasg.offlinenotes.domain.sync.SyncTransport
import java.util.concurrent.atomic.AtomicLong

class FakeTransport : SyncTransport {

    private val versionCounter = AtomicLong(0)
    private var forcedException: (() -> Nothing)? = null
    private val conflictOverrideByNoteId = mutableMapOf<String, ConflictPayload>()
    private var nextPullPage: RemoteChangePage? = null

    override suspend fun push(ops: List<FrozenOutboxOp>): List<OpOutcome> {
        throwForcedIfQueued()
        return ops.map { op ->
            conflictOverrideByNoteId.remove(op.noteId)
                ?.let { OpOutcome.Conflicted(op.opId, it) }
                ?: OpOutcome.Applied(op.opId, versionCounter.incrementAndGet().toString())
        }
    }

    override suspend fun pull(since: String?): RemoteChangePage {
        throwForcedIfQueued()
        nextPullPage?.let {
            nextPullPage = null
            return it
        }
        return RemoteChangePage(notes = emptyList(), nextSyncToken = since ?: "0", hasMore = false)
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
        conflictOverrideByNoteId[noteId] = ConflictPayload(
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

    fun queueNextPullPage(page: RemoteChangePage) {
        nextPullPage = page
    }
}
