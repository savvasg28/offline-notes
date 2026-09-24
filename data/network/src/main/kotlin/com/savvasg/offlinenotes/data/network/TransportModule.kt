package com.savvasg.offlinenotes.data.network

import com.savvasg.offlinenotes.data.network.http.HttpSyncTransport
import com.savvasg.offlinenotes.domain.sync.SyncTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface TransportModule {
    @Binds
    fun bindSyncTransport(impl: HttpSyncTransport): SyncTransport
}
