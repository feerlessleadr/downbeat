package com.feerlessleadr.downbeat.ma

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Talks to a scripted fake server shaped like Music Assistant 2.10.13 (docs/music-assistant-api-verified.md). */
class MaClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    private val clients = mutableListOf<MaClient>()
    private val serverSockets = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()

    @After fun tearDown() {
        // MockWebServer waits for upgraded sockets, so close both ends first.
        clients.forEach { it.close() }
        serverSockets.forEach { it.close(1000, "done") }
        server.shutdown()
    }

    private val serverInfo = """{"server_id":"abc","server_version":"2.10.13","schema_version":65,
        "min_supported_schema_version":28,"base_url":"https://media.example.com","onboard_done":true,
        "name":"Music Assistant","status":"running"}"""

    /** [onCommand] gets (command, messageId, rawMessage) and returns reply texts to send back. */
    private fun fakeServer(
        goodToken: String = "good",
        onCommand: (String, String, JsonObject) -> List<String> = { _, _, _ -> emptyList() },
        afterAuth: (WebSocket) -> Unit = {},
    ) {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSockets.add(webSocket)
                    webSocket.send(serverInfo)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = MaJson.parseToJsonElement(text).jsonObject
                    val command = msg["command"]!!.jsonPrimitive.content
                    val id = msg["message_id"]!!.jsonPrimitive.content
                    if (command == "auth") {
                        val token = msg["args"]!!.jsonObject["token"]!!.jsonPrimitive.content
                        if (token == goodToken) {
                            webSocket.send("""{"message_id":"$id","result":{"authenticated":true,"user":{"username":"u"}},"partial":false}""")
                            afterAuth(webSocket)
                        } else {
                            webSocket.send("""{"message_id":"$id","error_code":401,"details":"Invalid token"}""")
                        }
                    } else {
                        onCommand(command, id, msg).forEach { webSocket.send(it) }
                    }
                }
            }),
        )
    }

    private fun url() = server.url("/").toString().trimEnd('/')

    private fun connect(token: String = "good"): MaClient {
        val client = MaClient(http, callTimeoutMs = 5_000).also { clients.add(it) }
        client.connect(url(), token)
        return client
    }

    private suspend fun MaClient.awaitTerminal(): MaState =
        withTimeout(5_000) { state.first { it !is MaState.Idle && it !is MaState.Connecting } }

    @Test fun socketUrlReplacesPath() {
        assertEquals("https://music.example.com/ws", MaClient.toSocketUrl(" https://music.example.com/ "))
        assertEquals("https://music.example.com/ws", MaClient.toSocketUrl("https://music.example.com/some/path?x=1#y"))
        assertEquals("http://host.example:8095/ws", MaClient.toSocketUrl("http://host.example:8095"))
    }

    @Test fun socketUrlRejectsGarbage() {
        try {
            MaClient.toSocketUrl("not a url")
            fail("expected an exception")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun authenticatesAndReportsServerInfo() = runBlocking {
        fakeServer()
        val client = connect()
        val state = client.awaitTerminal()
        assertTrue("state was $state", state is MaState.Ready)
        assertEquals("2.10.13", (state as MaState.Ready).server.serverVersion)
        assertEquals(65, state.server.schemaVersion)
        client.close()
    }

    @Test fun badTokenIsAuthFailedNotDisconnected() = runBlocking {
        fakeServer()
        val client = connect(token = "bad")
        val state = client.awaitTerminal()
        assertTrue("state was $state", state is MaState.AuthFailed)
        assertEquals("Invalid token", (state as MaState.AuthFailed).reason)
    }

    @Test fun callMatchesReplyByStringMessageId() = runBlocking {
        fakeServer(onCommand = { cmd, id, _ ->
            assertEquals("players/all", cmd)
            listOf("""{"message_id":"$id","result":[{"player_id":"p1","name":"Kitchen","type":"player"}],"partial":false}""")
        })
        val client = connect()
        client.awaitTerminal()
        val result = client.call("players/all")
        val players = MaJson.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(Player.serializer()), result,
        )
        assertEquals(listOf("Kitchen"), players.map { it.name })
        client.close()
    }

    @Test fun partialBatchesAreJoinedInOrder() = runBlocking {
        fakeServer(onCommand = { _, id, _ ->
            listOf(
                """{"message_id":"$id","result":[1,2],"partial":true}""",
                """{"message_id":"$id","result":[3],"partial":true}""",
                """{"message_id":"$id","result":[4,5],"partial":false}""",
            )
        })
        val client = connect()
        client.awaitTerminal()
        val result = client.call("music/tracks/library_items")
        assertEquals(listOf("1", "2", "3", "4", "5"), (result as JsonArray).map { it.jsonPrimitive.content })
        client.close()
    }

    @Test fun serverErrorBecomesMaException() = runBlocking {
        fakeServer(onCommand = { _, id, _ -> listOf("""{"message_id":"$id","error_code":"invalid_command","details":"Command not found"}""") })
        val client = connect()
        client.awaitTerminal()
        try {
            client.call("nope")
            fail("expected MaException")
        } catch (e: MaException) {
            assertEquals("invalid_command", e.code)
            assertEquals("Command not found", e.message)
        }
        client.close()
    }

    @Test fun realErrorShapeHasNumericCodeAndDetails() = runBlocking {
        // Captured from the real server: {"message_id":"8","error_code":12,"details":"Invalid or unsupported command."}
        fakeServer(onCommand = { _, id, _ -> listOf("""{"message_id":"$id","error_code":12,"details":"Invalid or unsupported command."}""") })
        val client = connect()
        client.awaitTerminal()
        try {
            client.call("downbeat/not_a_command")
            fail("expected MaException")
        } catch (e: MaException) {
            assertEquals("12", e.code)
            assertEquals("Invalid or unsupported command.", e.message)
        }
        client.close()
    }

    @Test fun nullResultIsReturnedNotAnError() = runBlocking {
        // The real server answers players/get for an unknown id with result null and no error.
        fakeServer(onCommand = { _, id, _ -> listOf("""{"message_id":"$id","result":null,"partial":false}""") })
        val client = connect()
        client.awaitTerminal()
        assertEquals(kotlinx.serialization.json.JsonNull, client.call("players/get"))
        client.close()
    }

    @Test fun eventsAreEmittedWithRawData() = runBlocking {
        fakeServer(afterAuth = { ws ->
            ws.send("""{"event":"player_updated","object_id":"p1","data":{"player_id":"p1","name":"Kitchen","volume_level":30}}""")
        })
        val client = MaClient(http, callTimeoutMs = 5_000).also { clients.add(it) }
        // Collect before connecting so the event cannot be missed.
        val first = async { withTimeout(5_000) { client.events.first() } }
        client.connect(url(), "good")
        val ev = first.await()
        assertEquals("player_updated", ev.event)
        assertEquals("p1", ev.objectId)
        val player = MaJson.decodeFromJsonElement(Player.serializer(), ev.data!!)
        assertEquals(30, player.volumeLevel)
        client.close()
    }

    @Test fun droppedConnectionFailsPendingCalls() = runBlocking {
        fakeServer(onCommand = { _, _, _ -> emptyList() }) // never answers
        val client = connect()
        client.awaitTerminal()
        val call = async {
            try {
                client.call("players/all")
                null
            } catch (e: MaException) {
                e
            }
        }
        delay(200)
        client.close()
        assertTrue(withTimeout(5_000) { call.await() } != null)
        assertTrue(client.state.value is MaState.Disconnected)
    }

    @Test fun playerDecodingToleratesUnknownAndNullFields() {
        val json = """{"player_id":"p9","name":"Deck","type":"player","provider":"wiim","available":false,
            "volume_level":null,"powered":null,"synced_to":null,"current_media":null,
            "supported_features":["pause","volume_set","brand_new_feature"],"future_field":{"a":1},
            "group_members":["a","b"]}"""
        val p = MaJson.decodeFromString(Player.serializer(), json)
        assertFalse(p.available)
        assertEquals(null, p.volumeLevel)
        assertTrue(p.supports("volume_set"))
        assertFalse(p.supports("seek"))
        assertEquals(2, p.groupMembers.size)
    }

    @Test fun queueDecodingReadsCurrentItem() {
        val json = """{"queue_id":"q1","display_name":"Kitchen","items":146,"shuffle_enabled":true,"repeat_mode":"off",
            "state":"playing","current_index":76,"elapsed_time":82.7,"elapsed_time_last_updated":1792891309.4,
            "current_item":{"queue_item_id":"i1","name":"Artist - Title","duration":292,
            "image":{"type":"thumb","path":"x","provider":"fanarttv","proxy_id":"abc"}}}"""
        val q = MaJson.decodeFromString(PlayerQueue.serializer(), json)
        assertEquals("Artist - Title", q.currentItem?.name)
        assertEquals("abc", q.currentItem?.image?.proxyId)
        assertEquals(146, q.items)
        assertTrue(q.shuffleEnabled)
    }
}
