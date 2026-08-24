package com.savvasg.offlinenotes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY lastUpdated DESC")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    /** Bootstrap re-sync (§7): candidates for "deleted server-side, absent from the snapshot". */
    @Query("SELECT * FROM notes WHERE serverVersion IS NOT NULL")
    suspend fun getAllSyncedNotes(): List<NoteEntity>

    @Query("UPDATE notes SET serverVersion = :serverVersion WHERE id = :id")
    suspend fun updateServerVersion(id: String, serverVersion: String)

    @Query("UPDATE notes SET syncState = :syncState WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: SyncState)

    @Query("UPDATE notes SET isDeleted = :isDeleted, lastUpdated = :now WHERE id = :id")
    suspend fun updateIsDeleted(id: String, isDeleted: Boolean, now: Long)

    /** Only safe to call once no outbox row references the note (FK is ON DELETE RESTRICT). */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: String)

    // notes_fts is created via raw SQL (OfflineNotesDatabase.FTS_CALLBACK) since FTS5 has no Room
    // annotation — Room can neither verify this SQL at compile time nor parse notes_fts out of it
    // for Flow invalidation, so it's a @RawQuery with the observed table declared explicitly.
    // notes_fts changes only as a trigger-driven side effect of `notes` changing within the same
    // transaction, so observing NoteEntity is sufficient — the FTS index is never stale by the
    // time an invalidation fires.
    @RawQuery(observedEntities = [NoteEntity::class])
    fun searchActiveNotesRaw(query: RoomRawQuery): Flow<List<NoteEntity>>
}

fun NoteDao.searchActiveNotes(matchQuery: String): Flow<List<NoteEntity>> =
    searchActiveNotesRaw(
        RoomRawQuery(
            sql = """
                SELECT notes.* FROM notes
                JOIN notes_fts ON notes.rowid = notes_fts.rowid
                WHERE notes_fts MATCH ? AND notes.isDeleted = 0
                ORDER BY rank
            """.trimIndent(),
            onBindStatement = { statement: SQLiteStatement -> statement.bindText(1, matchQuery) },
        ),
    )
