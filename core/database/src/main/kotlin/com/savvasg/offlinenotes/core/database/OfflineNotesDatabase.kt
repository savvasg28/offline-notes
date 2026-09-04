package com.savvasg.offlinenotes.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.savvasg.offlinenotes.core.database.dao.ConflictDao
import com.savvasg.offlinenotes.core.database.dao.NoteDao
import com.savvasg.offlinenotes.core.database.dao.OutboxDao
import com.savvasg.offlinenotes.core.database.dao.SyncMetaDao
import com.savvasg.offlinenotes.core.database.entity.NoteConflictEntity
import com.savvasg.offlinenotes.core.database.entity.NoteEntity
import com.savvasg.offlinenotes.core.database.entity.OutboxEntity
import com.savvasg.offlinenotes.core.database.entity.SyncMetaEntity

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

        val SCHEMA_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL(
                    "CREATE UNIQUE INDEX idx_outbox_one_pending ON outbox(noteId) WHERE status = 'PENDING'",
                )
            }
        }

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
