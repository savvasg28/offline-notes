package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.model.Note
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<Note?> = repository.observeNote(id)
}
