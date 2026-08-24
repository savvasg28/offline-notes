package com.savvasg.offlinenotes.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the three transaction types (§5). Several of these are the named scenario
 * tests from §11, exercised directly against the transaction layer rather than the full sync
 * engine, since the engine doesn't exist yet at this point in the build order (§14 step 1).
 */
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
        transactions = NoteTransactions(db, db.noteDao(), db.outboxDao(), db.conflictDao())
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

        // A raw INSERT (bypassing NoteTransactions, and Room's @Upsert — which resolves conflicts
        // via INSERT OR IGNORE and would silently no-op instead of surfacing them) proves the
        // constraint lives in the schema itself, not just in NoteTransactions' own bookkeeping.
        try {
            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO outbox (opId, noteId, opType, title, body, status, createdAt, attemptCount)
                VALUES ('manual-op', 'note-1', 'UPDATE', 'x', 'y', 'PENDING', 1001, 0)
                """.trimIndent(),
            )
            throw AssertionError("expected SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
            // idx_outbox_one_pending fired.
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

        // A second edit arrives while the first op is IN_FLIGHT.
        transactions.writeTxn("note-1", "T3", "B3", now = 3000)

        // The in-flight op acks, bumping serverVersion to v2.
        transactions.applyAppliedTxn(frozenFirst.single().opId, newVersion = "v2")

        // The second op was created back when serverVersion was still v1 — it must ship v2.
        val frozenSecond = transactions.freezeTxn()
        assertThat(frozenSecond).hasSize(1)
        assertThat(frozenSecond.single().baseVersion).isEqualTo("v2")
    }

    @Test
    fun `apply_result_never_overwrites_content - APPLIED only moves serverVersion`() = runTest {
        transactions.writeTxn("note-1", "T1", "B1", now = 1000)
        val frozen = transactions.freezeTxn()
        val opId = frozen.single().opId

        // The user keeps typing after the freeze; this lands in a brand-new PENDING op.
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
}
