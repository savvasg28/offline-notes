package com.savvasg.offlinenotes.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.core.database.OfflineNotesDatabase
import com.savvasg.offlinenotes.core.database.entity.NoteEntity
import com.savvasg.offlinenotes.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoFtsTest {

    private lateinit var db: OfflineNotesDatabase
    private lateinit var noteDao: NoteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OfflineNotesDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(OfflineNotesDatabase.SCHEMA_CALLBACK)
            .addCallback(OfflineNotesDatabase.FTS_CALLBACK)
            .build()
        noteDao = db.noteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertNote(id: String, title: String, body: String) {
        noteDao.upsert(
            NoteEntity(
                id = id,
                title = title,
                body = body,
                serverVersion = null,
                syncState = SyncState.PENDING,
                isDeleted = false,
                createdAt = 0,
                lastUpdated = 0,
            ),
        )
    }

    @Test
    fun searchMatchesByTitlePrefix() = runTest {
        insertNote("1", "Grocery list", "Milk eggs bread")
        insertNote("2", "Work notes", "Standup at 10am")

        val results = noteDao.searchActiveNotes(buildFtsMatchQueryForTest("groc")).first()

        assertThat(results).hasSize(1)
        assertThat(results.single().id).isEqualTo("1")
    }

    @Test
    fun searchMatchesByBodyContent() = runTest {
        insertNote("1", "Grocery list", "Milk eggs bread")
        insertNote("2", "Work notes", "Standup at 10am")

        val results = noteDao.searchActiveNotes(buildFtsMatchQueryForTest("standup")).first()

        assertThat(results).hasSize(1)
        assertThat(results.single().id).isEqualTo("2")
    }

    @Test
    fun searchExcludesDeletedNotes() = runTest {
        insertNote("1", "Grocery list", "Milk eggs bread")
        noteDao.upsert(noteDao.getNote("1")!!.copy(isDeleted = true))

        val results = noteDao.searchActiveNotes(buildFtsMatchQueryForTest("grocery")).first()

        assertThat(results).isEmpty()
    }

    @Test
    fun searchReflectsAnUpdateToTheIndexedTable() = runTest {
        insertNote("1", "Original title", "Original body")
        assertThat(noteDao.searchActiveNotes(buildFtsMatchQueryForTest("original")).first()).hasSize(1)

        insertNote("1", "Renamed", "Different body")

        assertThat(noteDao.searchActiveNotes(buildFtsMatchQueryForTest("original")).first()).isEmpty()
        assertThat(noteDao.searchActiveNotes(buildFtsMatchQueryForTest("renamed")).first()).hasSize(1)
    }

    private fun buildFtsMatchQueryForTest(term: String) = "\"$term\"*"
}
