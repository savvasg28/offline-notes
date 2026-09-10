package com.savvasg.offlinenotes.domain.usecase

import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import javax.inject.Inject

class RequestSyncUseCase @Inject constructor(private val syncTrigger: SyncTrigger) {
    operator fun invoke() = syncTrigger.requestImmediateSync()
}
