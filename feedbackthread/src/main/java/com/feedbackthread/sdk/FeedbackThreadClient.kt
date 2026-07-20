package com.feedbackthread.sdk

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

@Serializable
public enum class FeedbackThreadFeedbackKind(public val title: String) {
    @SerialName("Bugs")
    BUG("Bug"),

    @SerialName("Requests")
    REQUEST("Request"),

    @SerialName("Reviews")
    REVIEW("Review"),
}

/**
 * A customer's plan tier, used to prioritize feedback and votes.
 *
 * Pass the same signal you trust for your own paywall — whatever your app already
 * uses to distinguish free users from paying customers.
 */
@Serializable(with = FeedbackThreadCustomerTierSerializer::class)
public sealed class FeedbackThreadCustomerTier {
    public abstract val rawValue: String

    public data object Free : FeedbackThreadCustomerTier() {
        override val rawValue: String = "free"
    }

    public data object Paying : FeedbackThreadCustomerTier() {
        override val rawValue: String = "paying"
    }

    public data class Custom(public val value: String) : FeedbackThreadCustomerTier() {
        override val rawValue: String = value
    }
}

public object FeedbackThreadCustomerTierSerializer : KSerializer<FeedbackThreadCustomerTier> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FeedbackThreadCustomerTier", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FeedbackThreadCustomerTier) {
        encoder.encodeString(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): FeedbackThreadCustomerTier =
        when (val value = decoder.decodeString()) {
            "free" -> FeedbackThreadCustomerTier.Free
            "paying" -> FeedbackThreadCustomerTier.Paying
            else -> FeedbackThreadCustomerTier.Custom(value)
        }
}

@Serializable
public data class FeedbackThreadFeedbackSubmission(
    public val kind: FeedbackThreadFeedbackKind,
    public val title: String,
    public val text: String,
    public val appVersion: String? = null,
    @SerialName("externalUserId")
    public val externalUserId: String? = null,
    public val customerTier: FeedbackThreadCustomerTier? = null,
)

@Serializable
public data class FeedbackThreadFeedback(
    public val id: String,
    public val kind: FeedbackThreadFeedbackKind,
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
public enum class FeedbackThreadRequestTarget {
    @SerialName("ios")
    IOS,

    @SerialName("android")
    ANDROID,

    @SerialName("watchos")
    WATCH_OS,
}

@Serializable
public data class FeedbackThreadFeatureRequest(
    public val id: String,
    public val title: String,
    public val description: String,
    public val votes: Int,
    public val target: FeedbackThreadRequestTarget,
    public val status: String,
    public val voted: Boolean,
    public val updatedAt: String,
    public val shippedInVersion: String? = null,
)

@Serializable
public data class FeedbackThreadVoteResult(
    public val feedbackId: String,
    public val votes: Int,
    public val voted: Boolean,
)

public data class FeedbackThreadConfiguration(
    public val baseUrl: String,
    public val projectKey: String,
    public val source: String,
    public val connectTimeoutMillis: Int = 10_000,
    public val readTimeoutMillis: Int = 15_000,
)

public sealed class FeedbackThreadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    public class InvalidConfiguration internal constructor(message: String) : FeedbackThreadException(message)

    public class InvalidResponse internal constructor(message: String, cause: Throwable? = null) :
        FeedbackThreadException(message, cause)

    public class Server(
        public val statusCode: Int,
        message: String,
    ) : FeedbackThreadException(message)
}

