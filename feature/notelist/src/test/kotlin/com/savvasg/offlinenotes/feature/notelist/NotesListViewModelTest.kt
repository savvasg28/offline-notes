package com.savvasg.offlinenotes.feature.notelist

import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.domain.model.NoteSummary
import com.savvasg.offlinenotes.domain.model.SyncState
import com.savvasg.offlinenotes.domain.usecase.DeleteNoteUseCase
import com.savvasg.offlinenotes.domain.usecase.ObserveNotesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val note = NoteSummary("1", "Weekly review", "Ship it", 0L, SyncState.SYNCED)
    private val observeNotes = mockk<ObserveNotesUseCase>()
    private val deleteNote = mockk<DeleteNoteUseCase>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { observeNotes(any()) } returns flowOf(listOf(note))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `notes are present on the first emission, without waiting out the search debounce`() =
        runTest(dispatcher.scheduler) {
            val viewModel = NotesListViewModel(observeNotes, deleteNote)
            val job = launch { viewModel.uiState.collect {} }
            runCurrent()

            assertThat(viewModel.uiState.value.notes).containsExactly(note)

            job.cancel()
        }

    @Test
    fun `clearing the query restores the full list without a debounce delay`() =
        runTest(dispatcher.scheduler) {
            val viewModel = NotesListViewModel(observeNotes, deleteNote)
            val job = launch { viewModel.uiState.collect {} }

            viewModel.onSearchQueryChange("weekly")
            testScheduler.advanceTimeBy(1_000)
            runCurrent()
            viewModel.onSearchQueryChange("")
            runCurrent()

            assertThat(viewModel.uiState.value.searchQuery).isEmpty()
            assertThat(viewModel.uiState.value.notes).containsExactly(note)

            job.cancel()
        }

    @Test
    fun `the typed query is echoed immediately, ahead of the debounced results`() =
        runTest(dispatcher.scheduler) {
            val viewModel = NotesListViewModel(observeNotes, deleteNote)
            val job = launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.onSearchQueryChange("we")
            runCurrent()

            assertThat(viewModel.uiState.value.searchQuery).isEqualTo("we")

            job.cancel()
        }
}
