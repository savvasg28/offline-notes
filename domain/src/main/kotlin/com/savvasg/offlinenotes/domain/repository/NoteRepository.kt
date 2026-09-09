package com.savvasg.offlinenotes.domain.repository

import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.model.NoteSummary
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(): Flow<List<NoteSummary>>
    fun observeNote(id: String): Flow<Note?>
    suspend fun saveNote(id: String, title: String, body: String)
    suspend fun deleteNote(id: String)
    suspend fun resolveConflict(noteId: String, title: String, body: String)
    fun searchNotes(query: String): Flow<List<NoteSummary>>

    fun observeConflict(noteId: String): Flow<NoteConflict?>
}
