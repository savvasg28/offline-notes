package com.savvasg.offlinenotes.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savvasg.offlinenotes.core.designsystem.component.EditorField
import com.savvasg.offlinenotes.core.designsystem.component.EmptyState
import com.savvasg.offlinenotes.core.designsystem.component.InlineBanner
import com.savvasg.offlinenotes.core.designsystem.component.LoadingIndicator
import com.savvasg.offlinenotes.core.designsystem.component.NotesTopAppBar
import com.savvasg.offlinenotes.core.designsystem.theme.Spacing
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.core.designsystem.R as DesignSystemR

@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    onResolveConflict: (String) -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentViewModel by rememberUpdatedState(viewModel)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentViewModel.onEditorClosed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentViewModel.onEditorClosed()
        }
    }

    NoteEditorContent(
        uiState = uiState,
        onTitleChange = viewModel::onTitleChange,
        onBodyChange = viewModel::onBodyChange,
        onBack = onBack,
        onResolveConflict = { onResolveConflict(viewModel.noteId) },
        onDiscardRejected = {
            viewModel.onDiscardRejected()
            onBack()
        },
    )
}

@Composable
fun NoteEditorContent(
    uiState: NoteEditorUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onBack: () -> Unit,
    onResolveConflict: () -> Unit,
    onDiscardRejected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NotesTopAppBar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            NoteEditorUiState.Loading -> LoadingIndicator(
                modifier = Modifier.padding(padding).padding(horizontal = Spacing.lg),
            )

            is NoteEditorUiState.Error -> EmptyState(
                title = stringResource(R.string.editor_error),
                supporting = uiState.message,
                modifier = Modifier.padding(padding),
            )

            is NoteEditorUiState.Success -> NoteEditor(
                state = uiState,
                onTitleChange = onTitleChange,
                onBodyChange = onBodyChange,
                onResolveConflict = onResolveConflict,
                onDiscardRejected = onDiscardRejected,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun NoteEditor(
    state: NoteEditorUiState.Success,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onResolveConflict: () -> Unit,
    onDiscardRejected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.syncState == SyncState.CONFLICT) {
            InlineBanner(
                text = stringResource(R.string.editor_conflict_banner),
                actionLabel = stringResource(DesignSystemR.string.action_resolve),
                onAction = onResolveConflict,
            )
        }
        if (state.syncState == SyncState.REJECTED) {
            InlineBanner(
                text = state.rejectionMessage?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.editor_rejected_banner),
                actionLabel = stringResource(R.string.editor_rejected_discard),
                onAction = onDiscardRejected,
            )
        }
        EditorField(
            value = state.title,
            onValueChange = onTitleChange,
            placeholder = stringResource(DesignSystemR.string.note_title_hint),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
        )
        EditorField(
            value = state.body,
            onValueChange = onBodyChange,
            placeholder = stringResource(DesignSystemR.string.note_body_hint),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = Spacing.lg),
        )
    }
}
