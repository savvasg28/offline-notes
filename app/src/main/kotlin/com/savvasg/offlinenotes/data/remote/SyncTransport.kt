package com.savvasg.offlinenotes.data.remote

import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.DeltaPage

/**
 * The transport contract, implemented both by a scriptable local stub and by a real HTTP client.
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
