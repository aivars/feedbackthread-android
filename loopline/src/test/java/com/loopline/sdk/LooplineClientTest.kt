package com.loopline.sdk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

public class LooplineClientTest {
    @Test
    public fun submitsDocumentedPayloadAndIdempotencyKey(): Unit = runBlocking {
        lateinit var connection: FakeHttpURLConnection
        val client = LooplineClient(
            configuration = LooplineConfiguration(
                baseUrl = "https://example.com",
                projectKey = "project-key",
                source = "android",
            ),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 201, successResponse).also { connection = it }
            },
        )
        val feedback = client.submit(
            submission = LooplineFeedbackSubmission(
                kind = LooplineFeedbackKind.REQUEST,
                title = "Schedule by weekday",
                text = "Please add weekday schedules.",
                appVersion = "1.0.0 (4)",
                externalUserId = "user-123",
            ),
            idempotencyKey = "stable-request-id",
        )

        assertEquals("POST", connection.requestMethod)
        assertEquals("/v1/projects/project-key/feedback", connection.url.path)
        assertEquals("stable-request-id", connection.getRequestProperty("Idempotency-Key"))
        assertTrue(connection.writtenBody.contains("\"kind\":\"Requests\""))
        assertTrue(connection.writtenBody.contains("\"source\":\"android\""))
        assertTrue(connection.writtenBody.contains("\"title\":\"Schedule by weekday\""))
        assertTrue(connection.writtenBody.contains("\"appVersion\":\"1.0.0 (4)\""))
        assertTrue(connection.writtenBody.contains("\"externalUserId\":\"user-123\""))
        assertTrue(!connection.writtenBody.contains("customerTier"))
        assertEquals("FDBK-test", feedback.id)
        assertEquals(LooplineFeedbackKind.REQUEST, feedback.kind)
        assertEquals("Submitted", feedback.status)
    }

    @Test
    public fun surfacesServerErrorMessage(): Unit = runBlocking {
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "wrong-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(
                    url = url,
                    statusCode = 404,
                    responseBody = """{"error":{"code":"not_found","message":"Project was not found."}}""",
                )
            },
        )

        try {
            client.submit(LooplineFeedbackSubmission(LooplineFeedbackKind.BUG, "Crash", "It crashed."))
            fail("Expected a server error")
        } catch (error: LooplineException.Server) {
            assertEquals(404, error.statusCode)
            assertEquals("Project was not found.", error.message)
        }
    }

    @Test
    public fun submissionEncodesCustomerTierWhenProvidedAndOmitsItOtherwise(): Unit = runBlocking {
        lateinit var connection: FakeHttpURLConnection
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 201, successResponse).also { connection = it }
            },
        )

        client.submit(
            LooplineFeedbackSubmission(
                kind = LooplineFeedbackKind.BUG,
                title = "Crash",
                text = "It crashed.",
                customerTier = LooplineCustomerTier.Paying,
            ),
        )
        assertTrue(connection.writtenBody.contains("\"customerTier\":\"paying\""))

        lateinit var omittingConnection: FakeHttpURLConnection
        val omittingClient = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 201, successResponse).also { omittingConnection = it }
            },
        )

        omittingClient.submit(
            LooplineFeedbackSubmission(kind = LooplineFeedbackKind.BUG, title = "Crash", text = "It crashed."),
        )
        assertTrue(!omittingConnection.writtenBody.contains("customerTier"))
    }

    @Test
    public fun submissionEncodesCustomCustomerTierByItsRawLabel(): Unit = runBlocking {
        lateinit var connection: FakeHttpURLConnection
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 201, successResponse).also { connection = it }
            },
        )

        client.submit(
            LooplineFeedbackSubmission(
                kind = LooplineFeedbackKind.BUG,
                title = "Crash",
                text = "It crashed.",
                customerTier = LooplineCustomerTier.Custom("enterprise"),
            ),
        )

        assertTrue(connection.writtenBody.contains("\"customerTier\":\"enterprise\""))
    }

    @Test
    public fun loadsAndroidRequestFeedWithVoterIdentity(): Unit = runBlocking {
        lateinit var connection: FakeHttpURLConnection
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 200, requestsResponse(shippedInVersion = null)).also { connection = it }
            },
        )

        val requests = client.requests("user-123")

        assertEquals("GET", connection.requestMethod)
        assertEquals(
            "https://example.com/v1/projects/project-key/requests?platform=android",
            connection.url.toString(),
        )
        assertEquals("user-123", connection.getRequestProperty("X-FeedbackThread-User"))
        assertEquals(1, requests.size)
        assertEquals("FDBK-request", requests.first().id)
        assertEquals(LooplineRequestTarget.ANDROID, requests.first().target)
        assertTrue(requests.first().voted)
        assertNull(requests.first().shippedInVersion)
    }

    @Test
    public fun decodesShippedInVersionWhenTheRequestFeedReportsAPublishedRelease(): Unit = runBlocking {
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 200, requestsResponse(shippedInVersion = "2.4.0"))
            },
        )

        val requests = client.requests("user-123")

        assertEquals("2.4.0", requests.first().shippedInVersion)
    }

    @Test
    public fun decodesAMissingShippedInVersionAsNull(): Unit = runBlocking {
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 200, requestsResponse(shippedInVersion = null))
            },
        )

        val requests = client.requests("user-123")

        assertNull(requests.first().shippedInVersion)
    }

    @Test
    public fun addsAndRemovesAndroidVotes(): Unit = runBlocking {
        val connections = mutableListOf<FakeHttpURLConnection>()
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                val removing = connections.isNotEmpty()
                FakeHttpURLConnection(
                    url,
                    200,
                    if (removing) removedVoteResponse else addedVoteResponse,
                ).also(connections::add)
            },
        )

        val added = client.setVote("FDBK-request", true, "user-123")
        val removed = client.setVote("FDBK-request", false, "user-123")

        assertEquals("POST", connections[0].requestMethod)
        assertEquals("DELETE", connections[1].requestMethod)
        assertEquals(
            "https://example.com/v1/projects/project-key/requests/FDBK-request/vote?platform=android",
            connections[0].url.toString(),
        )
        assertEquals("user-123", connections[0].getRequestProperty("X-FeedbackThread-User"))
        assertTrue(connections[0].writtenBody.isEmpty())
        assertTrue(added.voted)
        assertEquals(13, added.votes)
        assertTrue(!removed.voted)
        assertEquals(12, removed.votes)
    }

    @Test
    public fun voteCarriesCustomerTierInTheBodyWhenProvided(): Unit = runBlocking {
        lateinit var connection: FakeHttpURLConnection
        val client = LooplineClient(
            configuration = LooplineConfiguration("https://example.com", "project-key", "android"),
            connectionFactory = { url ->
                FakeHttpURLConnection(url, 200, addedVoteResponse).also { connection = it }
            },
        )

        client.setVote(
            requestId = "FDBK-request",
            voted = true,
            externalUserId = "user-123",
            customerTier = LooplineCustomerTier.Free,
        )

        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertTrue(connection.writtenBody.contains("\"customerTier\":\"free\""))
    }

    @Test
    public fun rejectsEmptyProjectKeyBeforeSending(): Unit = runBlocking {
        val client = LooplineClient(
            LooplineConfiguration("https://example.com", "  ", "android"),
        )

        try {
            client.submit(LooplineFeedbackSubmission(LooplineFeedbackKind.BUG, "Crash", "It crashed."))
            fail("Expected an invalid configuration error")
        } catch (error: LooplineException.InvalidConfiguration) {
            assertEquals("A FeedbackThread project key is required.", error.message)
        }
    }

    @Test
    public fun submitsThroughLiveStagingWhenConfigured(): Unit = runBlocking {
        val baseURL = System.getenv("FEEDBACKTHREAD_LIVE_BASE_URL")
            ?: System.getenv("LOOPLINE_LIVE_BASE_URL")
            ?: return@runBlocking
        val projectKey = System.getenv("FEEDBACKTHREAD_LIVE_PROJECT_KEY")
            ?: System.getenv("LOOPLINE_LIVE_PROJECT_KEY")
            ?: return@runBlocking
        val client = LooplineClient(
            LooplineConfiguration(baseURL, projectKey, "android"),
        )

        val feedback = client.submit(
            LooplineFeedbackSubmission(
                kind = LooplineFeedbackKind.BUG,
                title = "Android SDK live integration test",
                text = "Created by the Loopline Android package integration test.",
                appVersion = "Loopline Android SDK alpha",
            ),
            idempotencyKey = "android-live-${System.currentTimeMillis()}",
        )

        assertEquals("android", feedback.source)
        assertEquals("Android SDK live integration test", feedback.title)
        assertEquals("Submitted", feedback.status)

        val requests = client.requests("android-live-reader")
        assertTrue(requests.isNotEmpty())
    }

    private companion object {
        val successResponse: String = """
            {
              "feedback": {
                "id": "FDBK-test",
                "kind": "Requests",
                "source": "android",
                "title": "Schedule by weekday",
                "excerpt": "Please add weekday schedules.",
                "version": "1.0.0 (4)",
                "status": "Submitted",
                "count": 1,
                "note": "",
                "responseDraft": "",
                "responseState": "Not started",
                "createdAt": "2026-07-16T12:00:00.000Z",
                "updatedAt": "2026-07-16T12:00:00.000Z"
              }
            }
        """.trimIndent()

        fun requestsResponse(shippedInVersion: String?): String {
            val shippedInVersionJson = if (shippedInVersion != null) "\"$shippedInVersion\"" else "null"
            return """
                {
                  "requests": [
                    {
                      "id": "FDBK-request",
                      "title": "More training plans",
                      "description": "Add a longer progression for experienced users.",
                      "votes": 12,
                      "target": "android",
                      "status": "Planned",
                      "voted": true,
                      "updatedAt": "2026-07-16T12:00:00.000Z",
                      "shippedInVersion": $shippedInVersionJson
                    }
                  ]
                }
            """.trimIndent()
        }

        val addedVoteResponse: String =
            """{"feedbackId":"FDBK-request","votes":13,"voted":true}"""
        val removedVoteResponse: String =
            """{"feedbackId":"FDBK-request","votes":12,"voted":false}"""
    }
}

private class FakeHttpURLConnection(
    url: URL,
    private val statusCode: Int,
    responseBody: String,
) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()
    private val responseBytes = responseBody.toByteArray(StandardCharsets.UTF_8)

    val writtenBody: String
        get() = output.toString(StandardCharsets.UTF_8.name())

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getOutputStream(): OutputStream = output
    override fun getResponseCode(): Int = statusCode
    override fun getInputStream(): InputStream = ByteArrayInputStream(responseBytes)
    override fun getErrorStream(): InputStream? =
        if (statusCode in 200..299) null else ByteArrayInputStream(responseBytes)
}
