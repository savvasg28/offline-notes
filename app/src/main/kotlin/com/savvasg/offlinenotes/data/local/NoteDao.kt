package com.savvasg.offlinenotes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Upsert
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

    @Query("UPDATE notes SET serverVersion = :serverVersion WHERE id = :id")
    suspend fun updateServerVersion(id: String, serverVersion: String)

    @Query("UPDATE notes SET syncState = :syncState WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: SyncState)

    @Query("UPDATE notes SET isDeleted = :isDeleted, lastUpdated = :now WHERE id = :id")
    suspend fun updateIsDeleted(id: String, isDeleted: Boolean, now: Long)

    /** Only safe to call once no outbox row references the note (FK is ON DELETE RESTRICT). */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: String)

    // notes_fts is created via raw SQL (OfflineNotesDatabase.CALLBACK) since FTS5 has no Room
    // annotation, so compile-time SQL verification can't see it — skip it for this query only.
    @SkipQueryVerification
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :matchQuery AND notes.isDeleted = 0
        ORDER BY rank
        """,
    )
    fun searchActiveNotes(matchQuery: String): Flow<List<NoteEntity>>
}
