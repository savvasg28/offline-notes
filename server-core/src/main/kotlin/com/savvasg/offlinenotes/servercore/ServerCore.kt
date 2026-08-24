package com.savvasg.offlinenotes.servercore

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class ServerNote(
    val noteId: String,
    val version: Long,
    val title: String,
    val body: String,
    val deleted: Boolean,
)

private data class ChangeLogEntry(
    val seq: Long,
    val noteId: String,
    val version: Long,
    val deleted: Boolean,
    val title: String,
    val body: String,
)

/**
 * One implementation of server semantics (§9): per-note version counters with CAS on
 * baseVersion, an opId -> result dedupe table, a change log with cursor pagination and bounded
 * retention (so token expiry is reachable), and the failure-injection switches the debug panel
 * and scenario tests drive. `FakeTransport` calls this directly in-process; the `:server` Ktor
 * module wraps it in HTTP routes — both exercise identical semantics by construction.
 *
 * State is in-memory only, guarded by a single [Mutex] — this is a demo server, not a scalable
 * one; correctness under the documented contract matters far more than throughput here.
 */
class ServerCore(private val changeLogRetention: Int = 200) {
    private val mutex = Mutex()
    private val notes = mutableMapOf<String, ServerNote>()
    private val changeLog = ArrayDeque<ChangeLogEntry>()
    private var nextSeq = 1L
    private val opResults = mutableMapOf<String, OpResult>()

    // --- failure injection state (§9) ---
    private var latencyMillisRange: LongRange? = null
    private val forceConflictNoteIds = mutableSetOf<String>()
    private val dropResponseOpIds = mutableSetOf<String>()
    private var rejectNextOpForNoteId: Pair<String, Pair<String, String>>? = null
    private var rateLimitOnNextCall = false
    private var authFailureOnNextCall = false
    private var tokenExpiredOnNextPull = false

    suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> {
        maybeDelay()
        if (authFailureOnNextCall) {
            authFailureOnNextCall = false
            throw AuthFailureException()
        }
        if (rateLimitOnNextCall) {
            rateLimitOnNextCall = false
            throw RateLimitException()
        }

        val results = mutex.withLock { ops.map { processOpLocked(it) } }

        val dropped = ops.map { it.opId }.any { it in dropResponseOpIds }
        if (dropped) {
            ops.forEach { dropResponseOpIds.remove(it.opId) }
            throw SimulatedNetworkException("response dropped after applying")
        }
        return results
    }

    suspend fun pullDelta(since: String?): DeltaPage {
        maybeDelay()
        if (since != null && tokenExpiredOnNextPull) {
            tokenExpiredOnNextPull = false
            throw SyncTokenExpiredException()
        }
        return mutex.withLock { computeDeltaLocked(since) }
    }

    // --- failure injection controls, driven by the debug panel / scenario tests (§9) ---

    fun setLatencyMillis(range: LongRange?) {
        latencyMillisRange = range
    }

    fun forceConflictOnNextPush(noteId: String) {
        forceConflictNoteIds += noteId
    }

    fun dropNextResponseFor(opId: String) {
        dropResponseOpIds += opId
    }

    fun forceRejectedOnNextPush(noteId: String, code: String, message: String) {
        rejectNextOpForNoteId = noteId to (code to message)
    }

    fun expireTokenOnNextPull() {
        tokenExpiredOnNextPull = true
    }

    fun forceRateLimitOnNextCall() {
        rateLimitOnNextCall = true
    }

    fun forceAuthFailureOnNextCall() {
        authFailureOnNextCall = true
    }

    /** "Device B" writing directly to server state — the only way to produce a genuine conflict. */
    suspend fun simulateConcurrentEdit(noteId: String, title: String, body: String) {
        mutex.withLock {
            val existing = notes[noteId]
            val newVersion = (existing?.version ?: 0L) + 1
            notes[noteId] = ServerNote(noteId, newVersion, title, body, deleted = false)
            appendChangeLogLocked(noteId, newVersion, deleted = false, title = title, body = body)
        }
    }

    suspend fun simulateConcurrentDelete(noteId: String) {
        mutex.withLock {
            val existing = notes[noteId] ?: return@withLock
            val newVersion = existing.version + 1
            notes[noteId] = existing.copy(version = newVersion, deleted = true)
            appendChangeLogLocked(noteId, newVersion, deleted = true, title = existing.title, body = existing.body)
        }
    }

