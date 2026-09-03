package com.savvasg.offlinenotes.presentation.notelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savvasg.offlinenotes.domain.model.NoteListItem
import com.savvasg.offlinenotes.domain.model.SyncState
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesListScreen(
    onNoteClick: (String) -> Unit,
    onNewNote: (String) -> Unit,
    onOpenDebugPanel: (() -> Unit)?,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NotesListContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onNoteClick = onNoteClick,
        onDeleteNote = viewModel::onDeleteNote,
        onNewNote = { onNewNote(viewModel.newNoteId()) },
        onOpenDebugPanel = onOpenDebugPanel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListContent(
    uiState: NotesListUiState,
    onSearchQueryChange: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onOpenDebugPanel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Offline Notes") },
                actions = {
                    // Absent in release builds, where the debug panel isn't compiled in at all.
                    if (onOpenDebugPanel != null) {
                        IconButton(onClick = onOpenDebugPanel) {
                            Icon(Icons.Default.BugReport, contentDescription = "Debug panel")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewNote) {
                Icon(Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            if (uiState.notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.searchQuery.isBlank()) "No notes yet — tap + to create one" else "No matches",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(uiState.notes, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onClick = { onNoteClick(note.id) },
                            onDelete = { onDeleteNote(note.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteListItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        note.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SyncStateDot(note.syncState)
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                if (note.bodySnippet.isNotBlank()) {
                    Text(
                        note.bodySnippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(note.lastUpdated)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SyncStateDot(state: SyncState) {
    val color = when (state) {
        SyncState.SYNCED -> MaterialTheme.colorScheme.primary
        SyncState.PENDING -> MaterialTheme.colorScheme.tertiary
        SyncState.CONFLICT -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(10.dp)
            .background(color, CircleShape),
    )
}
