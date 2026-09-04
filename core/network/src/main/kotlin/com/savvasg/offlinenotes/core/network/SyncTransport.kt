package com.savvasg.offlinenotes.core.network

import com.savvasg.offlinenotes.core.network.dto.OutgoingOp
import com.savvasg.offlinenotes.core.network.dto.OpResult
import com.savvasg.offlinenotes.core.network.dto.DeltaPage

interface SyncTransport {
    suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult>
    suspend fun pullDelta(since: String?): DeltaPage
}
