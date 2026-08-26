package com.savvasg.offlinenotes.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.data.local.NoteDao
import com.savvasg.offlinenotes.data.local.NoteEntity
import com.savvasg.offlinenotes.data.local.OutboxDao
import com.savvasg.offlinenotes.data.local.OutboxEntity
import com.savvasg.offlinenotes.data.local.SyncMetaDao
import com.savvasg.offlinenotes.sync.SyncScheduler
import com.savvasg.offlinenotes.sync.transport.TransportSettings
import com.savvasg.offlinenotes.sync.transport.fake.FakeTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the debug panel: failure injection, the outbox inspector, the current sync token, and
 * force-sync. Injects [FakeTransport] directly rather than going through
 * [com.savvasg.offlinenotes.sync.transport.SyncTransport] — these controls aren't part of the
 * transport contract any real implementation would expose to callers, they're demo/test
 * scaffolding specific to the stub. (They have no effect when the debug panel's "Use HTTP
 * transport" toggle is on — that path is real HTTP against whatever Mockoon/real server is
 * configured, which has its own way of scripting responses.)
 */
@HiltViewModel
class DebugPanelViewModel @Inject constructor(
    private val fakeTransport: FakeTransport,
    outboxDao: OutboxDao,
    syncMetaDao: SyncMetaDao,
    noteDao: NoteDao,
    private val syncScheduler: SyncScheduler,
    val transportSettings: TransportSettings,
) : ViewModel() {

    val outboxOps: StateFlow<List<OutboxEntity>> =
        outboxDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncToken: StateFlow<String?> =
        syncMetaDao.observeSyncToken().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeNotes: StateFlow<List<NoteEntity>> =
        noteDao.observeActiveNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}
