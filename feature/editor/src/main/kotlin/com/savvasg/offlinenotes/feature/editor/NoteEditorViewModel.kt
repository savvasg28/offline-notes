package com.savvasg.offlinenotes.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.usecase.DiscardRejectedChangeUseCase
import com.savvasg.offlinenotes.domain.usecase.ObserveNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.RequestSyncUseCase
import com.savvasg.offlinenotes.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface NoteEditorUiState {
    data object Loading : NoteEditorUiState

    data class Success(
        val title: String = "",
        val body: String = "",
        val syncState: SyncState? = null,
        val rejectionMessage: String? = null,
    ) : NoteEditorUiState

    data class Error(val message: String?) : NoteEditorUiState
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    observeNote: ObserveNoteUseCase,
    private val saveNote: SaveNoteUseCase,
    private val requestSync: RequestSyncUseCase,
    private val discardRejectedChange: DiscardRejectedChangeUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = savedStateHandle.toRoute<EditorRoute>().noteId

    private val mutableUiState = MutableStateFlow<NoteEditorUiState>(NoteEditorUiState.Loading)
    val uiState: StateFlow<NoteEditorUiState> = mutableUiState.asStateFlow()

    private var userOwnsContent = false
    private var noteExists = false

    init {
        viewModelScope.launch {
            observeNote(noteId)
                .catch { failure -> mutableUiState.value = NoteEditorUiState.Error(failure.message) }
                .collect { note ->
                    if (note == null) {
                        mutableUiState.update { it as? NoteEditorUiState.Success ?: NoteEditorUiState.Success() }
                        return@collect
                    }
                    noteExists = true
                    mutableUiState.update { current ->
                        val loaded = current as? NoteEditorUiState.Success ?: NoteEditorUiState.Success()
                        val status = loaded.copy(
                            syncState = note.syncState,
                            rejectionMessage = note.rejection?.message,
                        )
                        if (userOwnsContent) status else status.copy(title = note.title, body = note.body)
                    }
                }
        }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

    fun onDiscardRejected() {
        viewModelScope.launch { withContext(NonCancellable) { discardRejectedChange(noteId) } }
    }

    fun onEditorClosed() {
        if (userOwnsContent) requestSync()
    }

    private fun edit(transform: (NoteEditorUiState.Success) -> NoteEditorUiState.Success) {
        val current = mutableUiState.value as? NoteEditorUiState.Success ?: return
        userOwnsContent = true
        val edited = transform(current)
        mutableUiState.value = edited
        if (!noteExists && edited.title.isBlank() && edited.body.isBlank()) return
        noteExists = true
        viewModelScope.launch { withContext(NonCancellable) { saveNote(noteId, edited.title, edited.body) } }
    }
}
