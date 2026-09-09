package com.savvasg.offlinenotes.data.network.http

import com.savvasg.offlinenotes.data.network.dto.OpResult
import com.savvasg.offlinenotes.data.network.dto.toOpOutcome
import com.savvasg.offlinenotes.data.network.dto.toOutgoingOp
import com.savvasg.offlinenotes.data.network.dto.toRemoteChangePage
import com.savvasg.offlinenotes.domain.model.FrozenOutboxOp
import com.savvasg.offlinenotes.domain.sync.AuthFailureException
import com.savvasg.offlinenotes.domain.sync.NetworkException
import com.savvasg.offlinenotes.domain.sync.OpOutcome
import com.savvasg.offlinenotes.domain.sync.RateLimitException
import com.savvasg.offlinenotes.domain.sync.RemoteChangePage
import com.savvasg.offlinenotes.domain.sync.SyncTokenExpiredException
import com.savvasg.offlinenotes.domain.sync.SyncTransport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Response

@Singleton
class HttpTransport @Inject constructor(private val api: OfflineNotesApi) : SyncTransport {

    override suspend fun pushOps(ops: List<FrozenOutboxOp>): List<OpOutcome> =
        unwrap(runCatchingIo { api.pushOps(ops.map { it.toOutgoingOp() }) })
            .map(OpResult::toOpOutcome)

    override suspend fun pullDelta(since: String?): RemoteChangePage =
        unwrap(runCatchingIo { api.pullDelta(since) }, isPull = true).toRemoteChangePage()

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

