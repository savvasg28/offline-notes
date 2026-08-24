package com.savvasg.offlinenotes.domain.repository

import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.NoteListItem
import kotlinx.coroutines.flow.Flow

/**
 * UI-facing half of the repository (§3). Writes here are *intents*: [saveNote] always goes
 * through the outbox, never touches `serverVersion` directly. The sync engine never calls these
 * methods — see [NoteSyncOperations] for why.
 */
interface NoteRepository {
    fun observeNotes(): Flow<List<NoteListItem>>
    fun observeNote(id: String): Flow<Note?>
    suspend fun saveNote(id: String, title: String, body: String)
    suspend fun deleteNote(id: String)
    suspend fun resolveConflict(noteId: String, title: String, body: String)
    fun searchNotes(query: String): Flow<List<NoteListItem>>
}
