package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteRepository
import com.savvasg.offlinenotes.core.model.NoteConflict
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveConflictUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: String): Flow<NoteConflict?> = repository.observeConflict(id)
}
