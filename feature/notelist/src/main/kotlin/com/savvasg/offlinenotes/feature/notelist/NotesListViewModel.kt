package com.savvasg.offlinenotes.feature.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.domain.model.NoteSummary
import com.savvasg.offlinenotes.domain.usecase.DeleteNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.ObserveNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NotesListUiState(
    val searchQuery: String = "",
    val notes: List<NoteSummary>? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val observeNotes: ObserveNotesUseCase,
    private val deleteNote: DeleteNoteUseCase,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val matchingNotes: Flow<List<NoteSummary>> = searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { query -> observeNotes(query) }

    val uiState: StateFlow<NotesListUiState> =
        combine(searchQuery, matchingNotes, ::NotesListUiState)
            .stateIn(viewModelScope, SharingStarted.Lazily, NotesListUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onDeleteNote(id: String) {
        viewModelScope.launch { deleteNote(id) }
    }

    fun newNoteId(): String = UUID.randomUUID().toString()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
