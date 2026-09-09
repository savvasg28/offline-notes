package com.savvasg.offlinenotes.data.network.http

import com.savvasg.offlinenotes.data.network.dto.DeltaPage
import com.savvasg.offlinenotes.data.network.dto.OpResult
import com.savvasg.offlinenotes.data.network.dto.OutgoingOp
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OfflineNotesApi {
    @POST("push")
    suspend fun push(@Body ops: List<OutgoingOp>): Response<List<OpResult>>

    @GET("pull")
    suspend fun pull(@Query("since") since: String?): Response<DeltaPage>
}
