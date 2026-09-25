package com.savvasg.offlinenotes.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.RejectionReason
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import com.savvasg.offlinenotes.domain.usecase.DiscardRejectedChangeUseCase
import com.savvasg.offlinenotes.domain.usecase.ObserveNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.RequestSyncUseCase
import com.savvasg.offlinenotes.domain.usecase.SaveNoteUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NoteEditorViewModelTest {

    private val repository = mockk<NoteRepository>(relaxed = true)
    private val syncTrigger = mockk<SyncTrigger>(relaxed = true)
    private val note = MutableSharedFlow<Note?>(replay = 1)
    private lateinit var viewModel: NoteEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeNote("note-1") } returns note
        note.tryEmit(null)
        viewModel = NoteEditorViewModel(
            ObserveNoteUseCase(repository),
            SaveNoteUseCase(repository),
            RequestSyncUseCase(syncTrigger),
            DiscardRejectedChangeUseCase(repository),
            SavedStateHandle(route = EditorRoute("note-1")),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun NoteEditorViewModel.loaded() = uiState.value as NoteEditorUiState.Success

    private fun note(
        title: String,
        body: String,
        syncState: SyncState,
        rejection: RejectionReason? = null,
    ) = Note("note-1", title, body, syncState, isDeleted = false, createdAt = 0, lastUpdated = 0, rejection = rejection)

    @Test
    fun `every edit is written through, with no debounce to wait out`() = runTest {
        viewModel.onTitleChange("Ti")
        viewModel.onTitleChange("Title")
        viewModel.onBodyChange("Body")

        coVerify(exactly = 1) { repository.saveNote("note-1", "Ti", "") }
        coVerify(exactly = 1) { repository.saveNote("note-1", "Title", "") }
        coVerify(exactly = 1) { repository.saveNote("note-1", "Title", "Body") }
    }

    @Test
    fun `opening a note and typing nothing never creates it`() = runTest {
        viewModel.onTitleChange("")
        viewModel.onBodyChange("")

        coVerify(exactly = 0) { repository.saveNote(any(), any(), any()) }
    }

    @Test
    fun `a conflict arriving mid-edit updates status without overwriting what the user typed`() = runTest {
        note.tryEmit(note("Server title", "Server body", SyncState.SYNCED))
        viewModel.onTitleChange("My edit")

        note.tryEmit(note("Device B title", "Device B body", SyncState.CONFLICT))

        assertThat(viewModel.loaded().title).isEqualTo("My edit")
        assertThat(viewModel.loaded().syncState).isEqualTo(SyncState.CONFLICT)
    }

    @Test
    fun `an existing note seeds the editor before the user touches it`() = runTest {
        note.tryEmit(note("Server title", "Server body", SyncState.SYNCED))

        assertThat(viewModel.loaded().title).isEqualTo("Server title")
        assertThat(viewModel.loaded().body).isEqualTo("Server body")
    }

    @Test
    fun `typing before the note has loaded cannot overwrite it`() = runTest {
        val unloaded = MutableSharedFlow<Note?>(replay = 1)
        every { repository.observeNote("note-2") } returns unloaded
        val subject = NoteEditorViewModel(
            ObserveNoteUseCase(repository),
            SaveNoteUseCase(repository),
            RequestSyncUseCase(syncTrigger),
            DiscardRejectedChangeUseCase(repository),
            SavedStateHandle(route = EditorRoute("note-2")),
        )

        assertThat(subject.uiState.value).isEqualTo(NoteEditorUiState.Loading)
        subject.onTitleChange("X")
        unloaded.tryEmit(
            Note("note-2", "Server title", "Server body", SyncState.SYNCED, false, 0, 0),
        )

        assertThat(subject.loaded().title).isEqualTo("Server title")
        coVerify(exactly = 0) { repository.saveNote(any(), any(), any()) }
    }

    @Test
    fun `a rejection surfaces the server's reason alongside the state`() = runTest {
        note.tryEmit(
            note("T", "B", SyncState.REJECTED, RejectionReason("TOO_LARGE", "Body exceeds 64 KB.")),
        )

        assertThat(viewModel.loaded().syncState).isEqualTo(SyncState.REJECTED)
        assertThat(viewModel.loaded().rejectionMessage).isEqualTo("Body exceeds 64 KB.")
    }

    @Test
    fun `discarding a rejected change reaches the repository`() = runTest {
        note.tryEmit(note("T", "B", SyncState.REJECTED, RejectionReason("INVALID", "No.")))

        viewModel.onDiscardRejected()

        coVerify(exactly = 1) { repository.discardRejectedChange("note-1") }
    }

    @Test
    fun `a failing note stream surfaces as an error state instead of crashing the screen`() = runTest {
        every { repository.observeNote("note-3") } returns flow { error("database is closed") }
        val subject = NoteEditorViewModel(
            ObserveNoteUseCase(repository),
            SaveNoteUseCase(repository),
            RequestSyncUseCase(syncTrigger),
            DiscardRejectedChangeUseCase(repository),
            SavedStateHandle(route = EditorRoute("note-3")),
        )

        assertThat(subject.uiState.value).isEqualTo(NoteEditorUiState.Error("database is closed"))
    }

    @Test
    fun `closing the editor after an edit expedites a sync`() = runTest {
        viewModel.onTitleChange("Title")
        viewModel.onEditorClosed()

        verify(exactly = 1) { syncTrigger.requestImmediateSync() }
    }

    @Test
    fun `closing the editor without editing does not expedite a sync`() = runTest {
        viewModel.onEditorClosed()

        verify(exactly = 0) { syncTrigger.requestImmediateSync() }
    }
}
