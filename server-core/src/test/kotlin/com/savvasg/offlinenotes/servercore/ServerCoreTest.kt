package com.savvasg.offlinenotes.servercore

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ServerCoreTest {

    private fun createOp(opId: String = "op-1", noteId: String = "note-1", title: String = "T", body: String = "B") =
        OutgoingOp(opId, OpType.CREATE, noteId, baseVersion = null, title = title, body = body)

    @Test
    fun `CREATE against a new note applies and returns version 1`() = runTest {
        val server = ServerCore()
        val result = server.pushOps(listOf(createOp())).single()
        assertThat(result).isInstanceOf(OpResult.Applied::class.java)
        assertThat((result as OpResult.Applied).newVersion).isEqualTo("1")
    }

    @Test
    fun `UPDATE with the correct baseVersion applies and bumps the version`() = runTest {
        val server = ServerCore()
        val created = server.pushOps(listOf(createOp())).single() as OpResult.Applied

        val update = OutgoingOp("op-2", OpType.UPDATE, "note-1", created.newVersion, "T2", "B2")
        val result = server.pushOps(listOf(update)).single()

        assertThat(result).isInstanceOf(OpResult.Applied::class.java)
        assertThat((result as OpResult.Applied).newVersion).isEqualTo("2")
    }

    @Test
    fun `UPDATE with a stale baseVersion returns Conflict carrying local, base, and remote`() = runTest {
        val server = ServerCore()
        server.pushOps(listOf(createOp()))
        server.simulateConcurrentEdit("note-1", "Device B Title", "Device B Body")

        val stale = OutgoingOp("op-2", OpType.UPDATE, "note-1", baseVersion = "1", title = "My Edit", body = "My Body")
        val result = server.pushOps(listOf(stale)).single()

        assertThat(result).isInstanceOf(OpResult.Conflict::class.java)
        val conflict = result as OpResult.Conflict
        assertThat(conflict.remoteVersion).isEqualTo("2")
        assertThat(conflict.remoteTitle).isEqualTo("Device B Title")
        assertThat(conflict.baseVersion).isEqualTo("1")
        assertThat(conflict.baseTitle).isEqualTo("T") // reconstructed from the change log
    }

    @Test
    fun `replaying the same opId returns the stored result without re-executing`() = runTest {
        val server = ServerCore()
        val op = createOp()
        val first = server.pushOps(listOf(op)).single() as OpResult.Applied
        val second = server.pushOps(listOf(op)).single() as OpResult.Applied

        assertThat(second.newVersion).isEqualTo(first.newVersion)
        // A second CREATE (had it re-executed) would have seen an existing note and conflicted.
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `one conflicting op in a batch does not fail the other ops`() = runTest {
        val server = ServerCore()
        server.pushOps(listOf(createOp(noteId = "note-1")))
        server.simulateConcurrentEdit("note-1", "Remote", "Remote")

        val results = server.pushOps(
            listOf(
                OutgoingOp("op-conflict", OpType.UPDATE, "note-1", "1", "x", "y"),
                createOp(opId = "op-ok", noteId = "note-2"),
            ),
        )

        assertThat(results[0]).isInstanceOf(OpResult.Conflict::class.java)
        assertThat(results[1]).isInstanceOf(OpResult.Applied::class.java)
    }

    @Test
    fun `forceConflictOnNextPush produces a Conflict result even with a matching baseVersion`() = runTest {
        val server = ServerCore()
        val created = server.pushOps(listOf(createOp())).single() as OpResult.Applied
        server.forceConflictOnNextPush("note-1")

        val op = OutgoingOp("op-2", OpType.UPDATE, "note-1", created.newVersion, "T2", "B2")
        val result = server.pushOps(listOf(op)).single()

        assertThat(result).isInstanceOf(OpResult.Conflict::class.java)
    }

    @Test
    fun `dropNextResponseFor applies the op but throws instead of returning a result`() = runTest {
        val server = ServerCore()
        val op = createOp()
        server.dropNextResponseFor(op.opId)

        try {
            server.pushOps(listOf(op))
            throw AssertionError("expected SimulatedNetworkException")
        } catch (expected: SimulatedNetworkException) {
            // expected
        }

        // The op was still applied server-side — a client retry with the same opId replays cleanly.
        val replay = server.pushOps(listOf(op)).single()
        assertThat(replay).isInstanceOf(OpResult.Applied::class.java)
    }

    @Test
    fun `forceAuthFailureOnNextCall and forceRateLimitOnNextCall throw before touching state`() = runTest {
        val server = ServerCore()
        server.forceAuthFailureOnNextCall()
        try {
            server.pushOps(listOf(createOp()))
            throw AssertionError("expected AuthFailureException")
        } catch (expected: AuthFailureException) {
            // expected
        }
        assertThat(server.pullDelta(null).notes).isEmpty()

        server.forceRateLimitOnNextCall()
        try {
            server.pushOps(listOf(createOp()))
            throw AssertionError("expected RateLimitException")
        } catch (expected: RateLimitException) {
            // expected
        }
    }

    @Test
    fun `bootstrap pull returns a full snapshot of current notes`() = runTest {
        val server = ServerCore()
        server.pushOps(listOf(createOp(noteId = "note-1"), createOp(opId = "op-2", noteId = "note-2")))

        val page = server.pullDelta(since = null)

        assertThat(page.notes).hasSize(2)
        assertThat(page.hasMore).isFalse()
    }

    @Test
    fun `incremental pull returns only changes after the given token`() = runTest {
        val server = ServerCore()
        val page0 = server.pullDelta(null)
        server.pushOps(listOf(createOp(noteId = "note-1")))

        val page1 = server.pullDelta(page0.nextSyncToken)

        assertThat(page1.notes).hasSize(1)
        assertThat(page1.notes.single().noteId).isEqualTo("note-1")
    }

    @Test
    fun `a tombstone is delivered with deleted true and no content`() = runTest {
        val server = ServerCore()
        val created = server.pushOps(listOf(createOp())).single() as OpResult.Applied
        val page0 = server.pullDelta(null)
        server.pushOps(listOf(OutgoingOp("op-2", OpType.DELETE, "note-1", created.newVersion, null, null)))

        val page1 = server.pullDelta(page0.nextSyncToken)

        val tombstone = page1.notes.single()
        assertThat(tombstone.deleted).isTrue()
        assertThat(tombstone.title).isNull()
        assertThat(tombstone.body).isNull()
    }

    @Test
    fun `expireTokenOnNextPull makes the next non-null pull throw`() = runTest {
        val server = ServerCore()
        server.expireTokenOnNextPull()
        try {
            server.pullDelta(since = "1")
            throw AssertionError("expected SyncTokenExpiredException")
        } catch (expected: SyncTokenExpiredException) {
            // expected
        }
        // Only the next call is affected; a bootstrap (null) right after works normally.
        server.pullDelta(since = null)
    }

    @Test
    fun `retention trimming makes an old-enough token expire on its own`() = runTest {
        val server = ServerCore(changeLogRetention = 2)
        val page0 = server.pullDelta(null)
        repeat(5) { i -> server.pushOps(listOf(createOp(opId = "op-$i", noteId = "note-$i"))) }

        try {
            server.pullDelta(page0.nextSyncToken)
            throw AssertionError("expected SyncTokenExpiredException")
        } catch (expected: SyncTokenExpiredException) {
            // retention (2 entries) no longer reaches back to the token from before the writes.
        }
    }
}
