package com.savvasg.offlinenotes.di

import com.savvasg.offlinenotes.data.remote.SelectableTransport
import com.savvasg.offlinenotes.data.remote.SyncTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds get [SelectableTransport], which switches between the scriptable stub and real
 * HTTP from the debug panel. Release binds [com.savvasg.offlinenotes.data.remote.http.HttpTransport]
 * directly — the stub isn't compiled into that variant at all.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {
    @Binds
    abstract fun bindSyncTransport(impl: SelectableTransport): SyncTransport
}
