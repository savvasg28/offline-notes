package com.savvasg.offlinenotes.feature.notelist

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.savvasg.offlinenotes.core.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.domain.model.NoteListItem
import com.savvasg.offlinenotes.domain.model.SyncState

private const val DAY = 86_400_000L
private val NOW = 1_760_000_000_000L

class NotesListUiStateProvider : PreviewParameterProvider<NotesListUiState> {
    override val values = sequenceOf(
        NotesListUiState(
            searchQuery = "",
            notes = listOf(
                NoteListItem(
                    id = "1",
                    title = "Weekly review",
                    bodySnippet = "Ship the sync engine rewrite, then look at the conflict banner copy once more.",
                    lastUpdated = NOW,
                    syncState = SyncState.SYNCED,
                ),
                NoteListItem(
                    id = "2",
                    title = "Groceries",
                    bodySnippet = "Olive oil, sourdough, coffee beans, something green.",
                    lastUpdated = NOW - DAY,
                    syncState = SyncState.PENDING,
                ),
                NoteListItem(
                    id = "3",
                    title = "",
                    bodySnippet = "",
                    lastUpdated = NOW - 3 * DAY,
                    syncState = SyncState.CONFLICT,
                ),
            ),
        ),
        NotesListUiState(searchQuery = "", notes = emptyList()),
        NotesListUiState(searchQuery = "quarterly", notes = emptyList()),
    )
}

@Preview(name = "Notes list", showBackground = true, heightDp = 640)
@Preview(name = "Notes list · dark", showBackground = true, heightDp = 640, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun NotesListContentPreview(
    @PreviewParameter(NotesListUiStateProvider::class) uiState: NotesListUiState,
) {
    OfflineNotesTheme {
        NotesListContent(
            uiState = uiState,
            onSearchQueryChange = {},
            onNoteClick = {},
            onDeleteNote = {},
            onNewNote = {},
        )
    }
}
