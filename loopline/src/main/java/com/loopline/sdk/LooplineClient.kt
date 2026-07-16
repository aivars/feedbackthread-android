package com.loopline.sdk

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
public enum class LooplineFeedbackKind(public val title: String) {
    @SerialName("Bugs")
    BUG("Bug"),

    @SerialName("Requests")
    REQUEST("Request"),

    @SerialName("Reviews")
    REVIEW("Review"),
}

@Serializable
public data class LooplineFeedbackSubmission(
    public val kind: LooplineFeedbackKind,
    public val title: String,
    public val text: String,
    public val appVersion: String? = null,
    @SerialName("externalUserId")
    public val externalUserId: String? = null,
)

@Serializable
public data class LooplineFeedback(
    public val id: String,
    public val kind: LooplineFeedbackKind,
    public val source: String,
    public val title: String,
    public val excerpt: String,
    public val version: String,
    public val status: String,
    public val count: Int,
    public val note: String,
    public val responseDraft: String,
    public val responseState: String,
    public val createdAt: String,
    public val updatedAt: String,
)

@Serializable
public enum class LooplineRequestTarget {
    @SerialName("ios")
    IOS,

    @SerialName("android")
    ANDROID,

    @SerialName("watchos")
    WATCH_OS,
}

@Serializable
public data class LooplineFeatureRequest(
    public val id: String,
    public val title: String,
    public val description: String,
    public val votes: Int,
    public val target: LooplineRequestTarget,
    public val status: String,
    public val voted: Boolean,
    public val updatedAt: String,
)

@Serializable
public data class LooplineVoteResult(
    public val feedbackId: String,
    public val votes: Int,
    public val voted: Boolean,
)

public data class LooplineConfiguration(
    public val baseUrl: String,
    public val projectKey: String,
    public val source: String,
    public val connectTimeoutMillis: Int = 10_000,
    public val readTimeoutMillis: Int = 15_000,
)

public sealed class LooplineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    public class InvalidConfiguration internal constructor(message: String) : LooplineException(message)

    public class InvalidResponse internal constructor(message: String, cause: Throwable? = null) :
        LooplineException(message, cause)

    public class Server(
        public val statusCode: Int,
        message: String,
    ) : LooplineException(message)
}

public class LooplineClient private constructor(
    private val handlers: LooplineHandlers,
) {
    internal constructor(
        submissionHandler: suspend (LooplineFeedbackSubmission, String) -> LooplineFeedback,
    ) : this(
        LooplineHandlers(
            submit = submissionHandler,
            requests = { emptyList() },
            setVote = { _, _, _ ->
                throw LooplineException.InvalidConfiguration("This Loopline client does not support voting.")
            },
        ),
    )

    internal constructor(
        submissionHandler: suspend (LooplineFeedbackSubmission, String) -> LooplineFeedback,
        requestListHandler: suspend (String?) -> List<LooplineFeatureRequest>,
        voteHandler: suspend (String, Boolean, String) -> LooplineVoteResult,
    ) : this(
        LooplineHandlers(
            submit = submissionHandler,
            requests = requestListHandler,
            setVote = voteHandler,
        ),
    )

    public constructor(configuration: LooplineConfiguration) : this(
        createHandlers(configuration) { url -> url.openConnection() as HttpURLConnection },
    )

    internal constructor(
        configuration: LooplineConfiguration,
        connectionFactory: (URL) -> HttpURLConnection,
    ) : this(createHandlers(configuration, connectionFactory))

    @JvmOverloads
    public suspend fun submit(
        submission: LooplineFeedbackSubmission,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): LooplineFeedback = handlers.submit(submission, idempotencyKey)

    public suspend fun requests(externalUserId: String? = null): List<LooplineFeatureRequest> =
        handlers.requests(externalUserId)

    public suspend fun setVote(
        requestId: String,
        voted: Boolean,
        externalUserId: String,
    ): LooplineVoteResult = handlers.setVote(requestId, voted, externalUserId)

    private companion object {
        fun createHandlers(
            configuration: LooplineConfiguration,
            connectionFactory: (URL) -> HttpURLConnection,
        ): LooplineHandlers {
            val transport = LooplineHTTPTransport(configuration, connectionFactory)
            return LooplineHandlers(
                submit = transport::submit,
                requests = transport::requests,
                setVote = transport::setVote,
            )
        }
    }
}

private data class LooplineHandlers(
    val submit: suspend (LooplineFeedbackSubmission, String) -> LooplineFeedback,
    val requests: suspend (String?) -> List<LooplineFeatureRequest>,
    val setVote: suspend (String, Boolean, String) -> LooplineVoteResult,
)

