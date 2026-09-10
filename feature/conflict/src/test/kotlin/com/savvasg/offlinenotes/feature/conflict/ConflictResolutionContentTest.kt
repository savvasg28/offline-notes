package com.savvasg.offlinenotes.feature.conflict

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.core.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.domain.model.NoteConflict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConflictResolutionContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val conflict = NoteConflict(
        noteId = "note-1",
        localTitle = "Mine",
        localBody = "My body",
        baseVersion = "1",
        baseTitle = "Base",
        baseBody = "Base body",
        remoteVersion = "2",
        remoteTitle = "Theirs",
        remoteBody = "Their body",
    )

    private fun render(
        state: ConflictResolutionUiState,
        onUseMine: () -> Unit = {},
        onUseTheirs: () -> Unit = {},
        onResolve: () -> Unit = {},
    ) {
        compose.setContent {
            OfflineNotesTheme {
                ConflictResolutionContent(
                    uiState = state,
                    onUseMine = onUseMine,
                    onUseTheirs = onUseTheirs,
                    onDraftTitleChange = {},
                    onDraftBodyChange = {},
                    onResolve = onResolve,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `all three versions are shown so the user can compare before choosing`() {
        render(ConflictResolutionUiState(conflict, conflict.localTitle, conflict.localBody))

        compose.scrollTo("Mine").assertIsDisplayed()
        compose.scrollTo("Base").assertIsDisplayed()
        compose.scrollTo("Theirs").assertIsDisplayed()
    }

    @Test
    fun `without a common ancestor the base card is replaced by an explanation`() {
        val noBase = conflict.copy(baseVersion = null, baseTitle = null, baseBody = null)
        render(ConflictResolutionUiState(noBase, noBase.localTitle, noBase.localBody))

        compose.scrollTo("No common history available — showing local vs. remote only.")
            .assertIsDisplayed()
    }

    @Test
    fun `a resolved conflict shows the empty state rather than a blank screen`() {
        render(ConflictResolutionUiState(conflict = null, draftTitle = "", draftBody = ""))

        compose.onNodeWithText("No conflict to resolve.").assertIsDisplayed()
    }

    @Test
    fun `keep mine and keep theirs are wired to their callbacks`() {
        var mine = 0
        var theirs = 0
        render(
            ConflictResolutionUiState(conflict, conflict.localTitle, conflict.localBody),
            onUseMine = { mine++ },
            onUseTheirs = { theirs++ },
        )

        compose.scrollTo("Keep mine").performClick()
        compose.scrollTo("Keep theirs").performClick()

        assertThat(mine).isEqualTo(1)
        assertThat(theirs).isEqualTo(1)
    }

    @Test
    fun `resolve submits the draft`() {
        var resolved = 0
        render(
            ConflictResolutionUiState(conflict, "Merged", "Merged body"),
            onResolve = { resolved++ },
        )

        compose.scrollTo("Resolve").performClick()

        assertThat(resolved).isEqualTo(1)
    }

    private fun ComposeContentTestRule.scrollTo(text: String) = apply {
        onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }.onNodeWithText(text)
}
