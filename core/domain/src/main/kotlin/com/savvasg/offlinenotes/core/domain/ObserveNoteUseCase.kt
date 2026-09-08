package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteRepository
import com.savvasg.offlinenotes.core.model.Note
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<Note?> = repository.observeNote(id)
}
