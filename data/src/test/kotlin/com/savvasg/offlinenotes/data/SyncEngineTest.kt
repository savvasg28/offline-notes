package com.savvasg.offlinenotes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.data.database.NoteTransactions
import com.savvasg.offlinenotes.data.database.OfflineNotesDatabase
import com.savvasg.offlinenotes.data.database.entity.OutboxStatus
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.data.NoteRepositoryImpl
import com.savvasg.offlinenotes.domain.model.RemoteNoteChange
import com.savvasg.offlinenotes.domain.sync.OpOutcome
import com.savvasg.offlinenotes.domain.sync.RemoteChangePage
import com.savvasg.offlinenotes.domain.sync.SyncEngine
import com.savvasg.offlinenotes.domain.sync.SyncOutcome
import com.savvasg.offlinenotes.data.testing.FakeTransport
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

        val frozen = repository.freezeAndDrain()
        val opId = frozen.single().opId
        val pushed = transport.pushOps(frozen)
        assertThat(pushed.single()).isInstanceOf(OpOutcome.Applied::class.java)

        assertThat(db.outboxDao().getById(opId)!!.status).isEqualTo(OutboxStatus.IN_FLIGHT)

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        assertThat(db.outboxDao().getById(opId)).isNull()
        assertThat(repository.observeNote("note-1").first()!!.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a forced conflict surfaces as a staged conflict, not a crash or silent overwrite`() = runTest {
        repository.saveNote("note-1", "Mine", "Mine body")
        engine.sync()

        transport.forceConflictOnNextPush("note-1", "Device B Title", "Device B Body")
        repository.saveNote("note-1", "My Edit", "My Edit Body")

        val outcome = engine.sync()

        assertThat(outcome).isEqualTo(SyncOutcome.Done)
        val note = repository.observeNote("note-1").first()!!
        assertThat(note.syncState).isEqualTo(SyncState.CONFLICT)
        assertThat(note.title).isEqualTo("My Edit")

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
        assertThat(conflict.baseTitle).isNull()
        assertThat(conflict.remoteTitle).isEqualTo("Device B Title")

        repository.resolveConflict("note-1", "Merged", "Merged body")
        assertThat(repository.observeConflict("note-1").first()).isNull()
    }

    @Test
    fun `pull_skips_notes_with_pending_ops - a remote change never clobbers a note being edited locally`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        engine.sync()

        repository.saveNote("note-1", "Local In Progress", "Local In Progress Body")

        val remoteChange = RemoteNoteChange("note-1", version = "99", isDeleted = false, title = "Remote Title", body = "Remote Body")
        repository.applyDelta(listOf(remoteChange), newSyncToken = "99")

        val note = repository.observeNote("note-1").first()!!
        assertThat(note.title).isEqualTo("Local In Progress")
    }

    @Test
    fun `a remote change with no local pending op is applied by the pull`() = runTest {
        repository.saveNote("note-1", "T1", "B1")
        engine.sync()

        transport.queueNextPullPage(
            RemoteChangePage(
                notes = listOf(RemoteNoteChange("note-1", version = "999", isDeleted = false, title = "Remote Title", body = "Remote Body")),
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
        assertThat(db.outboxDao().getPendingForNote("note-1")).isNull()
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
