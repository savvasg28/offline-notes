package com.savvasg.offlinenotes.feature.conflict

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.savvasg.offlinenotes.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.domain.model.NoteConflict

class ConflictResolutionUiStateProvider : PreviewParameterProvider<ConflictResolutionUiState> {
    private val withBase = NoteConflict(
        noteId = "1",
        localTitle = "Weekly review",
        localBody = "Ship the sync engine rewrite, then revisit the banner copy.",
        baseVersion = "7",
        baseTitle = "Weekly review",
        baseBody = "Ship the sync engine rewrite.",
        remoteVersion = "9",
        remoteTitle = "Weekly review (edited on laptop)",
        remoteBody = "Ship the sync engine rewrite. Ask design about the conflict screen.",
    )

    override val values = sequenceOf(
        ConflictResolutionUiState.Success(
            conflict = withBase,
            draftTitle = withBase.localTitle,
            draftBody = withBase.localBody,
        ),
        ConflictResolutionUiState.Success(
            conflict = withBase.copy(baseVersion = null, baseTitle = null, baseBody = null),
            draftTitle = withBase.localTitle,
            draftBody = withBase.localBody,
        ),
        ConflictResolutionUiState.NoConflict,
        ConflictResolutionUiState.Loading,
        ConflictResolutionUiState.Error("Conflict store is unavailable"),
    )
}

@Preview(name = "Conflict", showBackground = true, heightDp = 900)
@Preview(name = "Conflict · dark", showBackground = true, heightDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ConflictResolutionContentPreview(
    @PreviewParameter(ConflictResolutionUiStateProvider::class) uiState: ConflictResolutionUiState,
) {
    OfflineNotesTheme {
        ConflictResolutionContent(
            uiState = uiState,
            onUseMine = {},
            onUseTheirs = {},
            onDraftTitleChange = {},
            onDraftBodyChange = {},
            onResolve = {},
            onBack = {},
        )
    }
}
