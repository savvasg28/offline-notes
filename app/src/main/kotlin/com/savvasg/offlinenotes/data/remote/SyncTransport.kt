package com.savvasg.offlinenotes.data.remote

import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.DeltaPage

interface SyncTransport {
    suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult>
    suspend fun pullDelta(since: String?): DeltaPage
}
