package com.savvasg.offlinenotes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.sync.SyncScheduler
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
    /** Null until the note exists on disk; a note that was never created can't be in conflict. */
    val syncState: SyncState? = null,
)

/**
 * No save button: autosave is debounced on a typing pause, and [flush] persists immediately for
 * editor close / app background, which also triggers an expedited sync.
 *
 * Persistence runs in `NonCancellable`: without it a save launched as the screen is popped can be
 * cancelled when the NavBackStackEntry's ViewModelStore clears viewModelScope — precisely the
 * moment [flush] exists to protect against.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val syncScheduler: SyncScheduler,
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
                    // Once the user has typed, a remote update must not overwrite what's on screen.
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

    /**
     * Idempotent per edit session, deliberately: NoteEditorScreen wires this to both ON_STOP and
     * onDispose, which can both fire for one navigation-away. Without clearing [hasUserEdited] the
     * second call re-runs writeTxn on unchanged content, resetting syncState to PENDING and
     * clobbering a CONFLICT the first call's sync had already staged. Caught live; guarded by
     * NoteEditorViewModelTest.
     */
    fun flush() {
        if (!hasUserEdited) return
        persist(alsoSync = true)
    }

    private fun persist(alsoSync: Boolean = false) {
        val state = mutableUiState.value
        // Don't create a ghost note when the user opened the editor and typed nothing. Once the
        // note exists, always persist — including edits back down to blank.
        if (!hasPersistedOnce && state.title.isBlank() && state.body.isBlank()) return
        hasPersistedOnce = true
        hasUserEdited = false
        viewModelScope.launch(NonCancellable) {
            repository.saveNote(noteId, state.title, state.body)
            if (alsoSync) syncScheduler.expediteSync()
        }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
