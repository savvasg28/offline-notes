package com.savvasg.offlinenotes.server

import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.servercore.DeltaPage
import com.savvasg.offlinenotes.servercore.OpResult
import com.savvasg.offlinenotes.servercore.OpType
import com.savvasg.offlinenotes.servercore.OutgoingOp
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.Test

/**
 * Routes-level tests: wiring and HTTP status-code mapping only. `ServerCore`'s own semantics
 * (CAS, dedupe, change log, failure injection) are already covered by `server-core`'s 13 tests —
 * duplicating that here would just be the same assertions behind an HTTP hop.
 */
class ApplicationTest {

    private fun createOp(opId: String = "op-1", noteId: String = "note-1") =
        OutgoingOp(opId, OpType.CREATE, noteId, baseVersion = null, title = "T", body = "B")

    @Test
    fun `health check responds ok`() = testApplication {
        application { offlineNotesModule() }
        val response = client.get("/health")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `push applies a CREATE op and returns Applied`() = testApplication {
        application { offlineNotesModule() }
        val client = createJsonClient()

        val response = client.post("/push") {
            contentType(ContentType.Application.Json)
            setBody(listOf(createOp()))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val results = response.body<List<OpResult>>()
        assertThat(results.single()).isInstanceOf(OpResult.Applied::class.java)
    }

    @Test
    fun `bootstrap pull returns notes pushed earlier`() = testApplication {
        application { offlineNotesModule() }
        val client = createJsonClient()

        client.post("/push") {
            contentType(ContentType.Application.Json)
            setBody(listOf(createOp()))
        }

        val response = client.get("/pull")
        val page = response.body<DeltaPage>()

        assertThat(page.notes).hasSize(1)
        assertThat(page.notes.single().noteId).isEqualTo("note-1")
    }

    @Test
    fun `forced conflict via the debug endpoint surfaces as a Conflict result`() = testApplication {
        application { offlineNotesModule() }
        val client = createJsonClient()

        val created = client.post("/push") {
            contentType(ContentType.Application.Json)
            setBody(listOf(createOp()))
        }.body<List<OpResult>>().single() as OpResult.Applied

        client.post("/debug/force-conflict/note-1")

        val update = OutgoingOp("op-2", OpType.UPDATE, "note-1", created.newVersion, "T2", "B2")
        val result = client.post("/push") {
            contentType(ContentType.Application.Json)
            setBody(listOf(update))
        }.body<List<OpResult>>().single()

        assertThat(result).isInstanceOf(OpResult.Conflict::class.java)
    }

    @Test
    fun `forced auth failure maps to 401`() = testApplication {
        application { offlineNotesModule() }
        val client = createJsonClient()

        client.post("/debug/auth-failure")
        val response = client.post("/push") {
            contentType(ContentType.Application.Json)
            setBody(listOf(createOp()))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `expired token maps to 410 Gone`() = testApplication {
        application { offlineNotesModule() }
        val client = createJsonClient()

        client.post("/debug/expire-token")
        val response = client.get("/pull?since=1")

        assertThat(response.status).isEqualTo(HttpStatusCode.Gone)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.createJsonClient() =
        createClient { install(ContentNegotiation) { json() } }
}
