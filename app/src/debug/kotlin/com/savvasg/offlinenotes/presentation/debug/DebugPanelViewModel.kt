package com.savvasg.offlinenotes.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.data.local.dao.NoteDao
import com.savvasg.offlinenotes.data.local.dao.OutboxDao
import com.savvasg.offlinenotes.data.local.dao.SyncMetaDao
import com.savvasg.offlinenotes.data.local.entity.NoteEntity
import com.savvasg.offlinenotes.data.local.entity.OutboxEntity
import com.savvasg.offlinenotes.data.remote.SyncTransport
import com.savvasg.offlinenotes.data.remote.TransportSettings
import com.savvasg.offlinenotes.data.remote.fake.FakeTransport
import com.savvasg.offlinenotes.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the debug panel: failure injection, the outbox inspector, the current sync token, and
 * force-sync. Injects [FakeTransport] directly rather than going through
 * [com.savvasg.offlinenotes.data.remote.SyncTransport] — these controls aren't part of the
 * transport contract any real implementation would expose to callers, they're demo/test
 * scaffolding specific to the stub. (They have no effect when the debug panel's "Use HTTP
 * transport" toggle is on — that path is real HTTP against whatever Mockoon/real server is
 * configured, which has its own way of scripting responses.)
 */
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

    /** One tap: queues a Conflict for this note's next push, and its remote content — no
     * separate "simulate Device B" step needed, since there's no real server state to write to. */
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
