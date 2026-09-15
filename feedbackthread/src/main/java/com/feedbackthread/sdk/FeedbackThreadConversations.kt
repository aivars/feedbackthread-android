package com.feedbackthread.sdk

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable public data class FeedbackThreadCustomerSession(val customerId: String, val externalUserId: String, val token: String)
@Serializable public data class FeedbackThreadConversationRoute(val feedbackId: String, val audience: String) {
    init { require(audience == "private" || audience == "public"); require(feedbackId.matches(Regex("FDBK-[A-Za-z0-9-]+"))) }
    internal val path get() = "/threads/${URLEncoder.encode(feedbackId, "UTF-8")}/$audience"
}
@Serializable public data class FeedbackThreadConversationMessage(val id: String, val seq: Long, val body: String, val authorRole: String, val mine: Boolean, val createdAt: String, val deletedAt: String? = null)
@Serializable public data class FeedbackThreadConversationThread(val id: String, val audience: String, val status: String)
@Serializable public data class FeedbackThreadConversationHistory(val thread: FeedbackThreadConversationThread, val messages: List<FeedbackThreadConversationMessage>, val unreadCount: Int, val following: Boolean, val hasMore: Boolean, val nextBefore: Long? = null, val otherReadSeq: Long? = null)
@Serializable public data class FeedbackThreadConversationSummary(val id: String, val feedbackId: String, val audience: String, val title: String, val preview: String, val status: String, val unreadCount: Int, val updatedAt: String)
@Serializable public data class FeedbackThreadConversationInbox(val conversations: List<FeedbackThreadConversationSummary>, val unreadCount: Int)
public data class FeedbackThreadConversationState(val inbox: List<FeedbackThreadConversationSummary> = emptyList(), val unreadCount: Int = 0, val publicCommentsEnabled: Boolean = false, val ready: Boolean = false, val revision: Long = 0, val route: FeedbackThreadConversationRoute? = null, val error: String? = null)

