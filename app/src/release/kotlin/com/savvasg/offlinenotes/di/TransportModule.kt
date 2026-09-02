package com.savvasg.offlinenotes.di

import com.savvasg.offlinenotes.data.remote.SyncTransport
import com.savvasg.offlinenotes.data.remote.http.HttpTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {
    @Binds
    abstract fun bindSyncTransport(impl: HttpTransport): SyncTransport
}
