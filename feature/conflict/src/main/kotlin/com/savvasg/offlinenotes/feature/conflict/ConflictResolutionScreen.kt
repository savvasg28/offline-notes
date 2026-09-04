package com.savvasg.offlinenotes.feature.conflict

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                title = { Text(stringResource(R.string.conflict_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(DesignSystemR.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val conflict = uiState.conflict
        if (conflict == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(stringResource(R.string.conflict_none), style = MaterialTheme.typography.bodyLarge)
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
                    stringResource(R.string.conflict_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { VersionCard(label = stringResource(R.string.conflict_version_local), title = conflict.localTitle, body = conflict.localBody) }
            item {
                val baseTitle = conflict.baseTitle
                val baseBody = conflict.baseBody
                if (baseTitle != null && baseBody != null) {
                    VersionCard(label = stringResource(R.string.conflict_version_base), title = baseTitle, body = baseBody)
                } else {
                    Text(
                        stringResource(R.string.conflict_no_base),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { VersionCard(label = stringResource(R.string.conflict_version_remote), title = conflict.remoteTitle, body = conflict.remoteBody) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.conflict_resolution_heading), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onUseMine) { Text(stringResource(R.string.conflict_keep_mine)) }
                        OutlinedButton(onClick = onUseTheirs) { Text(stringResource(R.string.conflict_keep_theirs)) }
                    }
                    TextField(
                        value = uiState.draftTitle,
                        onValueChange = onDraftTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(DesignSystemR.string.note_title_hint)) },
                        singleLine = true,
                    )
                    TextField(
                        value = uiState.draftBody,
                        onValueChange = onDraftBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(DesignSystemR.string.note_body_hint)) },
                        minLines = 4,
                    )
                    Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(DesignSystemR.string.action_resolve))
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
            Text(title.ifBlank { stringResource(DesignSystemR.string.note_untitled) }, style = MaterialTheme.typography.titleSmall)
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
