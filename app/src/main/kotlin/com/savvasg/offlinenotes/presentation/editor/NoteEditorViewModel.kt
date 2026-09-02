package com.savvasg.offlinenotes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.presentation.navigation.Routes
import com.savvasg.offlinenotes.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * No save button (§10) — autosave is debounced ~500ms-1s of typing pause (§5). [flush] persists
 * immediately, bypassing the debounce, for editor close / app background — the same signal §6
 * uses to trigger an expedited sync, so [flush] does both: durability (Room) is invariant 1's
 * job, freshness (a prompt push) is §6's.
 *
 * Both use `NonCancellable`: without it, a save launched right as the screen is popped could be
 * cancelled mid-flight when the NavBackStackEntry's ViewModelStore clears viewModelScope —
 * exactly the moment this method exists to protect against.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = checkNotNull(savedStateHandle[Routes.EDITOR_ARG_NOTE_ID])

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val edits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasUserEdited = false
    private var hasPersistedOnce = false

    /** Drives the conflict banner (§10): `null` note (not yet created) never conflicts. */
    val syncState: StateFlow<SyncState?> = repository.observeNote(noteId)
        .map { it?.syncState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            repository.observeNote(noteId).collect { note ->
                if (note != null) {
                    hasPersistedOnce = true
                    if (!hasUserEdited) {
                        _title.value = note.title
                        _body.value = note.body
                    }
                }
            }
        }
        viewModelScope.launch {
            edits.debounce(AUTOSAVE_DEBOUNCE_MS).collect { if (hasUserEdited) persistNow() }
        }
    }

    fun onTitleChange(value: String) {
        hasUserEdited = true
        _title.value = value
        edits.tryEmit(Unit)
    }

    fun onBodyChange(value: String) {
        hasUserEdited = true
        _body.value = value
        edits.tryEmit(Unit)
    }

    /**
     * Wired to both the ON_STOP lifecycle event and onDispose (see [com.savvasg.offlinenotes.presentation.editor.NoteEditorScreen]),
     * which can both fire for the same navigation-away — so this must be idempotent per edit
     * session. Without resetting [hasUserEdited], a second immediate call re-runs writeTxn on
     * unchanged content, which unconditionally sets syncState back to PENDING — clobbering a
     * CONFLICT that the first call's sync may have already staged in the meantime. Caught live:
     * the debug panel's "force conflict" correctly parked the op and staged note_conflicts, but
     * the note's syncState read back PENDING because a redundant second flush() had just re-run
     * writeTxn on top of it.
     */
    fun flush() {
        if (!hasUserEdited) return
        val title = _title.value
        val body = _body.value
        if (!hasPersistedOnce && title.isBlank() && body.isBlank()) return
        hasPersistedOnce = true
        hasUserEdited = false
        viewModelScope.launch(NonCancellable) {
            repository.saveNote(noteId, title, body)
            syncScheduler.expediteSync()
        }
    }

    private fun persistNow() {
        val title = _title.value
        val body = _body.value
        // Don't create a ghost note for a tap into the editor with nothing ever typed. Once a
        // note has content on disk, always persist, including edits back down to blank.
        if (!hasPersistedOnce && title.isBlank() && body.isBlank()) return
        hasPersistedOnce = true
        hasUserEdited = false // symmetric with flush() — see its kdoc for why this matters
        viewModelScope.launch(NonCancellable) { repository.saveNote(noteId, title, body) }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
