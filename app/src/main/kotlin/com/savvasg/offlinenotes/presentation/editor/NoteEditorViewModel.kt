package com.savvasg.offlinenotes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * No save button (§10) — autosave is debounced ~500ms-1s of typing pause (§5). [flush] persists
 * immediately, bypassing the debounce, for editor close / app background (§6's expedited-sync
 * trigger will hang off the same call once the sync engine exists — for now this is purely the
 * Room-durability half of invariant 1).
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NoteRepository,
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
            edits.debounce(AUTOSAVE_DEBOUNCE_MS).collect { persistNow() }
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

    fun flush() {
        if (hasUserEdited) persistNow()
    }

    private fun persistNow() {
        val title = _title.value
        val body = _body.value
        // Don't create a ghost note for a tap into the editor with nothing ever typed. Once a
        // note has content on disk, always persist, including edits back down to blank.
        if (!hasPersistedOnce && title.isBlank() && body.isBlank()) return
        hasPersistedOnce = true
        viewModelScope.launch { repository.saveNote(noteId, title, body) }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
