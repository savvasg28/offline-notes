package com.savvasg.offlinenotes.core.sync

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
class SyncSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        workManager = WorkManager.getInstance(context)
        scheduler = SyncScheduler(workManager)
    }

    @Test
    fun `long_editing_session_produces_one_request_per_window - many debounced schedules coalesce into one`() {
        repeat(20) { scheduler.scheduleDebouncedSync() }

        val infos = workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_WORK_NAME).get()

        assertThat(infos).hasSize(1)
        assertThat(infos.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `expediteSync replaces a still-waiting debounced request rather than stacking a second one`() {
        scheduler.scheduleDebouncedSync()
        scheduler.expediteSync()

        val infos = workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_WORK_NAME).get()

        assertThat(infos).hasSize(1)
    }

    @Test
    fun `schedulePeriodicBackstop is safe to call on every app start`() {
        repeat(3) { scheduler.schedulePeriodicBackstop() }

        val infos = workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_PERIODIC_WORK_NAME).get()

        assertThat(infos).hasSize(1)
    }

    @Test
    fun `debounced and periodic work are tracked under separate unique names`() {
        scheduler.scheduleDebouncedSync()
        scheduler.schedulePeriodicBackstop()

        assertThat(workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_WORK_NAME).get()).hasSize(1)
        assertThat(workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_PERIODIC_WORK_NAME).get()).hasSize(1)
    }
}
