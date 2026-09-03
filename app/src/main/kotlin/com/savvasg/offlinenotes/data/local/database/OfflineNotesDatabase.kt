package com.savvasg.offlinenotes.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.savvasg.offlinenotes.data.local.dao.ConflictDao
import com.savvasg.offlinenotes.data.local.dao.NoteDao
import com.savvasg.offlinenotes.data.local.dao.OutboxDao
import com.savvasg.offlinenotes.data.local.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.local.entity.NoteConflictEntity
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxEntity
import com.savvasg.offlinenotes.data.local.entity.SyncMetaEntity

@Database(
    entities = [NoteEntity::class, OutboxEntity::class, NoteConflictEntity::class, SyncMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OfflineNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun outboxDao(): OutboxDao
    abstract fun conflictDao(): ConflictDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val DB_NAME = "offline_notes.db"

        /**
         * At most one PENDING op per note, enforced in the schema. Raw SQL because @Index cannot
         * express a WHERE clause, and split from [FTS_CALLBACK] because plain SQLite runs anywhere.
         */
        val SCHEMA_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL(
                    "CREATE UNIQUE INDEX idx_outbox_one_pending ON outbox(noteId) WHERE status = 'PENDING'",
                )
            }
        }

        /**
         * FTS5 has no Room annotation, so the virtual table and its sync triggers are raw SQL;
         * `content_rowid='rowid'` piggybacks on SQLite's implicit rowid because `notes.id` is TEXT.
         *
         * Production builds against [androidx.sqlite.driver.bundled.BundledSQLiteDriver] rather than
         * the platform's SQLite precisely because FTS5 is not guaranteed there — it is absent from the
         * API 36 emulator image. That driver ships an Android-native binary, so it cannot load under
         * Robolectric, whose own SQLite also lacks FTS5. Hence the split: host tests apply
         * [SCHEMA_CALLBACK] alone and cover FTS through an instrumented test, while production adds
         * both callbacks.
         */
        val FTS_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL(
                    "CREATE VIRTUAL TABLE notes_fts USING fts5(title, body, content='notes', content_rowid='rowid')",
                )
                connection.execSQL(
                    """
                    CREATE TRIGGER notes_fts_ai AFTER INSERT ON notes BEGIN
                      INSERT INTO notes_fts(rowid, title, body) VALUES (new.rowid, new.title, new.body);
                    END
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    CREATE TRIGGER notes_fts_ad AFTER DELETE ON notes BEGIN
                      INSERT INTO notes_fts(notes_fts, rowid, title, body) VALUES('delete', old.rowid, old.title, old.body);
                    END
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    CREATE TRIGGER notes_fts_au AFTER UPDATE ON notes BEGIN
                      INSERT INTO notes_fts(notes_fts, rowid, title, body) VALUES('delete', old.rowid, old.title, old.body);
                      INSERT INTO notes_fts(rowid, title, body) VALUES (new.rowid, new.title, new.body);
                    END
                    """.trimIndent(),
                )
            }
        }
    }
}
