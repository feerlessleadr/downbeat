package com.feerlessleadr.downbeat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeStore(var session: Session?) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class PlaybackControllerTest {
    private val fake = FakeMaServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = MemoryAppPrefs()
    private lateinit var repo: MusicRepository
    private lateinit var controller: PlaybackController

    private fun players(kitchenState: String = "idle") = """[
      {"player_id":"Kitchen","name":"Kitchen","type":"player","playback_state":"$kitchenState","supported_features":["pause","set_members","volume_set"],
       "can_group_with":["Deck","Pool"]},
      {"player_id":"Deck","name":"Deck","type":"player","supported_features":["pause","set_members","volume_set"],"can_group_with":["Kitchen","Pool"],
       "group_members":["Deck","Pool"]},
      {"player_id":"Pool","name":"Pool","type":"player","supported_features":["pause","set_members","volume_set"],"can_group_with":["Kitchen","Deck"],"synced_to":"Deck"},
      {"player_id":"TV","name":"TV","type":"player","supported_features":["volume_set"]}]"""

    @Before fun setUp() {
        fake.start()
        fake.tokens.add("good")
        fake.results["player_queues/all"] = "[]"
    }

    @After fun tearDown() {
        if (::repo.isInitialized) repo.signOut()
        scope.cancel()
        fake.stop()
    }

    private fun online(kitchenState: String = "idle") = runBlocking {
        fake.results["players/all"] = players(kitchenState)
        repo = MusicRepository(
            OkHttpClient(), FakeStore(Session(fake.url(), "good")), scope,
            requireHttps = false, backoffMs = listOf(50), prefs = prefs,
        )
        controller = PlaybackController(repo)
        repo.start()
        withTimeout(10_000) { repo.state.first { it.phase == Phase.Online } }
    }

    private fun leader(id: String) = repo.state.value.players.first { it.playerId == id }
    private fun last(command: String): JsonObject? = fake.received.last { it.first == command }.second

    @Test fun tickingARoomAddsItToTheSet() = runBlocking {
        online()
        assertNull(controller.tapRoom(leader("Kitchen"), "Deck"))
        val a = last("players/cmd/set_members")!!
        assertEquals("Kitchen", a["target_player"]!!.jsonPrimitive.content)
        assertEquals(listOf("Deck"), a["player_ids_to_add"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun untickingAMemberRemovesIt() = runBlocking {
        online()
        assertNull(controller.tapRoom(leader("Deck"), "Pool"))
        val a = last("players/cmd/set_members")!!
        assertEquals("Deck", a["target_player"]!!.jsonPrimitive.content)
        assertEquals(listOf("Pool"), a["player_ids_to_remove"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun switchingWhilePlayingMovesTheQueueAndSelectsTheRoom() = runBlocking {
        online(kitchenState = "playing")
        assertNull(controller.tapRoom(leader("Kitchen"), "TV"))
        val a = last("player_queues/transfer")!!
        assertEquals("Kitchen", a["source_queue_id"]!!.jsonPrimitive.content)
        assertEquals("TV", a["target_queue_id"]!!.jsonPrimitive.content)
        assertEquals("TV", repo.selectedRoomId.value)
        assertEquals("TV", prefs.lastRoomId)
    }

    @Test fun switchingWhileIdleOnlySelectsTheRoom() = runBlocking {
        online(kitchenState = "idle")
        assertNull(controller.tapRoom(leader("Kitchen"), "TV"))
        assertTrue(fake.received.none { it.first == "player_queues/transfer" })
        assertEquals("TV", repo.selectedRoomId.value)
    }

    @Test fun aFailedTransferKeepsTheOldSelection() = runBlocking {
        online(kitchenState = "playing")
        repo.selectRoom("Kitchen")
        fake.errors["player_queues/transfer"] = "Target is busy"
        assertEquals("Target is busy", controller.tapRoom(leader("Kitchen"), "TV"))
        assertEquals("Kitchen", repo.selectedRoomId.value)
    }

    @Test fun volumeIsGroupVolumeOnlyWhenSeveralRoomsPlayTogether() = runBlocking {
        online()
        assertNull(controller.setSetVolume(leader("Kitchen"), 40))
        assertEquals("players/cmd/volume_set", fake.received.last().first)
        assertNull(controller.setSetVolume(leader("Deck"), 40))
        assertEquals("players/cmd/group_volume", fake.received.last().first)
        assertEquals(40, last("players/cmd/group_volume")!!["volume_level"]!!.jsonPrimitive.int)
    }

    @Test fun transportCommandsUseTheLeadersQueue() = runBlocking {
        online()
        assertNull(controller.playPause(leader("Kitchen")))
        assertEquals("Kitchen", last("player_queues/play_pause")!!["queue_id"]!!.jsonPrimitive.content)
        assertNull(controller.toggleShuffle(leader("Kitchen"), current = false))
        assertTrue(last("player_queues/shuffle")!!["shuffle_enabled"]!!.jsonPrimitive.boolean)
        assertNull(controller.cycleRepeat(leader("Kitchen"), current = "all"))
        assertEquals("one", last("player_queues/repeat")!!["repeat_mode"]!!.jsonPrimitive.content)
    }

    @Test fun aServerErrorBecomesAMessage() = runBlocking {
        online()
        fake.errors["player_queues/next"] = "Nothing to skip to"
        assertEquals("Nothing to skip to", controller.next(leader("Kitchen")))
    }

    @Test fun offlineGivesANotConnectedMessage() = runBlocking {
        repo = MusicRepository(OkHttpClient(), FakeStore(null), scope, requireHttps = false)
        controller = PlaybackController(repo)
        val p = com.feerlessleadr.downbeat.ma.Player(playerId = "x", name = "x")
        assertEquals("Not connected to the server.", controller.playPause(p))
    }

    @Test fun repeatCyclesOffAllOneOff() {
        assertEquals("all", PlaybackController.nextRepeat("off"))
        assertEquals("one", PlaybackController.nextRepeat("all"))
        assertEquals("off", PlaybackController.nextRepeat("one"))
        assertEquals("off", PlaybackController.nextRepeat("unknown"))
    }
}
