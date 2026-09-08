package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteRepository
import com.savvasg.offlinenotes.core.model.NoteListItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Empty query means "show everything" — the one decision the notes list makes about its own
 * search box, kept out of the ViewModel so it's independent of how the query flow is debounced. */
class ObserveNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(query: String = ""): Flow<List<NoteListItem>> =
        if (query.isBlank()) repository.observeNotes() else repository.searchNotes(query)
}
