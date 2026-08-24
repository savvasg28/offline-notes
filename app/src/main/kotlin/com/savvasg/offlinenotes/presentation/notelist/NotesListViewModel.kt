package com.savvasg.offlinenotes.presentation.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.domain.model.NoteListItem
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val repository: NoteRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val notes: StateFlow<List<NoteListItem>> = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query -> if (query.isBlank()) repository.observeNotes() else repository.searchNotes(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onDeleteNote(id: String) {
        viewModelScope.launch { repository.deleteNote(id) }
    }

    fun newNoteId(): String = UUID.randomUUID().toString()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
