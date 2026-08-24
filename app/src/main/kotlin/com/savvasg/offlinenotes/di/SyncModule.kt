package com.savvasg.offlinenotes.di

import com.savvasg.offlinenotes.servercore.ServerCore
import com.savvasg.offlinenotes.sync.transport.SyncTransport
import com.savvasg.offlinenotes.sync.transport.fake.FakeTransport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    // Default transport (§9): FakeTransport calling server-core in-process, zero setup. A build
    // flavour or debug-panel toggle can swap this for HttpTransport later without touching the
    // sync engine, which only ever depends on the SyncTransport interface.
    @Binds
    abstract fun bindSyncTransport(impl: FakeTransport): SyncTransport

    companion object {
        @Provides
        @Singleton
        fun provideServerCore(): ServerCore = ServerCore()
    }
}
