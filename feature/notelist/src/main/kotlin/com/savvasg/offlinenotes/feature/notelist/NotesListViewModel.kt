package com.savvasg.offlinenotes.feature.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.core.model.NoteListItem
import com.savvasg.offlinenotes.core.data.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesListUiState(
    val searchQuery: String = "",
    val notes: List<NoteListItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val repository: NoteRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<NotesListUiState> = combine(
        searchQuery,
        searchQuery
            .debounce(SEARCH_DEBOUNCE_MS)
            .flatMapLatest { query ->
                if (query.isBlank()) repository.observeNotes() else repository.searchNotes(query)
            },
    ) { query, notes -> NotesListUiState(query, notes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesListUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onDeleteNote(id: String) {
        viewModelScope.launch { repository.deleteNote(id) }
    }

    fun newNoteId(): String = UUID.randomUUID().toString()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
