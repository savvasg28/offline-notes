package com.savvasg.offlinenotes.data.sync

import android.content.Context
import androidx.work.WorkManager
import com.savvasg.offlinenotes.domain.sync.SyncTrigger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncTriggerModule {
    @Binds
    abstract fun bindSyncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger
}