/** Back with encrypted platform storage; never use plain preferences for guest tokens. */
public interface FeedbackThreadConversationStore { public fun load(key: String): String?; public fun save(key: String, value: String); public fun remove(key: String) }
/** AES-GCM key stays in Android Keystore; ciphertext is excluded from device backup. */
public class FeedbackThreadSecureConversationStore(context: Context) : FeedbackThreadConversationStore {
    private val directory = File(context.noBackupFilesDir, "feedbackthread-conversations").also { it.mkdirs() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("feedbackthread.conversations.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("feedbackthread.conversations.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun file(key: String) = File(directory, key)
    @Synchronized override fun load(key: String): String? {
        val file = file(key); if (!file.exists()) return null
        val bytes = file.readBytes(); require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        cipher.updateAAD(key.toByteArray()); return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
    }
    @Synchronized override fun save(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(key.toByteArray())
        val target = file(key); val temporary = File(directory, "$key.tmp")
        temporary.outputStream().use { it.write(cipher.iv + cipher.doFinal(value.toByteArray())) }
        check(temporary.renameTo(target)) { "Could not persist conversation credentials." }
    }
    @Synchronized override fun remove(key: String) { val target = file(key); check(!target.exists() || target.delete()) }
}

/** Retain one instance per host account; accountScope is local isolation, not verified host login. */
public class FeedbackThreadConversations(
    private val configuration: FeedbackThreadConfiguration,
    private val store: FeedbackThreadConversationStore,
    accountScope: String = "guest",
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    public constructor(context: Context, configuration: FeedbackThreadConfiguration, accountScope: String = "guest") : this(configuration, FeedbackThreadSecureConversationStore(context.applicationContext), accountScope)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mutex = Mutex()
    private val key = MessageDigest.getInstance("SHA-256").digest("${configuration.baseUrl.trimEnd('/')}:${configuration.projectKey}:$accountScope".toByteArray()).joinToString("") { "%02x".format(it) }
    @Volatile private var revocationSession: FeedbackThreadCustomerSession? = null
    @Volatile private var session: FeedbackThreadCustomerSession? = null
    @Volatile private var closed = false
    private var liveSocket: WebSocket? = null
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(FeedbackThreadConversationState())
    public val state: StateFlow<FeedbackThreadConversationState> = mutableState.asStateFlow()
    private val legacy = FeedbackThreadClient(configuration, connectionFactory)
    public val client: FeedbackThreadClient = legacy.withConversationSession {
        if (closed) throw FeedbackThreadException.InvalidConfiguration("This conversation session has signed out.")
        session ?: throw FeedbackThreadException.InvalidConfiguration("Prepare conversations before using the client.")
    }
    public suspend fun prepare(): FeedbackThreadCustomerSession = mutex.withLock {
        check(!closed) { "This conversation session has signed out." }
        session?.let { return@withLock it }
        val stored = withContext(Dispatchers.IO) { store.load(key) }
        val value = if (stored == null) revocationSession ?: json.decodeFromString<FeedbackThreadCustomerSession>(request("/session", "POST", buildJsonObject {}, false)) else json.decodeFromString(stored)
        require(value.token.matches(Regex("[a-f0-9-]{72}")) && value.externalUserId.startsWith("ft-guest:"))
        revocationSession = value // Retain for logout/revocation even when preparation is cancelled.
        check(!closed)
        if (stored == null) withContext(Dispatchers.IO) { store.save(key, json.encodeToString(value)) }
        session = value; value
    }
    public suspend fun refresh() = refreshMutex.withLock {
        prepare()
        val settings = json.decodeFromString<FeedbackThreadConversationSettings>(request("/settings"))
        val inbox = json.decodeFromString<FeedbackThreadConversationInbox>(request("/inbox"))
        if (!closed) mutableState.value = mutableState.value.copy(inbox=inbox.conversations, unreadCount=inbox.unreadCount, publicCommentsEnabled=settings.publicCommentsEnabled, ready=true, revision=mutableState.value.revision+1, error=null,
            route=mutableState.value.route?.takeUnless { it.audience == "public" && !settings.publicCommentsEnabled })
    }
    public suspend fun history(route: FeedbackThreadConversationRoute, before: Long? = null): FeedbackThreadConversationHistory {
        prepare(); require(before == null || before > 0)
        return json.decodeFromString(request(route.path + (before?.let { "?before=$it" } ?: "")))
    }
    public suspend fun send(route: FeedbackThreadConversationRoute, body: String, clientId: String) {
        require(body.trim().isNotEmpty() && body.length <= 4000 && clientId.isNotEmpty() && clientId.length <= 100)
        prepare(); request(route.path+"/messages", "POST", buildJsonObject { put("body",body.trim()); put("clientId",clientId) })
    }
    public suspend fun markRead(route: FeedbackThreadConversationRoute, seq: Long) { prepare(); request(route.path+"/read", "POST", buildJsonObject { put("seq",seq) }) }
    public suspend fun follow(route: FeedbackThreadConversationRoute, following: Boolean) { prepare(); request(route.path+"/follow", "PUT", buildJsonObject { put("following",following) }) }
    public suspend fun remove(route: FeedbackThreadConversationRoute, messageId: String) { prepare(); request(route.path+"/messages/"+URLEncoder.encode(messageId,"UTF-8"), "DELETE", buildJsonObject {}) }
    public suspend fun registerDeviceToken(token: String) { prepare(); request("/device","PUT",buildJsonObject { put("provider","fcm"); put("token",token) }) }
    public suspend fun unregisterDeviceToken(token: String) { prepare(); request("/device","DELETE",buildJsonObject { put("provider","fcm"); put("token",token) }) }
    public fun open(route: FeedbackThreadConversationRoute?) { if (!closed) mutableState.value=mutableState.value.copy(route=route) }
    /** Forward Firebase RemoteMessage.data or activity intent extras after a notification tap. */
    public fun handleNotification(data: Map<String,String>): Boolean {
        val route = runCatching { json.decodeFromString<FeedbackThreadConversationRoute>(data["feedbackThread"] ?: return false) }.getOrNull() ?: return false
        if (!(state.value.ready && !state.value.publicCommentsEnabled && route.audience=="public")) open(route)
        return true
    }
    public suspend fun logout() {
        closed=true; liveSocket?.cancel(); mutableState.value=FeedbackThreadConversationState()
        mutex.withLock {
            val saved = revocationSession ?: session ?: withContext(Dispatchers.IO) { store.load(key) }?.let { json.decodeFromString<FeedbackThreadCustomerSession>(it) }
            withContext(Dispatchers.IO) { store.remove(key) }; session=null; mutableState.value=FeedbackThreadConversationState()
            if (saved != null) try { request("/session","DELETE",buildJsonObject {},false,saved.token) }
            catch (error: FeedbackThreadException.Server) { if(error.statusCode != 401) throw error }
            revocationSession=null
        }
    }
    /** Call only while foregrounded. Cancellation closes the socket and timers. */
    public suspend fun runLive() = coroutineScope {
        val http = OkHttpClient.Builder().pingInterval(20,java.util.concurrent.TimeUnit.SECONDS).build()
        var failures=0
        try {
            while (isActive && !closed) {
                try {
                    refresh()
                    val ticket=json.parseToJsonElement(request("/live-ticket","POST",buildJsonObject {})).jsonObject["path"]!!.jsonPrimitive.content
                    require(ticket.matches(Regex("/v1/chat/live/[a-f0-9-]{72}")))
                    val ended=CompletableDeferred<Unit>()
                    val signals=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
                    val base=URI(configuration.baseUrl)
                    val url=URI(if(base.scheme=="https") "wss" else "ws",null,base.host,base.port,ticket,null,null).toString()
                    val socket=http.newWebSocket(Request.Builder().url(url).build(),object: WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket,response: Response) { signals.trySend(Unit) }
                        override fun onMessage(webSocket: WebSocket,text: String) { if(text.contains("\"changed\"")) signals.trySend(Unit) }
                        override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) { ended.complete(Unit) }
                        override fun onClosed(webSocket: WebSocket,code: Int,reason: String) { ended.complete(Unit) }
                    }); liveSocket=socket
                    val heartbeat=launch { while(isActive) { delay(20_000); socket.send("ping") } }
                    val updates=launch { for(signal in signals) { try { refresh() } catch(e: CancellationException) { throw e } catch(e: Exception) { mutableState.value=mutableState.value.copy(error="Could not refresh conversations.") } } }
                    try { ended.await(); failures=0 } finally { heartbeat.cancel(); updates.cancel(); signals.close(); socket.cancel() }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { if(!closed) mutableState.value=mutableState.value.copy(error="Could not connect to conversations.") }
                delay(minOf(30_000L,1000L shl minOf(failures++,5)))
            }
        } finally { liveSocket?.cancel(); http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
    }
    private suspend fun request(path: String, method: String="GET", payload: JsonObject?=null, authenticated: Boolean=true, tokenOverride: String?=null): String = withContext(Dispatchers.IO) {
        val base=URI(configuration.baseUrl)
        require(base.userInfo==null && base.query==null && base.fragment==null && (base.path.isNullOrEmpty() || base.path=="/"))
        require(base.scheme=="https" || (base.scheme=="http" && base.host in setOf("localhost","127.0.0.1","[::1]")))
        val token=tokenOverride ?: if(authenticated) { check(!closed); session?.token ?: error("Prepare conversations first.") } else null
        val connection=connectionFactory(URL("${configuration.baseUrl.trimEnd('/')}/v1/projects/${URLEncoder.encode(configuration.projectKey,"UTF-8")}/chat$path"))
        try {
            connection.instanceFollowRedirects=false; connection.connectTimeout=configuration.connectTimeoutMillis; connection.readTimeout=configuration.readTimeoutMillis; connection.requestMethod=method
            connection.setRequestProperty("Accept","application/json"); if(token!=null) connection.setRequestProperty("X-FeedbackThread-Customer",token)
            if(payload!=null) { val bytes=payload.toString().toByteArray(); connection.setRequestProperty("Content-Type","application/json"); connection.doOutput=true; connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) } }
            val status=connection.responseCode; val stream=if(status in 200..299) connection.inputStream else connection.errorStream
            val bytes=stream?.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (output.size() <= 1_048_576) { val count = input.read(buffer,0,minOf(buffer.size,1_048_577-output.size())); if(count<0) break; output.write(buffer,0,count) }
                output.toByteArray()
            } ?: byteArrayOf(); require(bytes.size<=1_048_576) { "Conversation response too large." }; val text=String(bytes,Charsets.UTF_8)
            if(status !in 200..299) throw FeedbackThreadException.Server(status,"Conversation request failed ($status).")
            if(authenticated) check(!closed)
            text
        } finally { connection.disconnect() }
    }
}
