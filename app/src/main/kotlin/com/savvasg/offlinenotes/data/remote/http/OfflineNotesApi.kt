package com.savvasg.offlinenotes.data.remote.http

import com.savvasg.offlinenotes.data.remote.dto.DeltaPage
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
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
