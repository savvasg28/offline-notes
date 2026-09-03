package com.savvasg.offlinenotes.data.remote.fake

import com.savvasg.offlinenotes.data.remote.dto.AuthFailureException
import com.savvasg.offlinenotes.data.remote.dto.DeltaPage
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
import com.savvasg.offlinenotes.data.remote.dto.RateLimitException
import com.savvasg.offlinenotes.data.remote.dto.SimulatedNetworkException
import com.savvasg.offlinenotes.data.remote.dto.SyncTokenExpiredException
import com.savvasg.offlinenotes.data.remote.SyncTransport
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * A scriptable stub, not a simulated server: no CAS, no persistence, no dedupe. Every push defaults
 * to `Applied` with a freshly-minted version, and the debug panel's controls queue one-shot
 * overrides on top of that — the same shape a Mockoon rule set has over real HTTP.
 */
@Singleton
class FakeTransport @Inject constructor() : SyncTransport {

    private val versionCounter = AtomicLong(0)
    private var latencyRangeMillis: LongRange? = null
    private var forcedException: (() -> Nothing)? = null
    private val conflictOverrideByNoteId = mutableMapOf<String, OpResult.Conflict>()
    private val rejectedOverrideByNoteId = mutableMapOf<String, OpResult.Rejected>()
    private val dropResponseForOpIds = mutableSetOf<String>()
    private var tokenExpiredOnNextPull = false
    private var nextPullPage: DeltaPage? = null

    override suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> {
        maybeDelay()
        maybeThrowForced()
        if (ops.any { it.opId in dropResponseForOpIds }) {
            ops.forEach { dropResponseForOpIds.remove(it.opId) }
            throw SimulatedNetworkException("dropped response (debug panel)")
        }
        return ops.map { op ->
            conflictOverrideByNoteId.remove(op.noteId)?.copy(opId = op.opId)
                ?: rejectedOverrideByNoteId.remove(op.noteId)?.copy(opId = op.opId)
                ?: OpResult.Applied(op.opId, versionCounter.incrementAndGet().toString())
        }
    }

    override suspend fun pullDelta(since: String?): DeltaPage {
        maybeDelay()
        maybeThrowForced()
        if (tokenExpiredOnNextPull) {
            tokenExpiredOnNextPull = false
            throw SyncTokenExpiredException()
        }
        nextPullPage?.let {
            nextPullPage = null
            return it
        }
        return DeltaPage(notes = emptyList(), nextSyncToken = since ?: "0", hasMore = false)
    }

    private suspend fun maybeDelay() {
        latencyRangeMillis?.let { delay(it.random()) }
    }

    private fun maybeThrowForced() {
        forcedException?.let {
            forcedException = null
            it()
        }
    }

    fun setLatencyMillis(range: LongRange?) {
        latencyRangeMillis = range
    }

    fun forceConflictOnNextPush(
        noteId: String,
        remoteTitle: String = "Edited by Device B",
        remoteBody: String = "This edit came from a simulated second device.",
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

    fun forceRejectedOnNextPush(noteId: String, code: String, message: String) {
        rejectedOverrideByNoteId[noteId] = OpResult.Rejected(opId = "", code = code, message = message)
    }

    fun dropNextResponseFor(opId: String) {
        dropResponseForOpIds.add(opId)
    }

    fun expireTokenOnNextPull() {
        tokenExpiredOnNextPull = true
    }

    fun forceRateLimitOnNextCall() {
        forcedException = { throw RateLimitException() }
    }

    fun forceAuthFailureOnNextCall() {
        forcedException = { throw AuthFailureException() }
    }

    /** Not wired to the debug panel UI; used by tests that exercise the pull path directly. */
    fun queueNextPullPage(page: DeltaPage) {
        nextPullPage = page
    }
}
