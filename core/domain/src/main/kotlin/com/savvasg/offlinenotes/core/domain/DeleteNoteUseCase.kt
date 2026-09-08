package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: String) = repository.deleteNote(id)
}
