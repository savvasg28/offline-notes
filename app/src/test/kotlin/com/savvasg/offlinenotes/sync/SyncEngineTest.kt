package com.savvasg.offlinenotes.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.data.local.OfflineNotesDatabase
import com.savvasg.offlinenotes.data.local.NoteTransactions
import com.savvasg.offlinenotes.data.local.OutboxStatus
import com.savvasg.offlinenotes.data.local.SyncState
import com.savvasg.offlinenotes.data.repository.NoteRepositoryImpl
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.ServerCore
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
 * NoteTransactionsTest for why FTS5 is excluded here) to [FakeTransport] backed by a real
 * [ServerCore], driven through [SyncEngine]. No Hilt: everything is small enough to construct
 * directly, which keeps these tests fast and free of any framework magic to reason around.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var db: OfflineNotesDatabase
    private lateinit var repository: NoteRepositoryImpl
    private lateinit var serverCore: ServerCore
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
        serverCore = ServerCore()
        transport = FakeTransport(serverCore)
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

        // The first attempt: freeze + push succeed (server applies and caches the result), but
        // the process dies before the local apply txn runs — the ack is never processed.
        val frozen = repository.freezeAndDrain()
        val pushed = transport.pushOps(frozen.map { it.toOutgoingOp() })
        assertThat(pushed.single()).isInstanceOf(OpResult.Applied::class.java)

        val opId = frozen.single().opId
        assertThat(db.outboxDao().getById(opId)!!.status).isEqualTo(OutboxStatus.IN_FLIGHT)

        // App restarts; engine.sync() resets the stale IN_FLIGHT op and re-drains.
        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        assertThat(db.outboxDao().getById(opId)).isNull()
        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.serverVersion).isEqualTo("1") // the version from the ORIGINAL apply, not a duplicate
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a stale-base push surfaces as a staged conflict, not a crash or silent overwrite`() = runTest {
        repository.saveNote("note-1", "Mine", "Mine body")
        engine.sync() // establishes the note on the server at v1

        serverCore.simulateConcurrentEdit("note-1", "Device B Title", "Device B Body")
        repository.saveNote("note-1", "My Edit", "My Edit Body") // still based on v1 locally

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        val note = repository.observeNote("note-1").first()!!
        assertThat(note.syncState).isEqualTo(SyncState.CONFLICT)
        assertThat(note.title).isEqualTo("My Edit") // local content untouched (invariant 2)

        val conflict = db.conflictDao().getForNote("note-1")!!
        assertThat(conflict.remoteTitle).isEqualTo("Device B Title")
    }

    @Test
    fun `observeConflict exposes local, base, and remote — the conflict screen's three inputs`() = runTest {
        repository.saveNote("note-1", "Mine", "Mine body")
        engine.sync()
        serverCore.simulateConcurrentEdit("note-1", "Device B Title", "Device B Body")
        repository.saveNote("note-1", "My Edit", "My Edit Body")
        engine.sync()

        val conflict = repository.observeConflict("note-1").first()!!

        assertThat(conflict.localTitle).isEqualTo("My Edit")
        assertThat(conflict.baseTitle).isEqualTo("Mine") // reconstructed from the server's change log
        assertThat(conflict.remoteTitle).isEqualTo("Device B Title")

        repository.resolveConflict("note-1", "Merged", "Merged body")
        assertThat(repository.observeConflict("note-1").first()).isNull()
    }

    @Test
    fun `pull_skips_notes_with_pending_ops - a remote change never clobbers a note being edited locally`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        engine.sync()

        serverCore.simulateConcurrentEdit("note-1", "Remote Title", "Remote Body")
        repository.saveNote("note-1", "Local In Progress", "Local In Progress Body")

        // Pull happens as part of sync(), but the push above already turned this into a conflict
        // path in the previous test; here we isolate the pull-side skip rule directly.
        val page = transport.pullDelta(repository.currentSyncToken())
        repository.applyDelta(page.notes.map { it.toRemoteNoteChange() }, page.nextSyncToken)

        val note = repository.observeNote("note-1").first()!!
        assertThat(note.title).isEqualTo("Local In Progress")
    }

    @Test
    fun `a remote change with no local pending op is applied by the pull`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        engine.sync()

        serverCore.simulateConcurrentEdit("note-1", "Remote Title", "Remote Body")
        engine.sync()

        val note = repository.observeNote("note-1").first()!!
        assertThat(note.title).isEqualTo("Remote Title")
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a transport failure leaves the op IN_FLIGHT, and the next successful run recovers it`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        serverCore.forceAuthFailureOnNextCall()

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
        serverCore.simulateConcurrentEdit("note-1", "Device B", "Device B body")
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
