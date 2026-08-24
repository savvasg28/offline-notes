package com.savvasg.offlinenotes.sync.transport.http

import com.savvasg.offlinenotes.servercore.AuthFailureException
import com.savvasg.offlinenotes.servercore.DeltaPage
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OutgoingOp
import com.savvasg.offlinenotes.servercore.RateLimitException
import com.savvasg.offlinenotes.servercore.SimulatedNetworkException
import com.savvasg.offlinenotes.servercore.SyncTokenExpiredException
import com.savvasg.offlinenotes.sync.transport.SyncTransport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Response

/**
 * Retrofit implementation of the §7 contract against the `:server` Ktor module (§9 demo path).
 * Maps HTTP status codes back to the exact exception types [com.savvasg.offlinenotes.servercore.ServerCore]
 * throws in-process, so [com.savvasg.offlinenotes.sync.SyncEngine] handles a transport failure
 * identically regardless of which [SyncTransport] backs it.
 */
@Singleton
class HttpTransport @Inject constructor(private val api: OfflineNotesApi) : SyncTransport {

    override suspend fun pushOps(ops: List<OutgoingOp>): List<OpResult> =
        unwrap(runCatchingIo { api.pushOps(ops) })

    override suspend fun pullDelta(since: String?): DeltaPage =
        unwrap(runCatchingIo { api.pullDelta(since) }, isPull = true)

    private suspend fun <T> runCatchingIo(call: suspend () -> Response<T>): Response<T> = try {
        call()
    } catch (e: IOException) {
        throw SimulatedNetworkException(e.message ?: "network error")
    }

    private fun <T> unwrap(response: Response<T>, isPull: Boolean = false): T = when (response.code()) {
        in 200..299 -> response.body() ?: throw SimulatedNetworkException("empty ${response.raw().request.method} response")
        401 -> throw AuthFailureException()
        429 -> throw RateLimitException()
        410 -> if (isPull) throw SyncTokenExpiredException() else throw SimulatedNetworkException("unexpected 410")
        else -> throw SimulatedNetworkException("server responded ${response.code()}")
    }
}
