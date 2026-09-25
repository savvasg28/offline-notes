package com.savvasg.offlinenotes.feature.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.usecase.ObserveConflictUseCase
import com.savvasg.offlinenotes.domain.usecase.ResolveConflictUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConflictResolutionUiState {
    data object Loading : ConflictResolutionUiState

    data class Success(
        val conflict: NoteConflict,
        val draftTitle: String,
        val draftBody: String,
    ) : ConflictResolutionUiState

    data object NoConflict : ConflictResolutionUiState

    data class Error(val message: String?) : ConflictResolutionUiState
}

@HiltViewModel
class ConflictResolutionViewModel @Inject constructor(
    observeConflict: ObserveConflictUseCase,
    private val resolveConflict: ResolveConflictUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = savedStateHandle.toRoute<ConflictRoute>().noteId

    private val draft = MutableStateFlow<Pair<String, String>?>(null)

    val uiState: StateFlow<ConflictResolutionUiState> = observeConflict(noteId)
        .combine(draft) { conflict, draft ->
            if (conflict == null) {
                ConflictResolutionUiState.NoConflict
            } else {
                ConflictResolutionUiState.Success(
                    conflict = conflict,
                    draftTitle = draft?.first ?: conflict.localTitle,
                    draftBody = draft?.second ?: conflict.localBody,
                )
            }
        }
        .catch { failure -> emit(ConflictResolutionUiState.Error(failure.message)) }
        .stateIn(viewModelScope, SharingStarted.Lazily, ConflictResolutionUiState.Loading)

    fun useMine() {
        val state = uiState.value as? ConflictResolutionUiState.Success ?: return
        draft.value = state.conflict.localTitle to state.conflict.localBody
    }

    fun useTheirs() {
        val state = uiState.value as? ConflictResolutionUiState.Success ?: return
        draft.value = state.conflict.remoteTitle to state.conflict.remoteBody
    }

    fun onDraftTitleChange(value: String) {
        val state = uiState.value as? ConflictResolutionUiState.Success ?: return
        draft.value = value to state.draftBody
    }

    fun onDraftBodyChange(value: String) {
        val state = uiState.value as? ConflictResolutionUiState.Success ?: return
        draft.value = state.draftTitle to value
    }

    fun resolve(onDone: () -> Unit) {
        val state = uiState.value as? ConflictResolutionUiState.Success ?: return
        viewModelScope.launch {
            resolveConflict(noteId, state.draftTitle, state.draftBody)
            onDone()
        }
    }
}
