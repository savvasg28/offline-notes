package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.model.NoteConflict
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveConflictUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<NoteConflict?> = repository.observeConflict(id)
}
