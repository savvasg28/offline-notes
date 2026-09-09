package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<Note?> = repository.observeNote(id)
}
