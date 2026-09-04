package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.model.Note
import com.savvasg.offlinenotes.core.model.NoteConflict
import com.savvasg.offlinenotes.core.model.NoteListItem
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(): Flow<List<NoteListItem>>
    fun observeNote(id: String): Flow<Note?>
    suspend fun saveNote(id: String, title: String, body: String)
    suspend fun deleteNote(id: String)
    suspend fun resolveConflict(noteId: String, title: String, body: String)
    fun searchNotes(query: String): Flow<List<NoteListItem>>

    fun observeConflict(noteId: String): Flow<NoteConflict?>
}
