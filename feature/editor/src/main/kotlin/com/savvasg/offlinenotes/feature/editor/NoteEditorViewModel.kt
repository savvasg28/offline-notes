package com.savvasg.offlinenotes.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.savvasg.offlinenotes.core.model.SyncState
import com.savvasg.offlinenotes.core.data.NoteRepository
import com.savvasg.offlinenotes.core.data.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val title: String = "",
    val body: String = "",
    val syncState: SyncState? = null,
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val syncTrigger: SyncTrigger,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = savedStateHandle.toRoute<EditorRoute>().noteId

    private val mutableUiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = mutableUiState.asStateFlow()

    private val edits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasUserEdited = false
    private var hasPersistedOnce = false

    init {
        viewModelScope.launch {
            repository.observeNote(noteId).collect { note ->
                if (note == null) return@collect
                hasPersistedOnce = true
                mutableUiState.update { current ->
                    if (hasUserEdited) {
                        current.copy(syncState = note.syncState)
                    } else {
                        current.copy(title = note.title, body = note.body, syncState = note.syncState)
                    }
                }
            }
        }
        viewModelScope.launch {
            edits.debounce(AUTOSAVE_DEBOUNCE_MS).collect { if (hasUserEdited) persist() }
        }
    }

    fun onTitleChange(value: String) {
        hasUserEdited = true
        mutableUiState.update { it.copy(title = value) }
        edits.tryEmit(Unit)
    }

    fun onBodyChange(value: String) {
        hasUserEdited = true
        mutableUiState.update { it.copy(body = value) }
        edits.tryEmit(Unit)
    }

    fun flush() {
        if (!hasUserEdited) return
        persist(alsoSync = true)
    }

    private fun persist(alsoSync: Boolean = false) {
        val state = mutableUiState.value
        if (!hasPersistedOnce && state.title.isBlank() && state.body.isBlank()) return
        hasPersistedOnce = true
        hasUserEdited = false
        viewModelScope.launch(NonCancellable) {
            repository.saveNote(noteId, state.title, state.body)
            if (alsoSync) syncTrigger.requestImmediateSync()
        }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
