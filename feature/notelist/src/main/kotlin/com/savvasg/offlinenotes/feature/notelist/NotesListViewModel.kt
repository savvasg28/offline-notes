package com.savvasg.offlinenotes.feature.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.domain.model.NoteSummary
import com.savvasg.offlinenotes.domain.usecase.DeleteNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.ObserveNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface NotesListUiState {
    data object Loading : NotesListUiState

    data class Success(val notes: List<NoteSummary>) : NotesListUiState

    data class Error(val message: String?) : NotesListUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val observeNotes: ObserveNotesUseCase,
    private val deleteNote: DeleteNoteUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val uiState: StateFlow<NotesListUiState> = query
        .debounce { current -> if (current.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { current -> observeNotes(current) }
        .map<List<NoteSummary>, NotesListUiState> { notes -> NotesListUiState.Success(notes) }
        .catch { failure -> emit(NotesListUiState.Error(failure.message)) }
        .stateIn(viewModelScope, SharingStarted.Lazily, NotesListUiState.Loading)

    fun onSearchQueryChange(value: String) {
        query.value = value
    }

    fun onDeleteNote(id: String) {
        viewModelScope.launch { deleteNote(id) }
    }

    fun newNoteId(): String = UUID.randomUUID().toString()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