    // --- internals ---

    private suspend fun maybeDelay() {
        latencyMillisRange?.let { range -> delay(range.random()) }
    }

    private fun processOpLocked(op: OutgoingOp): OpResult {
        opResults[op.opId]?.let { return it } // idempotent replay — never re-executed

        val existing = notes[op.noteId]
        val forcedConflict = forceConflictNoteIds.remove(op.noteId) && existing != null
        val forcedRejection = rejectNextOpForNoteId?.takeIf { it.first == op.noteId }
            ?.also { rejectNextOpForNoteId = null }

        val result: OpResult = when {
            forcedRejection != null ->
                OpResult.Rejected(op.opId, forcedRejection.second.first, forcedRejection.second.second)
            forcedConflict -> conflictResult(op, existing!!)
            op.type == OpType.CREATE ->
                if (existing == null) applyLocked(op, baseVersion = 0L) else conflictResult(op, existing)
            existing == null -> OpResult.Rejected(op.opId, "NOT_FOUND", "note ${op.noteId} does not exist")
            op.baseVersion != existing.version.toString() -> conflictResult(op, existing)
            else -> applyLocked(op, baseVersion = existing.version)
        }
        opResults[op.opId] = result
        return result
    }

    private fun applyLocked(op: OutgoingOp, baseVersion: Long): OpResult.Applied {
        val newVersion = baseVersion + 1
        val deleted = op.type == OpType.DELETE
        val title = if (deleted) "" else op.title.orEmpty()
        val body = if (deleted) "" else op.body.orEmpty()
        notes[op.noteId] = ServerNote(op.noteId, newVersion, title, body, deleted)
        appendChangeLogLocked(op.noteId, newVersion, deleted, title, body)
        return OpResult.Applied(op.opId, newVersion.toString())
    }

    private fun conflictResult(op: OutgoingOp, existing: ServerNote): OpResult.Conflict {
        val base = op.baseVersion?.toLongOrNull()?.let { v -> changeLog.find { it.noteId == op.noteId && it.version == v } }
        return OpResult.Conflict(
            opId = op.opId,
            baseVersion = op.baseVersion,
            baseTitle = base?.title,
            baseBody = base?.body,
            remoteVersion = existing.version.toString(),
            remoteTitle = existing.title,
            remoteBody = existing.body,
        )
    }

    private fun appendChangeLogLocked(noteId: String, version: Long, deleted: Boolean, title: String, body: String) {
        changeLog.addLast(ChangeLogEntry(nextSeq++, noteId, version, deleted, title, body))
        while (changeLog.size > changeLogRetention) changeLog.removeFirst()
    }

    /**
     * `since == null` bootstraps: a snapshot of every current note, one page (the note counts a
     * demo app deals in don't warrant real pagination here; see `:server`'s README section on
     * rejected complexity for the same call made elsewhere). `since != null` walks the change log
     * from that cursor, in [PAGE_SIZE] pages, throwing [SyncTokenExpiredException] if retention
     * has trimmed past it.
     */
    private fun computeDeltaLocked(since: String?): DeltaPage {
        if (since == null) {
            val snapshot = notes.values.map { it.toRemoteNote() }
            return DeltaPage(snapshot, nextSyncToken = (nextSeq - 1).toString(), hasMore = false)
        }

        val sinceSeq = since.toLongOrNull() ?: 0L
        val earliestAvailableSeq = changeLog.firstOrNull()?.seq ?: nextSeq
        if (sinceSeq < earliestAvailableSeq - 1) throw SyncTokenExpiredException()

        val page = changeLog.filter { it.seq > sinceSeq }.sortedBy { it.seq }.take(PAGE_SIZE)
        val hasMore = changeLog.any { it.seq > (page.lastOrNull()?.seq ?: sinceSeq) }
        val nextToken = (page.lastOrNull()?.seq ?: sinceSeq).toString()
        return DeltaPage(page.map { it.toRemoteNote() }, nextToken, hasMore)
    }

    private fun ServerNote.toRemoteNote() =
        RemoteNote(noteId, version.toString(), deleted, if (deleted) null else title, if (deleted) null else body)

    private fun ChangeLogEntry.toRemoteNote() =
        RemoteNote(noteId, version.toString(), deleted, if (deleted) null else title, if (deleted) null else body)

    private companion object {
        const val PAGE_SIZE = 50
    }
}
