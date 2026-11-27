package com.feerlessleadr.downbeat.ma

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A server event, for example player_updated. [data] is the raw payload; decode it with [MaJson]. */
data class MaEvent(val event: String, val objectId: String?, val data: JsonElement?)

sealed interface MaState {
    data object Idle : MaState
    data object Connecting : MaState
    data class Ready(val server: ServerInfo) : MaState
    /** The server refused the token (sign in again) or setup is not finished. */
    data class AuthFailed(val reason: String) : MaState
    /** The connection dropped or never opened. The caller decides when to retry. */
    data class Disconnected(val reason: String) : MaState
}

class MaException(val code: String?, message: String) : Exception(message)

/**
 * One websocket session with a Music Assistant server.
 *
 * Protocol (verified against server 2.10.13, see docs/music-assistant-api-verified.md): the server speaks first with
 * a server-info object; the client then sends {"command":"auth","message_id":N,"args":{"token":...}}; replies carry
 * message_id (as a string), result and partial; events carry "event", "object_id" and "data".
 * Still unverified: the exact error reply fields and event payload shapes. Error parsing therefore accepts several
 * field names, and event data stays raw.
 *
 * A client is single-use: create a new one to reconnect.
 */
class MaClient(
    private val http: OkHttpClient,
    private val callTimeoutMs: Long = 30_000,
) {
    private val _state = MutableStateFlow<MaState>(MaState.Idle)
    val state: StateFlow<MaState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MaEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<MaEvent> = _events

    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private val partials = ConcurrentHashMap<String, MutableList<JsonElement>>()
    private var socket: WebSocket? = null
    private var authId: String? = null

    /** [serverUrl] is the address the user typed, for example https://music.example.com (no path). */
    fun connect(serverUrl: String, token: String) {
        check(_state.value is MaState.Idle) { "MaClient is single-use" }
        _state.value = MaState.Connecting
        val request = Request.Builder().url(toSocketUrl(serverUrl)).build()
        socket = http.newWebSocket(request, Listener(token))
    }

    fun close() {
        socket?.close(1000, "bye")
        failPending(MaException(null, "closed"))
        if (_state.value !is MaState.AuthFailed) _state.value = MaState.Disconnected("closed")
    }

    /** Sends a command and returns its result. Throws [MaException] for a server error or a dropped connection. */
    suspend fun call(command: String, args: JsonObject? = null): JsonElement {
        check(_state.value is MaState.Ready) { "Not connected" }
        return send(command, args)
    }

    private suspend fun send(command: String, args: JsonObject?): JsonElement {
        val id = nextId.getAndIncrement().toString()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        val ws = socket ?: throw MaException(null, "Not connected")
        val msg = buildJsonObject {
            put("command", command)
            put("message_id", id.toInt())
            if (args != null) put("args", args)
        }
        if (!ws.send(msg.toString())) {
            pending.remove(id)
            throw MaException(null, "Could not send")
        }
        return try {
            withTimeout(callTimeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
            partials.remove(id)
        }
    }

    private fun handle(text: String, token: String) {
        val obj = try {
            MaJson.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return
        }
        val id = obj["message_id"]?.jsonPrimitive?.contentOrNull
        when {
            id != null -> handleReply(id, obj)
            obj["event"] != null -> {
                _events.tryEmit(
                    MaEvent(
                        event = obj["event"]!!.jsonPrimitive.content,
                        objectId = obj["object_id"]?.jsonPrimitive?.contentOrNull,
                        data = obj["data"],
                    ),
                )
            }
            obj["server_version"] != null && authId == null -> {
                // The server-info message. Remember it and authenticate.
                val info = MaJson.decodeFromJsonElement(ServerInfo.serializer(), obj)
                serverInfo = info
                val aid = nextId.getAndIncrement().toString()
                authId = aid
                socket?.send(
                    buildJsonObject {
                        put("command", "auth")
                        put("message_id", aid.toInt())
                        put("args", buildJsonObject { put("token", token) })
                    }.toString(),
                )
            }
        }
    }

    private var serverInfo: ServerInfo? = null

    private fun handleReply(id: String, obj: JsonObject) {
        val errorCode = obj["error_code"]?.jsonPrimitive?.contentOrNull
        if (id == authId) {
            val ok = obj["result"]?.let { r ->
                (r as? JsonObject)?.get("authenticated")?.jsonPrimitive?.booleanOrNull
            } == true
            if (ok && errorCode == null) {
                _state.value = MaState.Ready(serverInfo ?: ServerInfo())
            } else {
                _state.value = MaState.AuthFailed(errorMessage(obj) ?: "Sign-in was refused")
                socket?.close(1000, "auth failed")
            }
            return
        }
        val deferred = pending[id] ?: return
        if (errorCode != null || obj["error"] != null) {
            deferred.completeExceptionally(MaException(errorCode, errorMessage(obj) ?: "Server error"))
            return
        }
        val result = obj["result"] ?: JsonNull
        val partial = obj["partial"]?.jsonPrimitive?.booleanOrNull == true
        if (partial) {
            // Large lists arrive in batches; collect until a reply with partial=false.
            val list = partials.getOrPut(id) { mutableListOf() }
            if (result is JsonArray) list.addAll(result) else list.add(result)
        } else {
            val earlier = partials.remove(id)
            deferred.complete(
                if (earlier != null && result is JsonArray) JsonArray(earlier + result) else result,
            )
        }
    }

    private fun errorMessage(obj: JsonObject): String? {
        for (key in listOf("details", "message", "error")) {
            val v = obj[key] as? JsonPrimitive
            if (v != null && v !is JsonNull && v.contentOrNull != null) return v.content
        }
        return null
    }

    private fun failPending(e: MaException) {
        pending.values.forEach { it.completeExceptionally(e) }
        pending.clear()
    }

    private inner class Listener(private val token: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = handle(text, token)

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            failPending(MaException(null, "Connection closed"))
            if (_state.value !is MaState.AuthFailed) _state.value = MaState.Disconnected(reason.ifBlank { "closed" })
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failPending(MaException(null, t.message ?: "Connection failed"))
            if (_state.value !is MaState.AuthFailed) {
                _state.value = MaState.Disconnected(t.message ?: "Connection failed")
            }
        }
    }

    companion object {
        /**
         * The address the user typed with the path replaced by /ws. OkHttp's newWebSocket takes an http(s) URL and
         * upgrades it to ws(s) itself, so https stays https. Any path, query or fragment on the input is dropped.
         */
        fun toSocketUrl(serverUrl: String): String {
            val parsed = serverUrl.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Not a valid address")
            return parsed.newBuilder().encodedPath("/ws").query(null).fragment(null).build().toString()
        }
    }
}
