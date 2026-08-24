package com.savvasg.offlinenotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
         * The partial unique index that is the coalescing invariant (§4) — @Index can't express
         * a WHERE clause, so it's raw SQL, applied once when the database file is created.
         * Split out from [FTS_CALLBACK] because it's plain SQLite and safe under Robolectric,
         * unlike FTS5 (see below).
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
         * FTS5 external-content table + sync triggers for offline search (§4, §10). FTS5 has no
         * Room annotation, so it's raw SQL; `content_rowid='rowid'` piggybacks on SQLite's
         * implicit integer rowid since `notes.id` (the declared PK) is TEXT.
         *
         * Production builds the database against
         * [androidx.sqlite.driver.bundled.BundledSQLiteDriver] rather than the platform's own
         * SQLite, specifically because FTS5 isn't guaranteed on the OS-provided one (confirmed
         * absent on the API 36 emulator image used during development). That bundled driver
         * ships an Android-native binary only, so it can't load under Robolectric's host-JVM
         * unit tests — those build the database without a driver at all (Robolectric's own
         * shadowed SQLite), which also lacks FTS5. So this callback stays split from
         * [SCHEMA_CALLBACK]: unit tests apply schema-only and verify FTS5 with an instrumented
         * test instead (§11: DAO tests for the FTS queries); production composes both via
         * `Room.databaseBuilder(...).addCallback` called twice.
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
