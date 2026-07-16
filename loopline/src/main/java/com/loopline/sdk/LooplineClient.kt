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

public class LooplineClient internal constructor(
    private val submissionHandler: suspend (LooplineFeedbackSubmission, String) -> LooplineFeedback,
) {
    public constructor(configuration: LooplineConfiguration) : this(
        configuration = configuration,
        connectionFactory = { url -> url.openConnection() as HttpURLConnection },
    )

    internal constructor(
        configuration: LooplineConfiguration,
        connectionFactory: (URL) -> HttpURLConnection,
    ) : this(
        submissionHandler = LooplineHTTPTransport(configuration, connectionFactory)::submit,
    )

    @JvmOverloads
    public suspend fun submit(
        submission: LooplineFeedbackSubmission,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): LooplineFeedback = submissionHandler(submission, idempotencyKey)
}

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

    private fun endpointURL(): URL {
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
        return URL("$baseUrl/v1/projects/$encodedKey/feedback")
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
private data class LooplineErrorEnvelope(
    val error: APIError,
) {
    @Serializable
    data class APIError(
        val message: String,
    )
}