private class LooplineHTTPTransport(
    private val configuration: LooplineConfiguration,
    private val connectionFactory: (URL) -> HttpURLConnection,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun submit(
        submission: LooplineFeedbackSubmission,
        idempotencyKey: String,
    ): LooplineFeedback = withContext(Dispatchers.IO) {
        val endpoint = endpointURL()
        val payload = json.encodeToString(
            LooplineIngestionPayload(
                kind = submission.kind,
                source = configuration.source.trim(),
                title = submission.title,
                text = submission.text,
                appVersion = submission.appVersion,
                externalUserId = submission.externalUserId,
            ),
        )
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val connection = connectionFactory(endpoint)

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = configuration.connectTimeoutMillis
            connection.readTimeout = configuration.readTimeoutMillis
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payloadBytes.size)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Idempotency-Key", idempotencyKey)
            connection.outputStream.use { it.write(payloadBytes) }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                val message = runCatching {
                    json.decodeFromString<LooplineErrorEnvelope>(responseBody).error.message
                }.getOrNull() ?: "Loopline returned HTTP $statusCode."
                throw LooplineException.Server(statusCode, message)
            }

            try {
                json.decodeFromString<LooplineFeedbackEnvelope>(responseBody).feedback
            } catch (error: SerializationException) {
                throw LooplineException.InvalidResponse("Loopline returned an unreadable response.", error)
            }
        } catch (error: LooplineException) {
            throw error
        } catch (error: IOException) {
            throw LooplineException.InvalidResponse("Could not reach Loopline.", error)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun requests(externalUserId: String?): List<LooplineFeatureRequest> = withContext(Dispatchers.IO) {
        val connection = connectionFactory(endpointURL("requests?platform=android"))
        try {
            connection.requestMethod = "GET"
            configureConnection(connection)
            normalizedUserId(externalUserId)?.let {
                connection.setRequestProperty("X-Loopline-User", it)
            }
            val responseBody = responseBody(connection)
            try {
                json.decodeFromString<LooplineRequestsEnvelope>(responseBody).requests
            } catch (error: SerializationException) {
                throw LooplineException.InvalidResponse("Loopline returned an unreadable response.", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun setVote(
        requestId: String,
        voted: Boolean,
        externalUserId: String,
    ): LooplineVoteResult = withContext(Dispatchers.IO) {
        val userId = normalizedUserId(externalUserId)
            ?: throw LooplineException.InvalidConfiguration("A stable user ID is required for voting.")
        val encodedRequestId = URLEncoder
            .encode(requestId, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        val connection = connectionFactory(endpointURL("requests/$encodedRequestId/vote?platform=android"))
        try {
            connection.requestMethod = if (voted) "POST" else "DELETE"
            configureConnection(connection)
            connection.setRequestProperty("X-Loopline-User", userId)
            val responseBody = responseBody(connection)
            try {
                json.decodeFromString<LooplineVoteResult>(responseBody)
            } catch (error: SerializationException) {
                throw LooplineException.InvalidResponse("Loopline returned an unreadable response.", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun configureConnection(connection: HttpURLConnection) {
        connection.connectTimeout = configuration.connectTimeoutMillis
        connection.readTimeout = configuration.readTimeoutMillis
        connection.setRequestProperty("Accept", "application/json")
    }

    private fun responseBody(connection: HttpURLConnection): String {
        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (statusCode !in 200..299) {
            val message = runCatching {
                json.decodeFromString<LooplineErrorEnvelope>(responseBody).error.message
            }.getOrNull() ?: "Loopline returned HTTP $statusCode."
            throw LooplineException.Server(statusCode, message)
        }
        return responseBody
    }

    private fun normalizedUserId(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun endpointURL(path: String = "feedback"): URL {
        val baseUrl = configuration.baseUrl.trim().trimEnd('/')
        val projectKey = configuration.projectKey.trim()
        val source = configuration.source.trim()

        if (projectKey.isEmpty()) {
            throw LooplineException.InvalidConfiguration("A Loopline project key is required.")
        }
        if (source.isEmpty()) {
            throw LooplineException.InvalidConfiguration("A Loopline source is required.")
        }

        val baseURI = try {
            URI(baseUrl)
        } catch (error: Exception) {
            throw LooplineException.InvalidConfiguration("The Loopline base URL is invalid.")
        }
        if (baseURI.scheme !in setOf("http", "https") || baseURI.host.isNullOrBlank()) {
            throw LooplineException.InvalidConfiguration("The Loopline base URL must use HTTP or HTTPS.")
        }

        val encodedKey = URLEncoder
            .encode(projectKey, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        return URL("$baseUrl/v1/projects/$encodedKey/$path")
    }
}

@Serializable
private data class LooplineIngestionPayload(
    val kind: LooplineFeedbackKind,
    val source: String,
    val title: String,
    val text: String,
    val appVersion: String?,
    @SerialName("externalUserId")
    val externalUserId: String?,
)

@Serializable
private data class LooplineFeedbackEnvelope(
    val feedback: LooplineFeedback,
)

@Serializable
private data class LooplineRequestsEnvelope(
    val requests: List<LooplineFeatureRequest>,
)

@Serializable
private data class LooplineErrorEnvelope(
    val error: APIError,
) {
    @Serializable
    data class APIError(
        val message: String,
    )
}
