package com.savvasg.offlinenotes.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savvasg.offlinenotes.data.local.NoteDao
import com.savvasg.offlinenotes.data.local.NoteEntity
import com.savvasg.offlinenotes.data.local.OutboxDao
import com.savvasg.offlinenotes.data.local.OutboxEntity
import com.savvasg.offlinenotes.data.local.SyncMetaDao
import com.savvasg.offlinenotes.servercore.ServerCore
import com.savvasg.offlinenotes.sync.SyncScheduler
import com.savvasg.offlinenotes.sync.transport.TransportSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the debug panel (§10): failure injection, the outbox inspector, the current sync token,
 * and force-sync. Injects [ServerCore] directly rather than going through [com.savvasg.offlinenotes.sync.transport.SyncTransport] —
 * these controls aren't something any real transport implementation would expose to callers,
 * they're test/demo scaffolding for whichever transport happens to be backed by this server-core
 * instance (§9).
 */
@HiltViewModel
class DebugPanelViewModel @Inject constructor(
    private val serverCore: ServerCore,
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

    fun setLatency(minMillis: Long, maxMillis: Long) = serverCore.setLatencyMillis(minMillis..maxMillis)
    fun clearLatency() = serverCore.setLatencyMillis(null)

    fun forceConflictOnNextPush(noteId: String) = serverCore.forceConflictOnNextPush(noteId)
    fun forceRejectedOnNextPush(noteId: String) =
        serverCore.forceRejectedOnNextPush(noteId, code = "SIMULATED_REJECTION", message = "Rejected by debug panel")

    fun dropNextResponseFor(opId: String) = serverCore.dropNextResponseFor(opId)
    fun expireTokenOnNextPull() = serverCore.expireTokenOnNextPull()
    fun forceRateLimitOnNextCall() = serverCore.forceRateLimitOnNextCall()
    fun forceAuthFailureOnNextCall() = serverCore.forceAuthFailureOnNextCall()

    /** "Device B" writing directly to server state — the only way to produce a genuine conflict. */
    fun simulateConcurrentEdit(noteId: String, title: String, body: String) {
        viewModelScope.launch { serverCore.simulateConcurrentEdit(noteId, title, body) }
    }

    fun simulateConcurrentDelete(noteId: String) {
        viewModelScope.launch { serverCore.simulateConcurrentDelete(noteId) }
    }
}
