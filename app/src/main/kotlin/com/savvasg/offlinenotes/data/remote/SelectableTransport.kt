package com.savvasg.offlinenotes.data.remote

import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.DeltaPage
import com.savvasg.offlinenotes.data.remote.fake.FakeTransport
import com.savvasg.offlinenotes.data.remote.http.HttpTransport
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
