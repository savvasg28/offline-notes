package com.savvasg.offlinenotes.feature.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savvasg.offlinenotes.core.designsystem.component.EditorField
import com.savvasg.offlinenotes.core.designsystem.component.EmptyState
import com.savvasg.offlinenotes.core.designsystem.component.LabeledCard
import com.savvasg.offlinenotes.core.designsystem.component.NotesTopAppBar
import com.savvasg.offlinenotes.core.designsystem.component.SectionLabel
import com.savvasg.offlinenotes.core.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.core.designsystem.theme.Spacing
import com.savvasg.offlinenotes.core.designsystem.R as DesignSystemR

@Composable
fun ConflictResolutionScreen(
    onResolved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConflictResolutionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ConflictResolutionContent(
        uiState = uiState,
        onUseMine = viewModel::useMine,
        onUseTheirs = viewModel::useTheirs,
        onDraftTitleChange = viewModel::onDraftTitleChange,
        onDraftBodyChange = viewModel::onDraftBodyChange,
        onResolve = { viewModel.resolve(onResolved) },
        onBack = onBack,
    )
}

@Composable
fun ConflictResolutionContent(
    uiState: ConflictResolutionUiState,
    onUseMine: () -> Unit,
    onUseTheirs: () -> Unit,
    onDraftTitleChange: (String) -> Unit,
    onDraftBodyChange: (String) -> Unit,
    onResolve: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NotesTopAppBar(
                title = stringResource(R.string.conflict_title),
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
        val conflict = uiState.conflict
        if (conflict == null) {
            EmptyState(
                title = stringResource(R.string.conflict_none),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    stringResource(R.string.conflict_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LabeledCard(
                    label = stringResource(R.string.conflict_version_local),
                    title = conflict.localTitle.ifBlank { stringResource(DesignSystemR.string.note_untitled) },
                    body = conflict.localBody,
                    accent = OfflineNotesTheme.statusColors.pending,
                    action = {
                        TextButton(onClick = onUseMine) { Text(stringResource(R.string.conflict_keep_mine)) }
                    },
                )
            }
            item {
                val baseTitle = conflict.baseTitle
                val baseBody = conflict.baseBody
                if (baseTitle != null && baseBody != null) {
                    LabeledCard(
                        label = stringResource(R.string.conflict_version_base),
                        title = baseTitle.ifBlank { stringResource(DesignSystemR.string.note_untitled) },
                        body = baseBody,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.conflict_no_base),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                LabeledCard(
                    label = stringResource(R.string.conflict_version_remote),
                    title = conflict.remoteTitle.ifBlank { stringResource(DesignSystemR.string.note_untitled) },
                    body = conflict.remoteBody,
                    accent = OfflineNotesTheme.statusColors.synced,
                    action = {
                        TextButton(onClick = onUseTheirs) { Text(stringResource(R.string.conflict_keep_theirs)) }
                    },
                )
            }
            item {
                SectionLabel(
                    text = stringResource(R.string.conflict_resolution_heading),
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            item {
                EditorField(
                    value = uiState.draftTitle,
                    onValueChange = onDraftTitleChange,
                    placeholder = stringResource(DesignSystemR.string.note_title_hint),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                )
            }
            item {
                EditorField(
                    value = uiState.draftBody,
                    onValueChange = onDraftBodyChange,
                    placeholder = stringResource(DesignSystemR.string.note_body_hint),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
            }
            item {
                Button(
                    onClick = onResolve,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(DesignSystemR.string.action_resolve))
                }
            }
        }
    }
}
