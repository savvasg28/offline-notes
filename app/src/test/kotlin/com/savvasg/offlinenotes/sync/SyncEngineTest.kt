package com.savvasg.offlinenotes.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.data.local.NoteTransactions
import com.savvasg.offlinenotes.data.local.OfflineNotesDatabase
import com.savvasg.offlinenotes.data.local.OutboxStatus
import com.savvasg.offlinenotes.data.local.SyncState
import com.savvasg.offlinenotes.data.repository.NoteRepositoryImpl
import com.savvasg.offlinenotes.sync.transport.DeltaPage
import com.savvasg.offlinenotes.sync.transport.OpResult
import com.savvasg.offlinenotes.sync.transport.RemoteNote
import com.savvasg.offlinenotes.sync.transport.fake.FakeTransport
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests wiring the real [NoteRepositoryImpl] + Room (schema only — see
 * NoteTransactionsTest for why FTS5 is excluded here) to a scripted [FakeTransport], driven
 * through [SyncEngine]. No Hilt: everything is small enough to construct directly, which keeps
 * these tests fast and free of any framework magic to reason around.
 *
 * This repo doesn't own backend semantics (§ Rejected alternatives) — [FakeTransport] is a
 * stateless stub, not a simulated server — so these tests script exactly the responses each
 * scenario needs (`forceConflictOnNextPush`, `queueNextPullPage`, ...) rather than driving real
 * server-side CAS/version state.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var db: OfflineNotesDatabase
    private lateinit var repository: NoteRepositoryImpl
    private lateinit var transport: FakeTransport
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OfflineNotesDatabase::class.java)
            .addCallback(OfflineNotesDatabase.SCHEMA_CALLBACK)
            .allowMainThreadQueries()
            .build()
        val transactions = NoteTransactions(db, db.noteDao(), db.outboxDao(), db.conflictDao(), db.syncMetaDao())
        // SyncScheduler just wraps WorkManager scheduling — irrelevant to these tests, which
        // drive SyncEngine.sync() directly rather than through the trigger layer.
        repository = NoteRepositoryImpl(
            db.noteDao(),
            db.outboxDao(),
            db.conflictDao(),
            db.syncMetaDao(),
            transactions,
            mockk(relaxed = true),
        )
        transport = FakeTransport()
        engine = SyncEngine(repository, transport)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a new note pushes, applies, and lands SYNCED`() = runTest {
        repository.saveNote("note-1", "Title", "Body")

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        val note = repository.observeNote("note-1").first()!!
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(db.outboxDao().countForNote("note-1")).isEqualTo(0)
    }

    @Test
    fun `crash_between_send_and_ack_replays_safely - end to end through the engine`() = runTest {
        repository.saveNote("note-1", "T", "B")

        // The first attempt: freeze + push succeed (server applies), but the process dies before
        // the local apply txn runs — the ack is never processed.
        val frozen = repository.freezeAndDrain()
        val opId = frozen.single().opId
        val pushed = transport.pushOps(frozen.map { it.toOutgoingOp() })
        assertThat(pushed.single()).isInstanceOf(OpResult.Applied::class.java)

        assertThat(db.outboxDao().getById(opId)!!.status).isEqualTo(OutboxStatus.IN_FLIGHT)

        // App restarts; engine.sync() resets the stale IN_FLIGHT op and re-freezes it — same
        // opId (proven at the transaction layer by NoteTransactionsTest's crash-recovery test;
        // this repo doesn't own backend semantics, so there's no server-side dedupe to assert
        // "same version" against — what's the client's to guarantee is that it replays the
        // identical op rather than fabricating a new one).
        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        assertThat(db.outboxDao().getById(opId)).isNull()
        assertThat(repository.observeNote("note-1").first()!!.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a forced conflict surfaces as a staged conflict, not a crash or silent overwrite`() = runTest {
        repository.saveNote("note-1", "Mine", "Mine body")
        engine.sync() // lands SYNCED

        transport.forceConflictOnNextPush("note-1", "Device B Title", "Device B Body")
        repository.saveNote("note-1", "My Edit", "My Edit Body")

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        val note = repository.observeNote("note-1").first()!!
        assertThat(note.syncState).isEqualTo(SyncState.CONFLICT)
        assertThat(note.title).isEqualTo("My Edit") // local content untouched (invariant 2)

        val conflict = db.conflictDao().getForNote("note-1")!!
        assertThat(conflict.remoteTitle).isEqualTo("Device B Title")
    }

    @Test
    fun `observeConflict exposes local and remote — the conflict screen's inputs`() = runTest {
        repository.saveNote("note-1", "Mine", "Mine body")
        engine.sync()
        transport.forceConflictOnNextPush("note-1", "Device B Title", "Device B Body")
        repository.saveNote("note-1", "My Edit", "My Edit Body")
        engine.sync()

        val conflict = repository.observeConflict("note-1").first()!!

        assertThat(conflict.localTitle).isEqualTo("My Edit")
        assertThat(conflict.baseTitle).isNull() // no owned backend history to reconstruct a base from
        assertThat(conflict.remoteTitle).isEqualTo("Device B Title")

        repository.resolveConflict("note-1", "Merged", "Merged body")
        assertThat(repository.observeConflict("note-1").first()).isNull()
    }

    @Test
    fun `pull_skips_notes_with_pending_ops - a remote change never clobbers a note being edited locally`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        engine.sync()

        repository.saveNote("note-1", "Local In Progress", "Local In Progress Body") // fresh PENDING op

        // Isolates the pull-side skip rule directly, independent of whatever the push side of
        // this same round would do to that PENDING op.
        val remoteChange = RemoteNote("note-1", version = "99", deleted = false, title = "Remote Title", body = "Remote Body")
        repository.applyDelta(listOf(remoteChange).map { it.toRemoteNoteChange() }, newSyncToken = "99")

        val note = repository.observeNote("note-1").first()!!
        assertThat(note.title).isEqualTo("Local In Progress")
    }

    @Test
    fun `a remote change with no local pending op is applied by the pull`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        engine.sync()

        transport.queueNextPullPage(
            DeltaPage(
                notes = listOf(RemoteNote("note-1", version = "999", deleted = false, title = "Remote Title", body = "Remote Body")),
                nextSyncToken = "999",
                hasMore = false,
            ),
        )
        engine.sync()

        val note = repository.observeNote("note-1").first()!!
        assertThat(note.title).isEqualTo("Remote Title")
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a transport failure leaves the op IN_FLIGHT, and the next successful run recovers it`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        transport.forceAuthFailureOnNextCall()

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.TransportFailure)
        assertThat(db.outboxDao().getPendingForNote("note-1")).isNull() // frozen (IN_FLIGHT), not PENDING
        assertThat(db.outboxDao().countForNote("note-1")).isEqualTo(1)

        val recovered = engine.sync()

        assertThat(recovered).isEqualTo(SyncOutcome.Done)
        assertThat(repository.observeNote("note-1").first()!!.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `resolution_supersedes_parked_op - resolving a conflict and syncing again ships cleanly`() = runTest {
        repository.saveNote("note-1", "Mine", "Mine body")
        engine.sync()
        transport.forceConflictOnNextPush("note-1", "Device B", "Device B body")
        repository.saveNote("note-1", "My Edit", "My Edit body")
        engine.sync()

        assertThat(repository.observeNote("note-1").first()!!.syncState).isEqualTo(SyncState.CONFLICT)

        repository.resolveConflict("note-1", "Merged Title", "Merged Body")
        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        val note = repository.observeNote("note-1").first()!!
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(note.title).isEqualTo("Merged Title")
    }
}
