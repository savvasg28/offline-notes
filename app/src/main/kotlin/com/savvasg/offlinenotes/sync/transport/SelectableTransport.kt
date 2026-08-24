package com.savvasg.offlinenotes.sync.transport

import com.savvasg.offlinenotes.servercore.DeltaPage
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OutgoingOp
import com.savvasg.offlinenotes.sync.transport.fake.FakeTransport
import com.savvasg.offlinenotes.sync.transport.http.HttpTransport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The debug-panel toggle (§9) lives here: the sync engine only ever depends on [SyncTransport],
 * so swapping which real implementation backs it never touches [com.savvasg.offlinenotes.sync.SyncEngine].
 */
@Singleton
class SelectableTransport @Inject constructor(
    private val settings: TransportSettings,
    private val fakeTransport: FakeTransport,
    private val httpTransport: HttpTransport,
) : SyncTransport {

    private fun current(): SyncTransport = if (settings.useHttpTransport.value) httpTransport else fakeTransport

    override suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> = current().pushOps(ops)
    override suspend fun pullDelta(since: String?): DeltaPage = current().pullDelta(since)
}
