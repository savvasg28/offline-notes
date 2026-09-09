package com.savvasg.offlinenotes.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncTriggerTest {

    private lateinit var workManager: WorkManager
    private lateinit var syncTrigger: WorkManagerSyncTrigger

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        workManager = WorkManager.getInstance(context)
        syncTrigger = WorkManagerSyncTrigger(workManager)
    }

    @Test
    fun `long_editing_session_produces_one_request_per_window - many debounced schedules coalesce into one`() {
        repeat(20) { syncTrigger.requestSync() }

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME).get()

        assertThat(infos).hasSize(1)
        assertThat(infos.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `expediteSync replaces a still-waiting debounced request rather than stacking a second one`() {
        syncTrigger.requestSync()
        syncTrigger.requestImmediateSync()

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME).get()

        assertThat(infos).hasSize(1)
    }

    @Test
    fun `ensurePeriodicSync is safe to call on every app start`() {
        repeat(3) { syncTrigger.ensurePeriodicSync() }

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_PERIODIC_WORK_NAME).get()

        assertThat(infos).hasSize(1)
    }

    @Test
    fun `debounced and periodic work are tracked under separate unique names`() {
        syncTrigger.requestSync()
        syncTrigger.ensurePeriodicSync()

        assertThat(workManager.getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME).get()).hasSize(1)
        assertThat(workManager.getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_PERIODIC_WORK_NAME).get()).hasSize(1)
    }
}
