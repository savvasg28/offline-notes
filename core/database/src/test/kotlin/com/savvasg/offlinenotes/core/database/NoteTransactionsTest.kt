package com.savvasg.offlinenotes.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.core.database.entity.OutboxStatus
import com.savvasg.offlinenotes.core.model.ConflictPayload
import com.savvasg.offlinenotes.core.model.OpType
import com.savvasg.offlinenotes.core.model.RemoteNoteChange
import com.savvasg.offlinenotes.core.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteTransactionsTest {

    private lateinit var db: OfflineNotesDatabase
    private lateinit var transactions: NoteTransactions

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OfflineNotesDatabase::class.java)
            .addCallback(OfflineNotesDatabase.SCHEMA_CALLBACK)
            .allowMainThreadQueries()
            .build()
        transactions = NoteTransactions(db, db.noteDao(), db.outboxDao(), db.conflictDao(), db.syncMetaDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `first edit creates the note and a CREATE op`() = runTest {
        transactions.writeTxn("note-1", "Title", "Body", now = 1000)

        val note = db.noteDao().getNote("note-1")
        assertThat(note).isNotNull()
        assertThat(note!!.title).isEqualTo("Title")
        assertThat(note.serverVersion).isNull()
        assertThat(note.syncState).isEqualTo(SyncState.PENDING)

        val op = db.outboxDao().getPendingForNote("note-1")
        assertThat(op).isNotNull()
        assertThat(op!!.opType).isEqualTo(OpType.CREATE)
        assertThat(op.title).isEqualTo("Title")
    }

    @Test
    fun `only_one_pending_op_per_note - rapid edits coalesce in place`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val firstOpId = db.outboxDao().getPendingForNote("note-1")!!.opId

        transactions.writeTxn("note-1", "T2", "B2", now = 1001)
        transactions.writeTxn("note-1", "T3", "B3", now = 1002)

        val ops = db.outboxDao().getPendingOpsOrderedByCreatedAt()
        assertThat(ops).hasSize(1)
        assertThat(ops.single().opId).isEqualTo(firstOpId)
        assertThat(ops.single().title).isEqualTo("T3")
        assertThat(ops.single().body).isEqualTo("B3")
        assertThat(ops.single().createdAt).isEqualTo(1000L)
    }

    @Test
    fun `partial unique index rejects a second PENDING op for the same note at the schema level`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)

        try {
            db.useWriterConnection { connection ->
                connection.execSQL(
                    """
                    INSERT INTO outbox (opId, noteId, opType, title, body, status, createdAt, attemptCount)
                    VALUES ('manual-op', 'note-1', 'UPDATE', 'x', 'y', 'PENDING', 1001, 0)
                    """.trimIndent(),
                )
            }
            throw AssertionError("expected SQLiteException")
        } catch (expected: SQLiteException) {
        }
    }

    @Test
    fun `edit after the CREATE op is acked produces an UPDATE op`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId
        transactions.applyAppliedTxn(opId, newVersion = "v1")

        transactions.writeTxn("note-1", "T2", "B2", now = 2000)

        val op = db.outboxDao().getPendingForNote("note-1")!!
        assertThat(op.opType).isEqualTo(OpType.UPDATE)
    }

    @Test
    fun `freeze txn moves PENDING to IN_FLIGHT and resolves baseVersion from serverVersion`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)

        val frozen = transactions.freezeTxn()

        assertThat(frozen).hasSize(1)
        assertThat(frozen.single().baseVersion).isNull()
        val op = db.outboxDao().getById(frozen.single().opId)!!
        assertThat(op.status).isEqualTo(OutboxStatus.IN_FLIGHT)
    }

    @Test
    fun `base_version_resolved_at_drain_not_creation - ships the post-ack version, not the version at op creation`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val createOpId = db.outboxDao().getPendingForNote("note-1")!!.opId
        transactions.applyAppliedTxn(createOpId, newVersion = "v1")

        transactions.writeTxn("note-1", "T2", "B2", now = 2000)
        val frozenFirst = transactions.freezeTxn()
        assertThat(frozenFirst.single().baseVersion).isEqualTo("v1")

        transactions.writeTxn("note-1", "T3", "B3", now = 3000)

        transactions.applyAppliedTxn(frozenFirst.single().opId, newVersion = "v2")

        val frozenSecond = transactions.freezeTxn()
        assertThat(frozenSecond).hasSize(1)
        assertThat(frozenSecond.single().baseVersion).isEqualTo("v2")
    }

    @Test
    fun `apply_result_never_overwrites_content - APPLIED only moves serverVersion`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val frozen = transactions.freezeTxn()
        val opId = frozen.single().opId

        transactions.writeTxn("note-1", "T2-live-edit", "B2-live-edit", now = 1500)

        transactions.applyAppliedTxn(opId, newVersion = "v1")

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.title).isEqualTo("T2-live-edit")
        assertThat(note.body).isEqualTo("B2-live-edit")
        assertThat(note.serverVersion).isEqualTo("v1")
        assertThat(note.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `applyAppliedTxn sets SYNCED only once no outbox rows remain for the note`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId

        transactions.applyAppliedTxn(opId, newVersion = "v1")

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(db.outboxDao().countForNote("note-1")).isEqualTo(0)
    }

    @Test
    fun `edit_during_in_flight_creates_second_op_and_loses_nothing`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val frozen = transactions.freezeTxn()
        val firstOpId = frozen.single().opId

        transactions.writeTxn("note-1", "T2", "B2", now = 1500)
        val secondOpId = db.outboxDao().getPendingForNote("note-1")!!.opId
        assertThat(secondOpId).isNotEqualTo(firstOpId)

        transactions.applyAppliedTxn(firstOpId, newVersion = "v1")

        assertThat(db.outboxDao().getById(firstOpId)).isNull()
        val remaining = db.outboxDao().getById(secondOpId)
        assertThat(remaining).isNotNull()
        assertThat(remaining!!.status).isEqualTo(OutboxStatus.PENDING)
    }

    @Test
    fun `conflict_parks_op_and_stages_all_three_inputs`() = runTest {
        transactions.writeTxn("note-1", "Local Title", "Local Body", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId

        transactions.applyConflictTxn(
            opId,
            ConflictPayload(
                baseVersion = "v0",
                baseTitle = "Base Title",
                baseBody = "Base Body",
                remoteVersion = "v1",
                remoteTitle = "Remote Title",
                remoteBody = "Remote Body",
            ),
            detectedAt = 5000,
        )

        val op = db.outboxDao().getById(opId)!!
        assertThat(op.status).isEqualTo(OutboxStatus.PARKED)

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.syncState).isEqualTo(SyncState.CONFLICT)
        assertThat(note.title).isEqualTo("Local Title")

        val conflict = db.conflictDao().getForNote("note-1")!!
        assertThat(conflict.opId).isEqualTo(opId)
        assertThat(conflict.baseVersion).isEqualTo("v0")
        assertThat(conflict.baseTitle).isEqualTo("Base Title")
        assertThat(conflict.remoteVersion).isEqualTo("v1")
        assertThat(conflict.remoteTitle).isEqualTo("Remote Title")
        assertThat(conflict.remoteBody).isEqualTo("Remote Body")
    }

    @Test
    fun `REJECTED deletes the op and returns it as a poison pill, never retried`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId

        val deleted = transactions.applyRejectedTxn(opId)

        assertThat(deleted).isNotNull()
        assertThat(deleted!!.opId).isEqualTo(opId)
        assertThat(db.outboxDao().getById(opId)).isNull()
    }

    @Test
    fun `deleteTxn tombstones the note and coalesces into a DELETE op`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId

        transactions.deleteTxn("note-1", now = 2000)

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.isDeleted).isTrue()

        val op = db.outboxDao().getById(opId)!!
        assertThat(op.opType).isEqualTo(OpType.DELETE)
        assertThat(op.title).isNull()
        assertThat(op.body).isNull()
    }

    @Test
    fun `resetInFlightToPending resets stale IN_FLIGHT rows for crash recovery`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        transactions.freezeTxn()

        db.outboxDao().resetInFlightToPending()

        val op = db.outboxDao().getPendingForNote("note-1")
        assertThat(op).isNotNull()
        assertThat(op!!.status).isEqualTo(OutboxStatus.PENDING)
    }

    @Test
    fun `pull_skips_notes_with_pending_ops - a delta carrying a remote change does not touch a locally-edited note`() = runTest {
        transactions.writeTxn("note-1", "Local Title", "Local Body", now = 1000)

        transactions.applyDeltaTxn(
            remoteNotes = listOf(
                RemoteNoteChange(noteId = "note-1", version = "v99", isDeleted = false, title = "Remote Title", body = "Remote Body"),
            ),
            newSyncToken = "token-1",
            now = 2000,
        )

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.title).isEqualTo("Local Title")
        assertThat(note.body).isEqualTo("Local Body")
        assertThat(note.serverVersion).isNull()
    }

    @Test
    fun `delete_edit_race_resurrects - a remote tombstone against a note with a pending edit does not purge it`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId
        transactions.applyAppliedTxn(opId, newVersion = "v1")
        transactions.writeTxn("note-1", "T2 (local edit)", "B2 (local edit)", now = 1500)

        transactions.applyDeltaTxn(
            remoteNotes = listOf(
                RemoteNoteChange(noteId = "note-1", version = "v2", isDeleted = true, title = null, body = null),
            ),
            newSyncToken = "token-1",
            now = 2000,
        )

        val note = db.noteDao().getNote("note-1")
        assertThat(note).isNotNull()
        assertThat(note!!.isDeleted).isFalse()
        assertThat(note.title).isEqualTo("T2 (local edit)")
    }

    @Test
    fun `applyDelta upserts new remote content and commits the sync token atomically`() = runTest {
        transactions.applyDeltaTxn(
            remoteNotes = listOf(
                RemoteNoteChange(noteId = "note-1", version = "v1", isDeleted = false, title = "From Server", body = "Server Body"),
            ),
            newSyncToken = "token-1",
            now = 5000,
        )

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.title).isEqualTo("From Server")
        assertThat(note.serverVersion).isEqualTo("v1")
        assertThat(note.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(db.syncMetaDao().getSyncToken()).isEqualTo("token-1")
    }

    @Test
    fun `applyDelta against a PARKED note only refreshes the conflict's remote side`() = runTest {
        transactions.writeTxn("note-1", "Local Title", "Local Body", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId
        transactions.applyConflictTxn(
            opId,
            ConflictPayload("v0", "Base Title", "Base Body", "v1", "Remote Title v1", "Remote Body v1"),
            detectedAt = 1500,
        )

        transactions.applyDeltaTxn(
            remoteNotes = listOf(
                RemoteNoteChange(noteId = "note-1", version = "v2", isDeleted = false, title = "Remote Title v2", body = "Remote Body v2"),
            ),
            newSyncToken = "token-1",
            now = 2000,
        )

        val note = db.noteDao().getNote("note-1")!!
        assertThat(note.title).isEqualTo("Local Title")

        val conflict = db.conflictDao().getForNote("note-1")!!
        assertThat(conflict.remoteVersion).isEqualTo("v2")
        assertThat(conflict.remoteTitle).isEqualTo("Remote Title v2")
    }

    @Test
    fun `resolution_supersedes_parked_op - one transaction, new op with baseVersion equal to remoteVersion`() = runTest {
        transactions.writeTxn("note-1", "Local Title", "Local Body", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId
        transactions.applyConflictTxn(
            opId,
            ConflictPayload("v0", "Base Title", "Base Body", "v1", "Remote Title", "Remote Body"),
            detectedAt = 1500,
        )

        transactions.resolveConflictTxn("note-1", "Merged Title", "Merged Body", now = 2000)

        assertThat(db.outboxDao().getById(opId)).isNull()
        assertThat(db.conflictDao().getForNote("note-1")).isNull()

        val newOp = db.outboxDao().getPendingForNote("note-1")!!
        assertThat(newOp.opId).isNotEqualTo(opId)
        assertThat(newOp.title).isEqualTo("Merged Title")

        val frozen = transactions.freezeTxn()
        assertThat(frozen.single().baseVersion).isEqualTo("v1")
    }

    @Test
    fun `expired_token_triggers_rebootstrap - local-only notes survive, previously-synced-and-now-absent notes are purged`() = runTest {
        transactions.writeTxn("gone-remotely", "T1", "B1", now = 1000)
        val goneOpId = db.outboxDao().getPendingForNote("gone-remotely")!!.opId
        transactions.applyAppliedTxn(goneOpId, newVersion = "1")

        transactions.writeTxn("local-only", "T2", "B2", now = 2000)

        transactions.applyBootstrapTxn(remoteNotes = emptyList(), newSyncToken = "boot-1", now = 3000)

        assertThat(db.noteDao().getNote("gone-remotely")).isNull()

        val survivor = db.noteDao().getNote("local-only")
        assertThat(survivor).isNotNull()
        assertThat(survivor!!.serverVersion).isNull()
        assertThat(db.outboxDao().getPendingForNote("local-only")).isNotNull()
    }

    @Test
    fun `applyBootstrapTxn does not purge a note that still has a live op`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val opId = db.outboxDao().getPendingForNote("note-1")!!.opId
        transactions.applyAppliedTxn(opId, newVersion = "1")
        transactions.writeTxn("note-1", "T2", "B2", now = 2000)
        transactions.freezeTxn()

        transactions.applyBootstrapTxn(remoteNotes = emptyList(), newSyncToken = "boot-1", now = 3000)

        assertThat(db.noteDao().getNote("note-1")).isNotNull()
    }
}
