package com.savvasg.offlinenotes.sync.transport

import com.savvasg.offlinenotes.servercore.DeltaPage
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OutgoingOp

/**
 * The transport contract (§7). Both [FakeTransport][com.savvasg.offlinenotes.sync.transport.fake.FakeTransport]
 * and `HttpTransport` implement this against the identical `server-core` semantics — one
 * contract, two ways to reach it.
 *
 * Contract rules an implementation must honour:
 * - Per-op independence: one conflict never fails the batch; results come back in request order.
 * - Idempotency: a replayed opId returns the stored original result, without re-executing.
 * - Auth failure, rate limit, and server error are exceptions — retry the whole batch with the
 *   same opIds. Conflict and Rejected are results, not errors.
 * - [pullDelta]'s `syncToken` is opaque: store it, echo it, never parse it. Null means bootstrap.
 */
interface SyncTransport {
    suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult>
    suspend fun pullDelta(since: String?): DeltaPage
}
