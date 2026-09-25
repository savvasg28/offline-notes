package com.savvasg.offlinenotes.feature.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.domain.model.NoteConflict
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.usecase.ObserveConflictUseCase
import com.savvasg.offlinenotes.domain.usecase.ResolveConflictUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConflictResolutionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<NoteRepository>(relaxed = true)
    private val conflicts = MutableSharedFlow<NoteConflict?>(replay = 1)
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeConflict("note-1") } returns conflicts
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ConflictResolutionViewModel(
        ObserveConflictUseCase(repository),
        ResolveConflictUseCase(repository),
        SavedStateHandle(route = ConflictRoute("note-1")),
    )

    private fun ConflictResolutionViewModel.loaded() = uiState.value as ConflictResolutionUiState.Success

    @Test
    fun `the screen is loading until the conflict arrives, then the draft starts as the local side`() = runTest(dispatcher.scheduler) {
        val subject = viewModel()
        val job = launch { subject.uiState.collect {} }
        runCurrent()

        assertThat(subject.uiState.value).isEqualTo(ConflictResolutionUiState.Loading)

        conflicts.tryEmit(conflict)
        runCurrent()

        assertThat(subject.loaded().draftTitle).isEqualTo("Mine")
        assertThat(subject.loaded().draftBody).isEqualTo("My body")

        job.cancel()
    }

    @Test
    fun `keep theirs replaces the draft, and resolve submits it`() = runTest(dispatcher.scheduler) {
        val subject = viewModel()
        val job = launch { subject.uiState.collect {} }
        conflicts.tryEmit(conflict)
        runCurrent()

        subject.useTheirs()
        subject.resolve {}

        coVerify(exactly = 1) { repository.resolveConflict("note-1", "Theirs", "Their body") }

        job.cancel()
    }

    @Test
    fun `a conflict that disappears underneath the screen is reported, not shown as loading`() = runTest(dispatcher.scheduler) {
        val subject = viewModel()
        val job = launch { subject.uiState.collect {} }
        conflicts.tryEmit(conflict)
        runCurrent()

        conflicts.tryEmit(null)
        runCurrent()

        assertThat(subject.uiState.value).isEqualTo(ConflictResolutionUiState.NoConflict)

        job.cancel()
    }

    @Test
    fun `a failing conflict stream surfaces as an error state instead of crashing the screen`() = runTest(dispatcher.scheduler) {
        every { repository.observeConflict("note-1") } returns flow { error("database is closed") }
        val subject = viewModel()
        val job = launch { subject.uiState.collect {} }
        runCurrent()

        assertThat(subject.uiState.value).isEqualTo(ConflictResolutionUiState.Error("database is closed"))

        job.cancel()
    }
}
