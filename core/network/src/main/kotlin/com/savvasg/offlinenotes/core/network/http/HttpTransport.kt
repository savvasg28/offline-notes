package com.savvasg.offlinenotes.core.network.http

import com.savvasg.offlinenotes.core.network.dto.AuthFailureException
import com.savvasg.offlinenotes.core.network.dto.DeltaPage
import com.savvasg.offlinenotes.core.network.dto.OpResult
import com.savvasg.offlinenotes.core.network.dto.OutgoingOp
import com.savvasg.offlinenotes.core.network.dto.RateLimitException
import com.savvasg.offlinenotes.core.network.dto.NetworkException
import com.savvasg.offlinenotes.core.network.dto.SyncTokenExpiredException
import com.savvasg.offlinenotes.core.network.SyncTransport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Response

@Singleton
class HttpTransport @Inject constructor(private val api: OfflineNotesApi) : SyncTransport {

    override suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> =
        unwrap(runCatchingIo { api.pushOps(ops) })

    override suspend fun pullDelta(since: String?): DeltaPage =
        unwrap(runCatchingIo { api.pullDelta(since) }, isPull = true)

    private suspend fun <T> runCatchingIo(call: suspend () -> Response<T>): Response<T> = try {
        call()
    } catch (e: IOException) {
        throw NetworkException(e.message ?: "network error")
    }

    private fun <T> unwrap(response: Response<T>, isPull: Boolean = false): T = when (response.code()) {
        in 200..299 -> response.body() ?: throw NetworkException("empty ${response.raw().request.method} response")
        401 -> throw AuthFailureException()
        429 -> throw RateLimitException()
        410 -> if (isPull) throw SyncTokenExpiredException() else throw NetworkException("unexpected 410")
        else -> throw NetworkException("server responded ${response.code()}")
    }
}
