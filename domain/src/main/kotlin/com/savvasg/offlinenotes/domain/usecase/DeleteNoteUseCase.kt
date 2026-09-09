package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: String) = repository.deleteNote(id)
}
