package com.savvasg.offlinenotes.presentation.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Resolve conflict") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val conflict = uiState.conflict
        if (conflict == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("No conflict to resolve.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "This note changed on another device while you had local edits pending. " +
                        "Compare the three versions below, then keep one side or edit the result manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { VersionCard(label = "Local (yours)", title = conflict.localTitle, body = conflict.localBody) }
            item {
                if (conflict.baseTitle != null && conflict.baseBody != null) {
                    VersionCard(label = "Base (common ancestor)", title = conflict.baseTitle, body = conflict.baseBody)
                } else {
                    Text(
                        "No common history available — showing local vs. remote only.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { VersionCard(label = "Remote (other device)", title = conflict.remoteTitle, body = conflict.remoteBody) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resolution", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onUseMine) { Text("Keep mine") }
                        OutlinedButton(onClick = onUseTheirs) { Text("Keep theirs") }
                    }
                    TextField(
                        value = uiState.draftTitle,
                        onValueChange = onDraftTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Title") },
                        singleLine = true,
                    )
                    TextField(
                        value = uiState.draftBody,
                        onValueChange = onDraftBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Note") },
                        minLines = 4,
                    )
                    Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                        Text("Resolve")
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCard(label: String, title: String, body: String) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
