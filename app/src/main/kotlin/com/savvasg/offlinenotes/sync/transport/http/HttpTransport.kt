package com.savvasg.offlinenotes.sync.transport.http

import com.savvasg.offlinenotes.sync.transport.AuthFailureException
import com.savvasg.offlinenotes.sync.transport.DeltaPage
import com.savvasg.offlinenotes.sync.transport.OpResult
import com.savvasg.offlinenotes.sync.transport.OutgoingOp
import com.savvasg.offlinenotes.sync.transport.RateLimitException
import com.savvasg.offlinenotes.sync.transport.SimulatedNetworkException
import com.savvasg.offlinenotes.sync.transport.SyncTokenExpiredException
import com.savvasg.offlinenotes.sync.transport.SyncTransport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Response

/**
 * Retrofit implementation of the wire contract against a real HTTP server — a Mockoon-mocked
 * environment for local dev/demo, or a genuine backend. This repo doesn't implement that server;
 * it only needs to be a correct client of it. Maps HTTP status codes to the same exception types
 * [com.savvasg.offlinenotes.sync.transport.fake.FakeTransport] throws for its own forced
 * failures, so [com.savvasg.offlinenotes.sync.SyncEngine] handles a transport failure identically
 * regardless of which [SyncTransport] backs it.
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
