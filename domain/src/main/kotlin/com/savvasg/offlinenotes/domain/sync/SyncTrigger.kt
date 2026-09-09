package com.savvasg.offlinenotes.domain.sync

interface SyncTrigger {
    fun requestSync()

    fun requestImmediateSync()
}
