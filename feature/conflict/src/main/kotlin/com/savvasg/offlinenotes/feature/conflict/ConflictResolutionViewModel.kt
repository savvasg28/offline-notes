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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConflictResolutionUiState(
    val conflict: NoteConflict? = null,
    val draftTitle: String = "",
    val draftBody: String = "",
)

@HiltViewModel
class ConflictResolutionViewModel @Inject constructor(
    observeConflict: ObserveConflictUseCase,
    private val resolveConflict: ResolveConflictUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val noteId: String = savedStateHandle.toRoute<ConflictRoute>().noteId

    private val conflict: StateFlow<NoteConflict?> = observeConflict(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val draft = MutableStateFlow<Pair<String, String>?>(null)

    val uiState: StateFlow<ConflictResolutionUiState> = combine(conflict, draft) { conflict, draft ->
        ConflictResolutionUiState(
            conflict = conflict,
            draftTitle = draft?.first ?: conflict?.localTitle.orEmpty(),
            draftBody = draft?.second ?: conflict?.localBody.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConflictResolutionUiState())

    fun useMine() {
        conflict.value?.let { draft.value = it.localTitle to it.localBody }
    }

    fun useTheirs() {
        conflict.value?.let { draft.value = it.remoteTitle to it.remoteBody }
    }

    fun onDraftTitleChange(value: String) {
        draft.value = value to uiState.value.draftBody
    }

    fun onDraftBodyChange(value: String) {
        draft.value = uiState.value.draftTitle to value
    }

    fun resolve(onDone: () -> Unit) {
        val state = uiState.value
        if (state.conflict == null) return
        viewModelScope.launch {
            resolveConflict(noteId, state.draftTitle, state.draftBody)
            onDone()
        }
    }
}
