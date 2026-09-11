package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import javax.inject.Inject

class DiscardRejectedChangeUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(noteId: String) = repository.discardRejectedChange(noteId)
}
