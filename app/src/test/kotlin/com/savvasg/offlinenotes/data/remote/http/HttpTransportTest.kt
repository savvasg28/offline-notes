package com.savvasg.offlinenotes.data.remote.http

import com.google.common.truth.Truth.assertThat
import com.savvasg.offlinenotes.data.remote.dto.AuthFailureException
import com.savvasg.offlinenotes.data.remote.dto.DeltaPage
import com.savvasg.offlinenotes.data.remote.dto.OpResult
import com.savvasg.offlinenotes.data.remote.dto.OutgoingOp
import com.savvasg.offlinenotes.data.remote.dto.RateLimitException
import com.savvasg.offlinenotes.data.remote.dto.NetworkException
import com.savvasg.offlinenotes.data.remote.dto.SyncTokenExpiredException
import com.savvasg.offlinenotes.domain.model.OpType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class HttpTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: HttpTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        transport = HttpTransport(retrofit.create(OfflineNotesApi::class.java))
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (ignored: Exception) {
        }
    }

    private fun createOp() = OutgoingOp("op-1", OpType.CREATE, "note-1", baseVersion = null, title = "T", body = "B")

    @Test
    fun `pushOps serializes the request and deserializes an Applied result`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"type":"Applied","opId":"op-1","newVersion":"1"}]""")
                .setHeader("Content-Type", "application/json"),
        )

        val results = transport.pushOps(listOf(createOp()))

        assertThat(results.single()).isEqualTo(OpResult.Applied("op-1", "1"))
        val sentBody = server.takeRequest().body.readUtf8()
        assertThat(sentBody).contains("\"opId\":\"op-1\"")
        assertThat(sentBody).contains("\"noteId\":\"note-1\"")
    }

    @Test
    fun `pullDelta deserializes a DeltaPage`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"notes":[],"nextSyncToken":"5","hasMore":false}""")
                .setHeader("Content-Type", "application/json"),
        )

        val page = transport.pullDelta(since = "1")

        assertThat(page.nextSyncToken).isEqualTo("5")
        assertThat(page.hasMore).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/pull?since=1")
    }

    @Test
    fun `401 maps to AuthFailureException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            transport.pushOps(listOf(createOp()))
            throw AssertionError("expected AuthFailureException")
        } catch (expected: AuthFailureException) {
        }
    }

    @Test
    fun `429 maps to RateLimitException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            transport.pushOps(listOf(createOp()))
            throw AssertionError("expected RateLimitException")
        } catch (expected: RateLimitException) {
        }
    }

    @Test
    fun `410 on pull maps to SyncTokenExpiredException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(410))
        try {
            transport.pullDelta(since = "1")
            throw AssertionError("expected SyncTokenExpiredException")
        } catch (expected: SyncTokenExpiredException) {
        }
    }

    @Test
    fun `500 maps to NetworkException - a whole-batch-retry signal`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            transport.pushOps(listOf(createOp()))
            throw AssertionError("expected NetworkException")
        } catch (expected: NetworkException) {
        }
    }

    @Test
    fun `a dropped connection maps to NetworkException rather than an unhandled IOException`() = runTest {
        server.shutdown()
        try {
            transport.pushOps(listOf(createOp()))
            throw AssertionError("expected NetworkException")
        } catch (expected: NetworkException) {
        }
    }
}
