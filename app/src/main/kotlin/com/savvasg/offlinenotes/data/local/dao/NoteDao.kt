package com.savvasg.offlinenotes.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import androidx.sqlite.SQLiteStatement
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.domain.model.SyncState
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

    @Query("SELECT * FROM notes WHERE serverVersion IS NOT NULL")
    suspend fun getAllSyncedNotes(): List<NoteEntity>

    @Query("UPDATE notes SET serverVersion = :serverVersion WHERE id = :id")
    suspend fun updateServerVersion(id: String, serverVersion: String)

    @Query("UPDATE notes SET syncState = :syncState WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: SyncState)

    @Query("UPDATE notes SET isDeleted = :isDeleted, lastUpdated = :now WHERE id = :id")
    suspend fun updateIsDeleted(id: String, isDeleted: Boolean, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: String)

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
