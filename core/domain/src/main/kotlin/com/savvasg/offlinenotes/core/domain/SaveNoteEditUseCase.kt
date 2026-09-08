package com.savvasg.offlinenotes.core.domain

import com.savvasg.offlinenotes.core.data.NoteRepository
import com.savvasg.offlinenotes.core.data.SyncTrigger
import javax.inject.Inject

/**
 * Persists an edit and, only when leaving the editor with something unsynced, asks for an
 * immediate sync rather than waiting out the debounce. The one use case in this app that composes
 * two collaborators instead of forwarding to one — the editor ViewModel used to hold both
 * [NoteRepository] and [SyncTrigger] itself just to sequence this.
 */
class SaveNoteEditUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(id: String, title: String, body: String, alsoSync: Boolean = false) {
        repository.saveNote(id, title, body)
        if (alsoSync) syncTrigger.requestImmediateSync()
    }
}