public class FeedbackThreadClient private constructor(
    private val handlers: FeedbackThreadHandlers,
) {
    internal constructor(
        submissionHandler: suspend (FeedbackThreadFeedbackSubmission, String) -> FeedbackThreadFeedback,
    ) : this(
        FeedbackThreadHandlers(
            submit = submissionHandler,
            requests = { emptyList() },
            setVote = { _, _, _, _ ->
                throw FeedbackThreadException.InvalidConfiguration("This FeedbackThread client does not support voting.")
            },
        ),
    )

    internal constructor(
        submissionHandler: suspend (FeedbackThreadFeedbackSubmission, String) -> FeedbackThreadFeedback,
        requestListHandler: suspend (String?) -> List<FeedbackThreadFeatureRequest>,
        voteHandler: suspend (String, Boolean, String, FeedbackThreadCustomerTier?) -> FeedbackThreadVoteResult,
    ) : this(
        FeedbackThreadHandlers(
            submit = submissionHandler,
            requests = requestListHandler,
            setVote = voteHandler,
        ),
    )

    public constructor(configuration: FeedbackThreadConfiguration) : this(
        createHandlers(configuration) { url -> url.openConnection() as HttpURLConnection },
    )

    internal constructor(
        configuration: FeedbackThreadConfiguration,
        connectionFactory: (URL) -> HttpURLConnection,
    ) : this(createHandlers(configuration, connectionFactory))

    @JvmOverloads
    public suspend fun submit(
        submission: FeedbackThreadFeedbackSubmission,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): FeedbackThreadFeedback = handlers.submit(submission, idempotencyKey)

    public suspend fun requests(externalUserId: String? = null): List<FeedbackThreadFeatureRequest> =
        handlers.requests(externalUserId)

    @JvmOverloads
    public suspend fun setVote(
        requestId: String,
        voted: Boolean,
        externalUserId: String,
        customerTier: FeedbackThreadCustomerTier? = null,
    ): FeedbackThreadVoteResult = handlers.setVote(requestId, voted, externalUserId, customerTier)

    private companion object {
        fun createHandlers(
            configuration: FeedbackThreadConfiguration,
            connectionFactory: (URL) -> HttpURLConnection,
        ): FeedbackThreadHandlers {
            val transport = FeedbackThreadHTTPTransport(configuration, connectionFactory)
            return FeedbackThreadHandlers(
                submit = transport::submit,
                requests = transport::requests,
                setVote = transport::setVote,
            )
        }
    }
}

private data class FeedbackThreadHandlers(
    val submit: suspend (FeedbackThreadFeedbackSubmission, String) -> FeedbackThreadFeedback,
    val requests: suspend (String?) -> List<FeedbackThreadFeatureRequest>,
    val setVote: suspend (String, Boolean, String, FeedbackThreadCustomerTier?) -> FeedbackThreadVoteResult,
)

