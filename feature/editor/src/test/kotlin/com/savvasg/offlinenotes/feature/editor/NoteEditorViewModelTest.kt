package com.savvasg.offlinenotes.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import com.savvasg.offlinenotes.domain.usecase.ObserveNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.RequestSyncUseCase
import com.savvasg.offlinenotes.domain.usecase.SaveNoteUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val note = MutableStateFlow<Note?>(null)
    private lateinit var viewModel: NoteEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeNote("note-1") } returns note
        viewModel = NoteEditorViewModel(
            ObserveNoteUseCase(repository),
            SaveNoteUseCase(repository),
            RequestSyncUseCase(syncTrigger),
            CoroutineScope(UnconfinedTestDispatcher()),
            SavedStateHandle(route = EditorRoute("note-1")),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun note(title: String, body: String, syncState: SyncState) =
        Note("note-1", title, body, syncState, isDeleted = false, createdAt = 0, lastUpdated = 0)

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
        note.value = note("Server title", "Server body", SyncState.SYNCED)
        viewModel.onTitleChange("My edit")

        note.value = note("Device B title", "Device B body", SyncState.CONFLICT)

        assertThat(viewModel.uiState.value.title).isEqualTo("My edit")
        assertThat(viewModel.uiState.value.syncState).isEqualTo(SyncState.CONFLICT)
    }

    @Test
    fun `an existing note seeds the editor before the user touches it`() = runTest {
        note.value = note("Server title", "Server body", SyncState.SYNCED)

        assertThat(viewModel.uiState.value.title).isEqualTo("Server title")
        assertThat(viewModel.uiState.value.body).isEqualTo("Server body")
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
