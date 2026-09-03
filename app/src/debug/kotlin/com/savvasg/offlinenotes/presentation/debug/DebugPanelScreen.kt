package com.savvasg.offlinenotes.presentation.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxEntity

@Composable
fun DebugPanelScreen(
    onBack: () -> Unit,
    viewModel: DebugPanelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DebugPanelContent(
        uiState = uiState,
        onBack = onBack,
        onForceSync = viewModel::forceSyncNow,
        onUseHttpTransportChange = viewModel::setUseHttpTransport,
        onBaseUrlChange = viewModel::setBaseUrl,
        onAddLatency = { viewModel.setLatency(LATENCY_MIN_MILLIS, LATENCY_MAX_MILLIS) },
        onClearLatency = viewModel::clearLatency,
        onExpireToken = viewModel::expireTokenOnNextPull,
        onForceRateLimit = viewModel::forceRateLimitOnNextCall,
        onForceAuthFailure = viewModel::forceAuthFailureOnNextCall,
        onForceConflict = viewModel::forceConflictOnNextPush,
        onForceRejected = viewModel::forceRejectedOnNextPush,
        onDropNextResponse = viewModel::dropNextResponseFor,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugPanelContent(
    uiState: DebugPanelUiState,
    onBack: () -> Unit,
    onForceSync: () -> Unit,
    onUseHttpTransportChange: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onAddLatency: () -> Unit,
    onClearLatency: () -> Unit,
    onExpireToken: () -> Unit,
    onForceRateLimit: () -> Unit,
    onForceAuthFailure: () -> Unit,
    onForceConflict: (String) -> Unit,
    onForceRejected: (String) -> Unit,
    onDropNextResponse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Debug panel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SyncStatusSection(syncToken = uiState.syncToken, onForceSync = onForceSync) }

            item { SectionTitle("Transport") }
            item {
                TransportSection(
                    useHttpTransport = uiState.useHttpTransport,
                    baseUrl = uiState.baseUrl,
                    onUseHttpTransportChange = onUseHttpTransportChange,
                    onBaseUrlChange = onBaseUrlChange,
                )
            }

            item { SectionTitle("Global failure injection") }
            item {
                FlowActions {
                    OutlinedButton(onClick = onAddLatency) { Text("Add latency (0.5-2s)") }
                    OutlinedButton(onClick = onClearLatency) { Text("Clear latency") }
                    OutlinedButton(onClick = onExpireToken) { Text("Expire sync token") }
                    OutlinedButton(onClick = onForceRateLimit) { Text("Force rate limit (next call)") }
                    OutlinedButton(onClick = onForceAuthFailure) { Text("Force auth failure (next call)") }
                }
            }

            item { SectionTitle("Per-note failure injection") }
            if (uiState.activeNotes.isEmpty()) {
                item { Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(uiState.activeNotes, key = { it.id }) { note ->
                NoteActionsCard(
                    note = note,
                    onForceConflict = { onForceConflict(note.id) },
                    onForceRejected = { onForceRejected(note.id) },
                )
            }

            item { SectionTitle("Outbox inspector (${uiState.outboxOps.size} op${if (uiState.outboxOps.size == 1) "" else "s"})") }
            if (uiState.outboxOps.isEmpty()) {
                item { Text("Outbox is empty — everything is synced.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(uiState.outboxOps, key = { it.opId }) { op ->
                OutboxRow(op = op, onDropNextResponse = { onDropNextResponse(op.opId) })
            }
        }
    }
}

@Composable
private fun SyncStatusSection(syncToken: String?, onForceSync: () -> Unit) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current sync token", style = MaterialTheme.typography.labelLarge)
            Text(syncToken ?: "(never synced)", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onForceSync, modifier = Modifier.fillMaxWidth()) { Text("Force sync now") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun TransportSection(
    useHttpTransport: Boolean,
    baseUrl: String,
    onUseHttpTransportChange: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use HTTP transport", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = useHttpTransport, onCheckedChange = onUseHttpTransportChange)
            }
            if (useHttpTransport) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server base URL") },
                    supportingText = { Text("Emulator: 10.0.2.2 · physical device: host's LAN IP") },
                    singleLine = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowActions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteActionsCard(note: NoteEntity, onForceConflict: () -> Unit, onForceRejected: () -> Unit) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
            FlowActions {
                OutlinedButton(onClick = onForceConflict) { Text("Force conflict (next push)") }
                OutlinedButton(onClick = onForceRejected) { Text("Force rejection (next push)") }
            }
        }
    }
}

@Composable
private fun OutboxRow(op: OutboxEntity, onDropNextResponse: () -> Unit) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${op.opType}  ${op.status}", style = MaterialTheme.typography.labelLarge)
                Text(op.opId.take(8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("note ${op.noteId.take(8)} · attempt ${op.attemptCount}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = onDropNextResponse) { Text("Drop next response for this op") }
        }
    }
}

private const val LATENCY_MIN_MILLIS = 500L
private const val LATENCY_MAX_MILLIS = 2000L
