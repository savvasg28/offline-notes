package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import javax.inject.Inject

class ResolveConflictUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: String, title: String, body: String) = repository.resolveConflict(id, title, body)
}