private class FeedbackThreadHTTPTransport(
    private val configuration: FeedbackThreadConfiguration,
    private val connectionFactory: (URL) -> HttpURLConnection,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun submit(
        submission: FeedbackThreadFeedbackSubmission,
        idempotencyKey: String,
    ): FeedbackThreadFeedback = withContext(Dispatchers.IO) {
        val endpoint = endpointURL()
        val payload = json.encodeToString(
            FeedbackThreadIngestionPayload(
                kind = submission.kind,
                source = configuration.source.trim(),
                title = submission.title,
                text = submission.text,
                appVersion = submission.appVersion,
                externalUserId = submission.externalUserId,
                customerTier = submission.customerTier,
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
                    json.decodeFromString<FeedbackThreadErrorEnvelope>(responseBody).error.message
                }.getOrNull() ?: "FeedbackThread returned HTTP $statusCode."
                throw FeedbackThreadException.Server(statusCode, message)
            }

            try {
                json.decodeFromString<FeedbackThreadFeedbackEnvelope>(responseBody).feedback
            } catch (error: SerializationException) {
                throw FeedbackThreadException.InvalidResponse("FeedbackThread returned an unreadable response.", error)
            }
        } catch (error: FeedbackThreadException) {
            throw error
        } catch (error: IOException) {
            throw FeedbackThreadException.InvalidResponse("Could not reach FeedbackThread.", error)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun requests(externalUserId: String?): List<FeedbackThreadFeatureRequest> = withContext(Dispatchers.IO) {
        val connection = connectionFactory(endpointURL("requests?platform=android"))
        try {
            connection.requestMethod = "GET"
            configureConnection(connection)
            normalizedUserId(externalUserId)?.let {
                connection.setRequestProperty("X-FeedbackThread-User", it)
            }
            val responseBody = responseBody(connection)
            try {
                json.decodeFromString<FeedbackThreadRequestsEnvelope>(responseBody).requests
            } catch (error: SerializationException) {
                throw FeedbackThreadException.InvalidResponse("FeedbackThread returned an unreadable response.", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun setVote(
        requestId: String,
        voted: Boolean,
        externalUserId: String,
        customerTier: FeedbackThreadCustomerTier?,
    ): FeedbackThreadVoteResult = withContext(Dispatchers.IO) {
        val userId = normalizedUserId(externalUserId)
            ?: throw FeedbackThreadException.InvalidConfiguration("A stable user ID is required for voting.")
        val encodedRequestId = URLEncoder
            .encode(requestId, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        val connection = connectionFactory(endpointURL("requests/$encodedRequestId/vote?platform=android"))
        try {
            connection.requestMethod = if (voted) "POST" else "DELETE"
            configureConnection(connection)
            connection.setRequestProperty("X-FeedbackThread-User", userId)
            if (customerTier != null) {
                val payloadBytes = json.encodeToString(FeedbackThreadVotePayload(customerTier))
                    .toByteArray(StandardCharsets.UTF_8)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payloadBytes.size)
                connection.outputStream.use { it.write(payloadBytes) }
            }
            val responseBody = responseBody(connection)
            try {
                json.decodeFromString<FeedbackThreadVoteResult>(responseBody)
            } catch (error: SerializationException) {
                throw FeedbackThreadException.InvalidResponse("FeedbackThread returned an unreadable response.", error)
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
                json.decodeFromString<FeedbackThreadErrorEnvelope>(responseBody).error.message
            }.getOrNull() ?: "FeedbackThread returned HTTP $statusCode."
            throw FeedbackThreadException.Server(statusCode, message)
        }
        return responseBody
    }

    private fun normalizedUserId(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun endpointURL(path: String = "feedback"): URL {
        val baseUrl = configuration.baseUrl.trim().trimEnd('/')
        val projectKey = configuration.projectKey.trim()
        val source = configuration.source.trim()

        if (projectKey.isEmpty()) {
            throw FeedbackThreadException.InvalidConfiguration("A FeedbackThread project key is required.")
        }
        if (source.isEmpty()) {
            throw FeedbackThreadException.InvalidConfiguration("A FeedbackThread source is required.")
        }

        val baseURI = try {
            URI(baseUrl)
        } catch (error: Exception) {
            throw FeedbackThreadException.InvalidConfiguration("The FeedbackThread base URL is invalid.")
        }
        if (baseURI.scheme !in setOf("http", "https") || baseURI.host.isNullOrBlank()) {
            throw FeedbackThreadException.InvalidConfiguration("The FeedbackThread base URL must use HTTP or HTTPS.")
        }

        val encodedKey = URLEncoder
            .encode(projectKey, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        return URL("$baseUrl/v1/projects/$encodedKey/$path")
    }
}

@Serializable
private data class FeedbackThreadIngestionPayload(
    val kind: FeedbackThreadFeedbackKind,
    val source: String,
    val title: String,
    val text: String,
    val appVersion: String?,
    @SerialName("externalUserId")
    val externalUserId: String?,
    val customerTier: FeedbackThreadCustomerTier?,
)

@Serializable
private data class FeedbackThreadVotePayload(
    val customerTier: FeedbackThreadCustomerTier,
)

@Serializable
private data class FeedbackThreadFeedbackEnvelope(
    val feedback: FeedbackThreadFeedback,
)

@Serializable
private data class FeedbackThreadRequestsEnvelope(
    val requests: List<FeedbackThreadFeatureRequest>,
)

@Serializable
private data class FeedbackThreadErrorEnvelope(
    val error: APIError,
) {
    @Serializable
    data class APIError(
        val message: String,
    )
}
