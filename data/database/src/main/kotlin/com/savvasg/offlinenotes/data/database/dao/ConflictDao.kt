package com.savvasg.offlinenotes.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.savvasg.offlinenotes.data.database.entity.NoteConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {
    @Upsert
    suspend fun upsert(conflict: NoteConflictEntity)

    @Query("SELECT * FROM note_conflicts WHERE noteId = :noteId")
    suspend fun getForNote(noteId: String): NoteConflictEntity?

    @Query("SELECT * FROM note_conflicts WHERE noteId = :noteId")
    fun observeForNote(noteId: String): Flow<NoteConflictEntity?>

    @Query("DELETE FROM note_conflicts WHERE noteId = :noteId")
    suspend fun delete(noteId: String)

    @Query(
        "UPDATE note_conflicts SET remoteVersion = :remoteVersion, remoteTitle = :remoteTitle, remoteBody = :remoteBody WHERE noteId = :noteId",
    )
    suspend fun refreshRemote(noteId: String, remoteVersion: String, remoteTitle: String, remoteBody: String)
}
