package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteRepository
import javax.inject.Inject

class ResolveConflictUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(id: String, title: String, body: String) = repository.resolveConflict(id, title, body)
}
