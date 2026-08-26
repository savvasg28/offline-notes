package com.savvasg.offlinenotes.sync.transport.http

import com.savvasg.offlinenotes.sync.transport.DeltaPage
import com.savvasg.offlinenotes.sync.transport.OpResult
import com.savvasg.offlinenotes.sync.transport.OutgoingOp
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** The wire contract over HTTP. Raw [Response] so [HttpTransport] can map status codes itself. */
interface OfflineNotesApi {
    @POST("push")
    suspend fun pushOps(@Body ops: List<OutgoingOp>): Response<List<OpResult>>

    @GET("pull")
    suspend fun pullDelta(@Query("since") since: String?): Response<DeltaPage>
}
