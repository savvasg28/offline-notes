package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConflictUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<NoteConflict?> = repository.observeConflict(id)
}
