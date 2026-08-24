package com.savvasg.offlinenotes.presentation.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "keep mine" / "keep theirs" / manual edit (§10) all funnel through one editable draft: the
 * quick actions just overwrite it, so [resolve] always does the same thing regardless of how the
 * user got there.
 */
@HiltViewModel
class ConflictResolutionViewModel @Inject constructor(
    private val repository: NoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = checkNotNull(savedStateHandle[Routes.CONFLICT_ARG_NOTE_ID])

    val conflict: StateFlow<NoteConflict?> = repository.observeConflict(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _draftTitle = MutableStateFlow<String?>(null)
    val draftTitle: StateFlow<String?> = _draftTitle.asStateFlow()

    private val _draftBody = MutableStateFlow<String?>(null)
    val draftBody: StateFlow<String?> = _draftBody.asStateFlow()

    fun useMine() {
        conflict.value?.let {
            _draftTitle.value = it.localTitle
            _draftBody.value = it.localBody
        }
    }

    fun useTheirs() {
        conflict.value?.let {
            _draftTitle.value = it.remoteTitle
            _draftBody.value = it.remoteBody
        }
    }

    fun onDraftTitleChange(value: String) {
        _draftTitle.value = value
    }

    fun onDraftBodyChange(value: String) {
        _draftBody.value = value
    }

    /** Resolves with the current draft, defaulting to "keep mine" if the user touched nothing. */
    fun resolve(onDone: () -> Unit) {
        val current = conflict.value ?: return
        val title = _draftTitle.value ?: current.localTitle
        val body = _draftBody.value ?: current.localBody
        viewModelScope.launch {
            repository.resolveConflict(noteId, title, body)
            onDone()
        }
    }
}
