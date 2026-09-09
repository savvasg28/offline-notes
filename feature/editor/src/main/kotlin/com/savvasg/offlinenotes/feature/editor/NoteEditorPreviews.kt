package com.savvasg.offlinenotes.feature.editor

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.savvasg.offlinenotes.core.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.domain.model.SyncState

class NoteEditorUiStateProvider : PreviewParameterProvider<NoteEditorUiState> {
    override val values = sequenceOf(
        NoteEditorUiState(
            title = "Weekly review",
            body = "Ship the sync engine rewrite.\n\nThe outbox coalescing held up under the " +
                "rapid-edit test, so the remaining risk is the bootstrap path after a token expiry.",
            syncState = SyncState.SYNCED,
        ),
        NoteEditorUiState(title = "", body = "", syncState = null),
        NoteEditorUiState(
            title = "Groceries",
            body = "Olive oil, sourdough, coffee beans.",
            syncState = SyncState.CONFLICT,
        ),
    )
}

@Preview(name = "Editor", showBackground = true, heightDp = 640)
@Preview(name = "Editor · dark", showBackground = true, heightDp = 640, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun NoteEditorContentPreview(
    @PreviewParameter(NoteEditorUiStateProvider::class) uiState: NoteEditorUiState,
) {
    OfflineNotesTheme {
        NoteEditorContent(
            uiState = uiState,
            onTitleChange = {},
            onBodyChange = {},
            onBack = {},
            onResolveConflict = {},
        )
    }
}
