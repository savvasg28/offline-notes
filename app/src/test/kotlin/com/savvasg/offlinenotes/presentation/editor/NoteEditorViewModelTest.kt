package com.savvasg.offlinenotes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.savvasg.offlinenotes.domain.model.Note
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.sync.SyncScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    private val syncScheduler = mockk<SyncScheduler>(relaxed = true)
    private lateinit var viewModel: NoteEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeNote("note-1") } returns MutableStateFlow<Note?>(null)
        val savedStateHandle = SavedStateHandle(route = EditorRoute("note-1"))
        viewModel = NoteEditorViewModel(repository, syncScheduler, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `flush called twice in a row for the same edit only saves once`() = runTest {
        viewModel.onTitleChange("Title")
        viewModel.onBodyChange("Body")

        viewModel.flush()
        viewModel.flush()

        coVerify(exactly = 1) { repository.saveNote("note-1", "Title", "Body") }
    }

    @Test
    fun `a fresh edit after a flush is saved again`() = runTest {
        viewModel.onTitleChange("Title")
        viewModel.flush()

        viewModel.onBodyChange("New body")
        viewModel.flush()

        coVerify(exactly = 2) { repository.saveNote(any(), any(), any()) }
    }

    @Test
    fun `flush with no edits made is a no-op`() = runTest {
        viewModel.flush()

        coVerify(exactly = 0) { repository.saveNote(any(), any(), any()) }
    }
}
