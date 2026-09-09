package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.model.NoteSummary
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(query: String = ""): Flow<List<NoteSummary>> =
        if (query.isBlank()) repository.observeNotes() else repository.searchNotes(query)
}
