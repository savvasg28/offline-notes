package com.savvasg.offlinenotes.server

import com.savvasg.offlinenotes.servercore.AuthFailureException
import com.savvasg.offlinenotes.servercore.OutgoingOp
import com.savvasg.offlinenotes.servercore.RateLimitException
import com.savvasg.offlinenotes.servercore.ServerCore
import com.savvasg.offlinenotes.servercore.SimulatedNetworkException
import com.savvasg.offlinenotes.servercore.SyncTokenExpiredException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import kotlinx.serialization.Serializable

/**
 * Wraps [ServerCore] in HTTP routes (§9) — the demo path. One implementation of server
 * semantics, two ways to reach it: this module and [com.savvasg.offlinenotes.sync.transport.fake.FakeTransport]
 * (in `:app`) both call the exact same `ServerCore` class, so a divergence between "what the
 * tests prove" and "what the two-device demo shows" is impossible by construction.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::offlineNotesModule)
        .start(wait = true)
}

fun Application.offlineNotesModule() {
    val serverCore = ServerCore()

    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        // Transport-level failures (§7): the client retries the whole batch with the same opIds.
        exception<AuthFailureException> { call, _ -> call.respond(HttpStatusCode.Unauthorized) }
        exception<RateLimitException> { call, _ -> call.respond(HttpStatusCode.TooManyRequests) }
        exception<SimulatedNetworkException> { call, _ -> call.respond(HttpStatusCode.InternalServerError) }
        // Not a transport failure — a distinct signal the client maps back to re-bootstrap (§7).
        exception<SyncTokenExpiredException> { call, _ -> call.respond(HttpStatusCode.Gone) }
    }

    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "ok") }

        post("/push") {
            val ops = call.receive<List<OutgoingOp>>()
            call.respond(serverCore.pushOps(ops))
        }

        get("/pull") {
            val since = call.request.queryParameters["since"]
            call.respond(serverCore.pullDelta(since))
        }

        // Failure injection (§9): "a control endpoint (POST /debug/...) toggles latency, dropped
        // responses, forced conflicts, token expiry" — affects every connected device coherently,
        // since they all hit this one server-core instance.
        route("/debug") {
            post("/latency") {
                val body = call.receive<LatencyRequest>()
                val range = if (body.minMillis != null && body.maxMillis != null) body.minMillis..body.maxMillis else null
                serverCore.setLatencyMillis(range)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/force-conflict/{noteId}") {
                serverCore.forceConflictOnNextPush(call.parameters.getOrFail("noteId"))
                call.respond(HttpStatusCode.NoContent)
            }
            post("/force-rejected/{noteId}") {
                val body = call.receive<ForceRejectedRequest>()
                serverCore.forceRejectedOnNextPush(call.parameters.getOrFail("noteId"), body.code, body.message)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/drop-next-response/{opId}") {
                serverCore.dropNextResponseFor(call.parameters.getOrFail("opId"))
                call.respond(HttpStatusCode.NoContent)
            }
            post("/expire-token") {
                serverCore.expireTokenOnNextPull()
                call.respond(HttpStatusCode.NoContent)
            }
            post("/rate-limit") {
                serverCore.forceRateLimitOnNextCall()
                call.respond(HttpStatusCode.NoContent)
            }
            post("/auth-failure") {
                serverCore.forceAuthFailureOnNextCall()
                call.respond(HttpStatusCode.NoContent)
            }
            post("/simulate-edit/{noteId}") {
                val body = call.receive<SimulateEditRequest>()
                serverCore.simulateConcurrentEdit(call.parameters.getOrFail("noteId"), body.title, body.body)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/simulate-delete/{noteId}") {
                serverCore.simulateConcurrentDelete(call.parameters.getOrFail("noteId"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

@Serializable
data class LatencyRequest(val minMillis: Long?, val maxMillis: Long?)

@Serializable
data class ForceRejectedRequest(val code: String, val message: String)

@Serializable
data class SimulateEditRequest(val title: String, val body: String)
