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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NoteEditorUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val body: String = "",
    val syncState: SyncState? = null,
    val rejectionMessage: String? = null,
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    observeNote: ObserveNoteUseCase,
    private val saveNote: SaveNoteUseCase,
    private val requestSync: RequestSyncUseCase,
    private val discardRejectedChange: DiscardRejectedChangeUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = savedStateHandle.toRoute<EditorRoute>().noteId

    private val mutableUiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = mutableUiState.asStateFlow()

    private var userOwnsContent = false
    private var noteExists = false

    init {
        viewModelScope.launch {
            observeNote(noteId).collect { note ->
                if (note == null) {
                    mutableUiState.update { it.copy(isLoading = false) }
                    return@collect
                }
                noteExists = true
                mutableUiState.update { current ->
                    val status = current.copy(
                        isLoading = false,
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

    private fun edit(transform: (NoteEditorUiState) -> NoteEditorUiState) {
        if (mutableUiState.value.isLoading) return
        userOwnsContent = true
        val edited = mutableUiState.updateAndGet(transform)
        if (!noteExists && edited.title.isBlank() && edited.body.isBlank()) return
        noteExists = true
        viewModelScope.launch { withContext(NonCancellable) { saveNote(noteId, edited.title, edited.body) } }
    }
}
