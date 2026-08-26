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
import com.savvasg.offlinenotes.data.local.NoteEntity
import com.savvasg.offlinenotes.data.local.OutboxEntity

/**
 * Failure injection, the outbox inspector, and the current sync token (§10) — "a reviewer should
 * be able to open the debug screen, tap 'simulate concurrent device edit,' and watch the conflict
 * flow happen" (§9). Every action here is a single tap for exactly that reason.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugPanelScreen(
    onBack: () -> Unit,
    viewModel: DebugPanelViewModel = hiltViewModel(),
) {
    val outboxOps by viewModel.outboxOps.collectAsStateWithLifecycle()
    val syncToken by viewModel.syncToken.collectAsStateWithLifecycle()
    val activeNotes by viewModel.activeNotes.collectAsStateWithLifecycle()
    val useHttpTransport by viewModel.transportSettings.useHttpTransport.collectAsStateWithLifecycle()
    val baseUrl by viewModel.transportSettings.baseUrl.collectAsStateWithLifecycle()

    Scaffold(
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
            item { SyncStatusSection(syncToken = syncToken, onForceSync = viewModel::forceSyncNow) }

            item { SectionTitle("Transport (§9)") }
            item {
                TransportSection(
                    useHttpTransport = useHttpTransport,
                    baseUrl = baseUrl,
                    onUseHttpTransportChange = viewModel.transportSettings::setUseHttpTransport,
                    onBaseUrlChange = viewModel.transportSettings::setBaseUrl,
                )
            }

            item { SectionTitle("Global failure injection") }
            item {
                FlowActions {
                    OutlinedButton(onClick = { viewModel.setLatency(500, 2000) }) { Text("Add latency (0.5-2s)") }
                    OutlinedButton(onClick = viewModel::clearLatency) { Text("Clear latency") }
                    OutlinedButton(onClick = viewModel::expireTokenOnNextPull) { Text("Expire sync token") }
                    OutlinedButton(onClick = viewModel::forceRateLimitOnNextCall) { Text("Force rate limit (next call)") }
                    OutlinedButton(onClick = viewModel::forceAuthFailureOnNextCall) { Text("Force auth failure (next call)") }
                }
            }

            item { SectionTitle("Per-note failure injection") }
            if (activeNotes.isEmpty()) {
                item { Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(activeNotes, key = { it.id }) { note ->
                NoteActionsCard(note = note, viewModel = viewModel)
            }

            item { SectionTitle("Outbox inspector (${outboxOps.size} op${if (outboxOps.size == 1) "" else "s"})") }
            if (outboxOps.isEmpty()) {
                item { Text("Outbox is empty — everything is synced.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(outboxOps, key = { it.opId }) { op ->
                OutboxRow(op = op, onDropNextResponse = { viewModel.dropNextResponseFor(op.opId) })
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
                Text("Use HTTP transport (:server demo)", style = MaterialTheme.typography.bodyMedium)
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
private fun NoteActionsCard(note: NoteEntity, viewModel: DebugPanelViewModel) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
            FlowActions {
                OutlinedButton(onClick = { viewModel.forceConflictOnNextPush(note.id) }) { Text("Force conflict (next push)") }
                OutlinedButton(onClick = { viewModel.forceRejectedOnNextPush(note.id) }) { Text("Force rejection (next push)") }
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
