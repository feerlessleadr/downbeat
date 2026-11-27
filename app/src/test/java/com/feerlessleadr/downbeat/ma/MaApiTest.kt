package com.feerlessleadr.downbeat.ma

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Checks command names and argument names that MaApi sends, and how it reads replies. */
class MaApiTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private val received = CopyOnWriteArrayList<Pair<String, JsonObject?>>()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    private val clients = mutableListOf<MaClient>()

    /** Result JSON text per command; commands not listed answer null. */
    private val results = mutableMapOf<String, String>()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sockets.add(webSocket)
                    webSocket.send("""{"server_id":"x","server_version":"2.10.13","schema_version":65}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = MaJson.parseToJsonElement(text).jsonObject
                    val command = msg["command"]!!.jsonPrimitive.content
                    val id = msg["message_id"]!!.jsonPrimitive.content
                    if (command == "auth") {
                        webSocket.send("""{"message_id":"$id","result":{"authenticated":true},"partial":false}""")
                        return
                    }
                    received.add(command to (msg["args"] as? JsonObject))
                    val result = results[command] ?: "null"
                    webSocket.send("""{"message_id":"$id","result":$result,"partial":false}""")
                }
            }),
        )
    }

    @After fun tearDown() {
        clients.forEach { it.close() }
        sockets.forEach { it.close(1000, "done") }
        server.shutdown()
    }

    private fun api(): MaApi = runBlocking {
        val client = MaClient(http, callTimeoutMs = 5_000).also { clients.add(it) }
        client.connect(server.url("/").toString().trimEnd('/'), "t")
        withTimeout(5_000) { client.state.first { it is MaState.Ready } }
        MaApi(client)
    }

    private fun lastArgs(): JsonObject = received.last().second!!

    @Test fun playersAsksForUnavailableToo() = runBlocking {
        results["players/all"] = """[{"player_id":"p1","name":"Kitchen"},{"player_id":"p2","name":"Deck","available":false}]"""
        val players = api().players()
        assertEquals("players/all", received.last().first)
        assertTrue(lastArgs()["return_unavailable"]!!.jsonPrimitive.boolean)
        assertEquals(listOf("Kitchen", "Deck"), players.map { it.name })
        assertFalse(players[1].available)
    }

    @Test fun nullResultsBecomeNullOrEmpty() = runBlocking {
        val api = api()
        assertNull(api.activeQueue("nope"))
        assertNull(api.queue("nope"))
        assertTrue(api.players().isEmpty())
        assertTrue(api.search("x").artists.isEmpty())
    }

    @Test fun playMediaSingleUriIsAString() = runBlocking {
        api().playMedia("q1", listOf("library://album/1"))
        assertEquals("player_queues/play_media", received.last().first)
        val a = lastArgs()
        assertEquals("q1", a["queue_id"]!!.jsonPrimitive.content)
        assertEquals("library://album/1", a["media"]!!.jsonPrimitive.content)
        assertEquals("replace", a["option"]!!.jsonPrimitive.content)
        assertFalse(a["radio_mode"]!!.jsonPrimitive.boolean)
    }

    @Test fun playMediaSeveralUrisIsAnArray() = runBlocking {
        api().playMedia("q1", listOf("a://1", "a://2"), option = "add")
        val media = lastArgs()["media"]!!.jsonArray
        assertEquals(listOf("a://1", "a://2"), media.map { it.jsonPrimitive.content })
        assertEquals("add", lastArgs()["option"]!!.jsonPrimitive.content)
    }

    @Test fun setMembersOmitsEmptyLists() = runBlocking {
        val api = api()
        api.setMembers("leader", add = listOf("a", "b"))
        assertEquals("players/cmd/set_members", received.last().first)
        assertTrue(lastArgs().containsKey("player_ids_to_add"))
        assertFalse(lastArgs().containsKey("player_ids_to_remove"))
        api.setMembers("leader", remove = listOf("a"))
        assertFalse(lastArgs().containsKey("player_ids_to_add"))
        assertEquals(listOf("a"), lastArgs()["player_ids_to_remove"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun volumeIsClampedTo0To100() = runBlocking {
        val api = api()
        api.setVolume("p1", 150)
        assertEquals(100, lastArgs()["volume_level"]!!.jsonPrimitive.int)
        api.setGroupVolume("p1", -5)
        assertEquals("players/cmd/group_volume", received.last().first)
        assertEquals(0, lastArgs()["volume_level"]!!.jsonPrimitive.int)
    }

    @Test fun transportUsesQueueCommandsWithQueueId() = runBlocking {
        val api = api()
        api.playPause("q"); assertEquals("player_queues/play_pause", received.last().first)
        api.next("q"); assertEquals("player_queues/next", received.last().first)
        api.previous("q"); assertEquals("player_queues/previous", received.last().first)
        api.seek("q", 42); assertEquals(42, lastArgs()["position"]!!.jsonPrimitive.int)
        api.shuffle("q", true); assertTrue(lastArgs()["shuffle_enabled"]!!.jsonPrimitive.boolean)
        api.repeat("q", "all"); assertEquals("all", lastArgs()["repeat_mode"]!!.jsonPrimitive.content)
        api.moveQueueItem("q", "item", -1); assertEquals(-1, lastArgs()["pos_shift"]!!.jsonPrimitive.int)
        api.removeQueueItem("q", "item"); assertEquals("item", lastArgs()["item_id_or_index"]!!.jsonPrimitive.content)
        api.transferQueue("a", "b")
        assertEquals("a", lastArgs()["source_queue_id"]!!.jsonPrimitive.content)
        assertEquals("b", lastArgs()["target_queue_id"]!!.jsonPrimitive.content)
    }

    @Test fun libraryUsesTypeInCommandAndOnlySetsGivenOptions() = runBlocking {
        results["music/albums/library_items"] =
            """[{"item_id":"1","provider":"library","name":"Album","uri":"library://album/1","media_type":"album","year":1999,
               "artists":[{"name":"A"},{"name":"B"}],"metadata":{"images":[{"proxy_id":"abc","path":"x","provider":"p"}]}}]"""
        val api = api()
        val items = api.library("albums", limit = 3)
        assertEquals("music/albums/library_items", received.last().first)
        assertFalse(lastArgs().containsKey("search"))
        assertFalse(lastArgs().containsKey("favorite"))
        assertEquals("A, B", items.single().artistNames)
        assertEquals("abc", items.single().image?.proxyId)
        api.library("radios", search = "jazz", favoriteOnly = true)
        assertEquals("jazz", lastArgs()["search"]!!.jsonPrimitive.content)
        assertTrue(lastArgs()["favorite"]!!.jsonPrimitive.boolean)
        try {
            api.library("bogus")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun searchDecodesGroupedResults() = runBlocking {
        results["music/search"] =
            """{"artists":[{"name":"Band"}],"albums":[],"tracks":[{"name":"Song","album":{"name":"Rec","image":{"proxy_id":"z"}}}],
               "playlists":[],"radio":[],"genres":[{"name":"Rock"}],"audiobooks":[]}"""
        val r = api().search("x")
        assertEquals("music/search", received.last().first)
        assertEquals("x", lastArgs()["search_query"]!!.jsonPrimitive.content)
        assertEquals("Band", r.artists.single().name)
        assertEquals("z", r.tracks.single().image?.proxyId)
    }

    @Test fun imageUrlUsesTypedHostAndProxyId() {
        val image = MediaItemImage(proxyId = "abc123")
        assertEquals(
            "https://music.example.com/imageproxy/abc123?size=256&fmt=jpg",
            ImageUrls.forImage("https://music.example.com/", image, 256),
        )
        assertNull(ImageUrls.forImage("https://music.example.com", MediaItemImage()))
        assertNull(ImageUrls.forImage("https://music.example.com", null))
    }

    @Test fun rewriteHostKeepsPathAndQuery() {
        assertEquals(
            "https://music.example.com/imageproxy/abc?size=512&fmt=jpg",
            ImageUrls.rewriteHost(
                "https://music.example.com",
                "http://192.0.2.7:8095/imageproxy/abc?size=512&fmt=jpg",
            ),
        )
        assertNull(ImageUrls.rewriteHost("https://music.example.com", null))
    }
}
