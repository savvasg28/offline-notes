package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import javax.inject.Inject

class SaveNoteEditUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val syncTrigger: SyncTrigger,
) {
    suspend operator fun invoke(id: String, title: String, body: String, alsoSync: Boolean = false) {
        repository.saveNote(id, title, body)
        if (alsoSync) syncTrigger.requestImmediateSync()
    }
}
