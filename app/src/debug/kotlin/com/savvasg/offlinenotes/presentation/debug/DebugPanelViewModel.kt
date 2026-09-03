package com.savvasg.offlinenotes.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.data.local.dao.NoteDao
import com.savvasg.offlinenotes.data.local.dao.OutboxDao
import com.savvasg.offlinenotes.data.local.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxEntity
import com.savvasg.offlinenotes.data.remote.TransportSettings
import com.savvasg.offlinenotes.data.remote.fake.FakeTransport
import com.savvasg.offlinenotes.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class DebugPanelUiState(
    val outboxOps: List<OutboxEntity> = emptyList(),
    val syncToken: String? = null,
    val activeNotes: List<NoteEntity> = emptyList(),
    val useHttpTransport: Boolean = false,
    val baseUrl: String = "",
)

@HiltViewModel
class DebugPanelViewModel @Inject constructor(
    private val fakeTransport: FakeTransport,
    outboxDao: OutboxDao,
    syncMetaDao: SyncMetaDao,
    noteDao: NoteDao,
    private val syncScheduler: SyncScheduler,
    private val transportSettings: TransportSettings,
) : ViewModel() {

    val uiState: StateFlow<DebugPanelUiState> = combine(
        outboxDao.observeAll(),
        syncMetaDao.observeSyncToken(),
        noteDao.observeActiveNotes(),
        transportSettings.useHttpTransport,
        transportSettings.baseUrl,
    ) { outboxOps, syncToken, activeNotes, useHttp, baseUrl ->
        DebugPanelUiState(outboxOps, syncToken, activeNotes, useHttp, baseUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugPanelUiState())

    fun forceSyncNow() = syncScheduler.expediteSync()

    fun setLatency(minMillis: Long, maxMillis: Long) = fakeTransport.setLatencyMillis(minMillis..maxMillis)
    fun clearLatency() = fakeTransport.setLatencyMillis(null)

    /** Queues both the Conflict result and its remote content, since there is no server state to write. */
    fun forceConflictOnNextPush(noteId: String) = fakeTransport.forceConflictOnNextPush(noteId)

    fun forceRejectedOnNextPush(noteId: String) =
        fakeTransport.forceRejectedOnNextPush(noteId, code = "SIMULATED_REJECTION", message = "Rejected by debug panel")

    fun dropNextResponseFor(opId: String) = fakeTransport.dropNextResponseFor(opId)
    fun expireTokenOnNextPull() = fakeTransport.expireTokenOnNextPull()
    fun forceRateLimitOnNextCall() = fakeTransport.forceRateLimitOnNextCall()
    fun forceAuthFailureOnNextCall() = fakeTransport.forceAuthFailureOnNextCall()

    fun setUseHttpTransport(value: Boolean) = transportSettings.setUseHttpTransport(value)

    fun setBaseUrl(value: String) = transportSettings.setBaseUrl(value)
}
