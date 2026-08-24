package com.savvasg.offlinenotes.sync.transport.fake

import com.savvasg.offlinenotes.servercore.DeltaPage
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OutgoingOp
import com.savvasg.offlinenotes.servercore.ServerCore
import com.savvasg.offlinenotes.sync.transport.SyncTransport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The default transport (§9): calls [ServerCore] directly, in-process — zero setup, and what
 * the instrumented scenario tests run against. A thin pass-through by design: all server
 * semantics, including failure injection, live in `server-core` so `FakeTransport` and the real
 * `HttpTransport` are provably exercising the same contract, not two divergent fakes.
 *
 * The debug panel injects [ServerCore] directly for its failure-injection controls rather than
 * going through this interface — those controls aren't part of the [SyncTransport] contract any
 * real transport would expose to callers.
 */
@Singleton
class FakeTransport @Inject constructor(private val serverCore: ServerCore) : SyncTransport {
    override suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> = serverCore.pushOps(ops)
    override suspend fun pullDelta(since: String?): DeltaPage = serverCore.pullDelta(since)
}
