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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savvasg.offlinenotes.R
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
                title = { Text(stringResource(R.string.debug_panel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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

            item { SectionTitle(stringResource(R.string.debug_section_transport)) }
            item {
                TransportSection(
                    useHttpTransport = uiState.useHttpTransport,
                    baseUrl = uiState.baseUrl,
                    onUseHttpTransportChange = onUseHttpTransportChange,
                    onBaseUrlChange = onBaseUrlChange,
                )
            }

            item { SectionTitle(stringResource(R.string.debug_section_global_failures)) }
            item {
                FlowActions {
                    OutlinedButton(onClick = onAddLatency) { Text(stringResource(R.string.debug_add_latency)) }
                    OutlinedButton(onClick = onClearLatency) { Text(stringResource(R.string.debug_clear_latency)) }
                    OutlinedButton(onClick = onExpireToken) { Text(stringResource(R.string.debug_expire_token)) }
                    OutlinedButton(onClick = onForceRateLimit) { Text(stringResource(R.string.debug_force_rate_limit)) }
                    OutlinedButton(onClick = onForceAuthFailure) { Text(stringResource(R.string.debug_force_auth_failure)) }
                }
            }

            item { SectionTitle(stringResource(R.string.debug_section_per_note_failures)) }
            if (uiState.activeNotes.isEmpty()) {
                item { Text(stringResource(R.string.debug_no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(uiState.activeNotes, key = { it.id }) { note ->
                NoteActionsCard(
                    note = note,
                    onForceConflict = { onForceConflict(note.id) },
                    onForceRejected = { onForceRejected(note.id) },
                )
            }

            item {
                SectionTitle(
                    pluralStringResource(
                        R.plurals.debug_outbox_inspector_title,
                        uiState.outboxOps.size,
                        uiState.outboxOps.size,
                    ),
                )
            }
            if (uiState.outboxOps.isEmpty()) {
                item { Text(stringResource(R.string.debug_outbox_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            Text(stringResource(R.string.debug_sync_token_label), style = MaterialTheme.typography.labelLarge)
            Text(syncToken ?: stringResource(R.string.debug_sync_token_none), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onForceSync, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debug_force_sync)) }
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
                Text(stringResource(R.string.debug_use_http_transport), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = useHttpTransport, onCheckedChange = onUseHttpTransportChange)
            }
            if (useHttpTransport) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.debug_base_url_label)) },
                    supportingText = { Text(stringResource(R.string.debug_base_url_help)) },
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
            Text(note.title.ifBlank { stringResource(R.string.note_untitled) }, style = MaterialTheme.typography.titleSmall)
            FlowActions {
                OutlinedButton(onClick = onForceConflict) { Text(stringResource(R.string.debug_force_conflict)) }
                OutlinedButton(onClick = onForceRejected) { Text(stringResource(R.string.debug_force_rejection)) }
            }
        }
    }
}

@Composable
private fun OutboxRow(op: OutboxEntity, onDropNextResponse: () -> Unit) {
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.debug_outbox_op_header, op.opType.name, op.status.name),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(op.opId.take(8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.debug_outbox_op_detail, op.noteId.take(8), op.attemptCount),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = onDropNextResponse) { Text(stringResource(R.string.debug_drop_next_response)) }
        }
    }
}

private const val LATENCY_MIN_MILLIS = 500L
private const val LATENCY_MAX_MILLIS = 2000L
