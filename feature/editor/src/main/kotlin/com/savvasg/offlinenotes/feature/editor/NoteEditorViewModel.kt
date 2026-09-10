package com.savvasg.offlinenotes.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.savvasg.offlinenotes.domain.ApplicationScope
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.usecase.ObserveNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.RequestSyncUseCase
import com.savvasg.offlinenotes.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorUiState(
    val title: String = "",
    val body: String = "",
    val syncState: SyncState? = null,
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    observeNote: ObserveNoteUseCase,
    private val saveNote: SaveNoteUseCase,
    private val requestSync: RequestSyncUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
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
                if (note == null) return@collect
                noteExists = true
                mutableUiState.update { current ->
                    if (userOwnsContent) {
                        current.copy(syncState = note.syncState)
                    } else {
                        current.copy(title = note.title, body = note.body, syncState = note.syncState)
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

    fun onEditorClosed() {
        if (userOwnsContent) requestSync()
    }

    private fun edit(transform: (NoteEditorUiState) -> NoteEditorUiState) {
        userOwnsContent = true
        val edited = mutableUiState.updateAndGet(transform)
        if (!noteExists && edited.title.isBlank() && edited.body.isBlank()) return
        noteExists = true
        applicationScope.launch {
            saveNote(noteId, edited.title, edited.body)
        }
    }
}
