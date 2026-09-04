package com.savvasg.offlinenotes.core.data

/**
 * The "please sync soon" contract, kept independent of whatever actually schedules the work
 * (WorkManager today) so this module never depends on it — see :sync:work for the implementation.
 */
interface SyncTrigger {
    /** Eventual, debounced: called after every local write. */
    fun requestSync()

    /** Now: called when the user is leaving a screen with a pending edit. */
    fun requestImmediateSync()
}
