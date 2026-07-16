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
        assertEquals("FDBK-test", feedback.id)
        assertEquals(LooplineFeedbackKind.REQUEST, feedback.kind)
        assertEquals("Open", feedback.status)
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
    public fun rejectsEmptyProjectKeyBeforeSending(): Unit = runBlocking {
        val client = LooplineClient(
            LooplineConfiguration("https://example.com", "  ", "android"),
        )

        try {
            client.submit(LooplineFeedbackSubmission(LooplineFeedbackKind.BUG, "Crash", "It crashed."))
            fail("Expected an invalid configuration error")
        } catch (error: LooplineException.InvalidConfiguration) {
            assertEquals("A Loopline project key is required.", error.message)
        }
    }

    @Test
    public fun submitsThroughLiveStagingWhenConfigured(): Unit = runBlocking {
        val baseURL = System.getenv("LOOPLINE_LIVE_BASE_URL") ?: return@runBlocking
        val projectKey = System.getenv("LOOPLINE_LIVE_PROJECT_KEY") ?: return@runBlocking
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
        assertEquals("Open", feedback.status)
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
                "status": "Open",
                "count": 1,
                "note": "",
                "responseDraft": "",
                "responseState": "Not started",
                "createdAt": "2026-07-16T12:00:00.000Z",
                "updatedAt": "2026-07-16T12:00:00.000Z"
              }
            }
        """.trimIndent()
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
