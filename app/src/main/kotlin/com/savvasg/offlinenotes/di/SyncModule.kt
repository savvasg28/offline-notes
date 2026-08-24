package com.savvasg.offlinenotes.di

import android.content.Context
import androidx.work.WorkManager
import com.savvasg.offlinenotes.servercore.ServerCore
import com.savvasg.offlinenotes.sync.transport.SelectableTransport
import com.savvasg.offlinenotes.sync.transport.SyncTransport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    // FakeTransport is the zero-setup default; SelectableTransport is what SyncEngine actually
    // sees — it picks FakeTransport vs HttpTransport per TransportSettings, driven by the debug
    // panel (§9), without SyncEngine ever knowing a choice is being made.
    @Binds
    abstract fun bindSyncTransport(impl: SelectableTransport): SyncTransport

    companion object {
        @Provides
        @Singleton
        fun provideServerCore(): ServerCore = ServerCore()

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}
