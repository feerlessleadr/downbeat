package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/** A scripted stand-in for Music Assistant 2.10.13: HTTP login, websocket auth, canned results, pushed events. */
class FakeMaServer {
    private val server = MockWebServer()
    val tokens = CopyOnWriteArraySet<String>()
    val results = ConcurrentHashMap<String, String>()
    val received = CopyOnWriteArrayList<Pair<String, JsonObject?>>()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    val connections = AtomicInteger()

    @Volatile var loginStatus = 200
    @Volatile var loginBody = """{"success":true,"token":"SESSION","user":{}}"""
    @Volatile var lastLoginBody: String? = null
    /** Token that auth/token/create hands out (and then accepts). */
    @Volatile var longToken = "LONG"

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sockets.add(webSocket)
            connections.incrementAndGet()
            webSocket.send("""{"server_id":"x","server_version":"2.10.13","schema_version":65}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = MaJson.parseToJsonElement(text).jsonObject
            val command = msg["command"]!!.jsonPrimitive.content
            val id = msg["message_id"]!!.jsonPrimitive.content
            when (command) {
                "auth" -> {
                    val token = msg["args"]!!.jsonObject["token"]!!.jsonPrimitive.content
                    if (token in tokens) {
                        webSocket.send("""{"message_id":"$id","result":{"authenticated":true},"partial":false}""")
                    } else {
                        webSocket.send("""{"message_id":"$id","error_code":401,"details":"Invalid token"}""")
                    }
                }
                "auth/token/create" -> {
                    received.add(command to (msg["args"] as? JsonObject))
                    tokens.add(longToken)
                    webSocket.send("""{"message_id":"$id","result":"$longToken","partial":false}""")
                }
                else -> {
                    received.add(command to (msg["args"] as? JsonObject))
                    webSocket.send("""{"message_id":"$id","result":${results[command] ?: "null"},"partial":false}""")
                }
            }
        }
    }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/ws" -> MockResponse().withWebSocketUpgrade(listener)
                "/auth/login" -> {
                    lastLoginBody = request.body.readUtf8()
                    MockResponse().setResponseCode(loginStatus).setBody(loginBody)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    fun url(): String = server.url("/").toString().trimEnd('/')

    /** Sends a raw text message (an event) to every connected client. */
    fun push(text: String) = sockets.forEach { it.send(text) }

    /** Closes every websocket from the server side, as a network drop would. */
    fun dropConnections() {
        sockets.forEach { it.close(1001, "drop") }
        sockets.clear()
    }

    fun stop() {
        sockets.forEach { it.close(1000, "done") }
        server.shutdown()
    }
}
