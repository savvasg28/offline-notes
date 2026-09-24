package com.savvasg.offlinenotes.domain.sync

import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.domain.model.ConflictPayload
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.repository.OpAcknowledgement
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncEngineTest {

    private val monitor = RecordingMonitor()
    private val transport = ScriptedTransport()

    private fun engine(operations: RecordingSyncOperations) =
        SyncEngine(operations, transport, monitor)

    @Test
    fun `stale in-flight ops are reset before anything is pushed`() = runTest {
        val operations = RecordingSyncOperations()

        engine(operations).sync()

        assertThat(operations.staleResets).isEqualTo(1)
    }

    @Test
    fun `an empty outbox pushes nothing but still pulls`() = runTest {
        val operations = RecordingSyncOperations()

        val outcome = engine(operations).sync()

        assertThat(transport.pushed).isEmpty()
        assertThat(transport.pullCursors).hasSize(1)
        assertThat(outcome).isEqualTo(SyncOutcome.Done)
    }

    @Test
    fun `an applied result is acknowledged with the new version`() = runTest {
        val operations = RecordingSyncOperations(frozen = listOf(op("op-1")))
        transport.pushOutcome = { listOf(OpOutcome.Applied("op-1", "7")) }

        engine(operations).sync()

        assertThat(operations.acknowledgements).containsExactly("op-1" to OpAcknowledgement.Applied("7"))
    }

    @Test
    fun `a conflict is staged rather than acknowledged`() = runTest {
        val operations = RecordingSyncOperations(frozen = listOf(op("op-1")))
        val payload = ConflictPayload(null, null, null, "9", "Theirs", "Their body")
        transport.pushOutcome = { listOf(OpOutcome.Conflicted("op-1", payload)) }

        engine(operations).sync()

        assertThat(operations.stagedConflicts).containsExactly("op-1" to payload)
        assertThat(operations.acknowledgements).isEmpty()
    }

    @Test
    fun `a transport failure stops the round and is reported, not swallowed`() = runTest {
        val operations = RecordingSyncOperations(frozen = listOf(op("op-1")))
        transport.failPushWith = RateLimitException()

        val outcome = engine(operations).sync()

        assertThat(outcome).isEqualTo(SyncOutcome.TransportFailure)
        assertThat(monitor.transportFailures.single()).isInstanceOf(RateLimitException::class.java)
        assertThat(transport.pullCursors).isEmpty()
    }

    @Test
    fun `an auth failure ends the round as AuthFailure, which the worker will not retry`() = runTest {
        val operations = RecordingSyncOperations(frozen = listOf(op("op-1")))
        transport.failPushWith = AuthFailureException()

        val outcome = engine(operations).sync()

        assertThat(outcome).isEqualTo(SyncOutcome.AuthFailure)
        assertThat(monitor.transportFailures.single()).isInstanceOf(AuthFailureException::class.java)
        assertThat(monitor.outcomes).containsExactly(SyncOutcome.AuthFailure)
        assertThat(transport.pullCursors).isEmpty()
    }

    @Test
    fun `every page of a multi-page delta is applied, following the cursor`() = runTest {
        val operations = RecordingSyncOperations(startingToken = "t0")
        transport.pages = mutableListOf(
            RemoteChangePage(listOf(change("a")), nextSyncToken = "t1", hasMore = true),
            RemoteChangePage(listOf(change("b")), nextSyncToken = "t2", hasMore = false),
        )

        engine(operations).sync()

        assertThat(transport.pullCursors).containsExactly("t0", "t1").inOrder()
        assertThat(operations.appliedDeltas.map { it.second }).containsExactly("t1", "t2").inOrder()
    }

    @Test
    fun `an expired token switches to a snapshot pull and reports it`() = runTest {
        val operations = RecordingSyncOperations(startingToken = "stale")
        transport.expireTokenOnce = true
        transport.pages = mutableListOf(
            RemoteChangePage(listOf(change("a")), nextSyncToken = "fresh", hasMore = false),
        )

        engine(operations).sync()

        assertThat(monitor.tokenExpiries).isEqualTo(1)
        assertThat(operations.snapshotPages).hasSize(1)
        assertThat(operations.completedSnapshots).containsExactly(setOf("a") to "fresh")
        assertThat(operations.appliedDeltas).isEmpty()
        assertThat(transport.pullCursors).containsExactly("stale", null).inOrder()
    }

    @Test
    fun `a multi-page snapshot sweeps once, against the union of every page`() = runTest {
        val operations = RecordingSyncOperations(startingToken = "stale")
        transport.expireTokenOnce = true
        transport.pages = mutableListOf(
            RemoteChangePage(listOf(change("a")), nextSyncToken = "cursor-1", hasMore = true),
            RemoteChangePage(listOf(change("b")), nextSyncToken = "cursor-2", hasMore = true),
            RemoteChangePage(listOf(change("c")), nextSyncToken = "fresh", hasMore = false),
        )

        engine(operations).sync()

        assertThat(operations.snapshotPages).hasSize(3)
        assertThat(operations.completedSnapshots)
            .containsExactly(setOf("a", "b", "c") to "fresh")
        assertThat(transport.pullCursors)
            .containsExactly("stale", null, "cursor-1", "cursor-2").inOrder()
    }

    @Test
    fun `an interrupted snapshot never advances the sync token`() = runTest {
        val operations = RecordingSyncOperations(startingToken = "stale")
        transport.expireTokenOnce = true
        transport.pages = mutableListOf(
            RemoteChangePage(listOf(change("a")), nextSyncToken = "cursor-1", hasMore = true),
        )
        transport.failPullAfterPages = 1

        engine(operations).sync()

        assertThat(operations.snapshotPages).hasSize(1)
        assertThat(operations.completedSnapshots).isEmpty()
        assertThat(monitor.outcomes).containsExactly(SyncOutcome.TransportFailure)
    }

    @Test
    fun `residue in the outbox after a round reports MoreWorkPending`() = runTest {
        val operations = RecordingSyncOperations(pendingAfterDrain = true)

        val outcome = engine(operations).sync()

        assertThat(outcome).isEqualTo(SyncOutcome.MoreWorkPending)
        assertThat(monitor.outcomes).containsExactly(SyncOutcome.MoreWorkPending)
    }

    private fun change(id: String) =
        RemoteNoteChange(id, version = "1", isDeleted = false, title = "T", body = "B")
}
