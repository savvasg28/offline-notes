package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.model.NoteListItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(query: String = ""): Flow<List<NoteListItem>> =
        if (query.isBlank()) repository.observeNotes() else repository.searchNotes(query)
}
