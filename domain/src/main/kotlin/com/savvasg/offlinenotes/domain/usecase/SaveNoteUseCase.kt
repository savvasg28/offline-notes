package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import javax.inject.Inject

class SaveNoteUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: String, title: String, body: String) =
        repository.saveNote(id, title, body)
}
