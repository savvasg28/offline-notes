package com.savvasg.offlinenotes.sync.transport.http

import com.savvasg.offlinenotes.servercore.DeltaPage
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OutgoingOp
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** The §7 contract over HTTP. Raw [Response] so [HttpTransport] can map status codes itself. */
interface OfflineNotesApi {
    @POST("push")
    suspend fun pushOps(@Body ops: List<OutgoingOp>): Response<List<OpResult>>

    @GET("pull")
    suspend fun pullDelta(@Query("since") since: String?): Response<DeltaPage>
}
