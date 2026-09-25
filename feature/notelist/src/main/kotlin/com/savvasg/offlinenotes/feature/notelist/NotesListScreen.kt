package com.savvasg.offlinenotes.feature.notelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savvasg.offlinenotes.core.designsystem.component.ContentCard
import com.savvasg.offlinenotes.core.designsystem.component.EmptyState
import com.savvasg.offlinenotes.core.designsystem.component.NotesTopAppBar
import com.savvasg.offlinenotes.core.designsystem.component.SearchField
import com.savvasg.offlinenotes.core.designsystem.component.StatusDot
import com.savvasg.offlinenotes.core.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.core.designsystem.theme.Spacing
import com.savvasg.offlinenotes.domain.model.SyncState
import java.text.DateFormat
import java.util.Date
import com.savvasg.offlinenotes.core.designsystem.R as DesignSystemR

@Composable
fun NotesListScreen(
    onNoteClick: (String) -> Unit,
    onNewNote: (String) -> Unit,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NotesListContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onNoteClick = onNoteClick,
        onDeleteNote = viewModel::onDeleteNote,
        onNewNote = { onNewNote(viewModel.newNoteId()) },
    )
}

@Composable
fun NotesListContent(
    uiState: NotesListUiState,
    onSearchQueryChange: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NotesTopAppBar(title = stringResource(R.string.notes_list_title)) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewNote) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notes_list_new_note))
            }
        },
    ) { padding ->
        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = stringResource(R.string.notes_list_search_hint),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )

            val notes = uiState.notes
            when {
                notes == null -> Unit
                notes.isEmpty() -> EmptyState(
                    title = stringResource(
                        if (uiState.searchQuery.isBlank()) R.string.notes_list_empty else R.string.notes_list_no_matches,
                    ),
                    supporting = if (uiState.searchQuery.isBlank()) {
                        stringResource(R.string.notes_list_empty_supporting)
                    } else {
                        null
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(notes, key = { it.id }) { note ->
                        ContentCard(
                            title = note.title.ifBlank { stringResource(DesignSystemR.string.note_untitled) },
                            snippet = note.bodySnippet,
                            meta = remember(note.lastUpdated, dateFormat) {
                                dateFormat.format(Date(note.lastUpdated))
                            },
                            onClick = { onNoteClick(note.id) },
                            leading = {
                                StatusDot(
                                    color = note.syncState.color(),
                                    contentDescription = stringResource(note.syncState.labelRes()),
                                )
                            },
                            trailing = {
                                FilledTonalIconButton(
                                    onClick = { onDeleteNote(note.id) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.notes_list_delete_note),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncState.color(): Color = when (this) {
    SyncState.SYNCED -> OfflineNotesTheme.statusColors.synced
    SyncState.PENDING -> OfflineNotesTheme.statusColors.pending
    SyncState.CONFLICT -> OfflineNotesTheme.statusColors.conflict
    SyncState.REJECTED -> OfflineNotesTheme.statusColors.rejected
}

private fun SyncState.labelRes(): Int = when (this) {
    SyncState.SYNCED -> R.string.notes_list_status_synced
    SyncState.PENDING -> R.string.notes_list_status_pending
    SyncState.CONFLICT -> R.string.notes_list_status_conflict
    SyncState.REJECTED -> R.string.notes_list_status_rejected
}
