package com.feerlessleadr.downbeat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemoryStore(var session: Session? = null) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class MusicRepositoryTest {
    private val fake = FakeMaServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient()
    private val repos = mutableListOf<MusicRepository>()

    private val twoPlayers = """[
        {"player_id":"p1","name":"Kitchen","type":"player","playback_state":"idle","volume_level":30},
        {"player_id":"p2","name":"Deck","type":"player","playback_state":"idle","volume_level":25}]"""

    @Before fun setUp() {
        fake.start()
        fake.results["players/all"] = twoPlayers
        fake.results["player_queues/all"] = "[]"
    }

    @After fun tearDown() {
        repos.forEach { it.signOut() }
        scope.cancel()
        fake.stop()
    }

    private fun repo(store: SessionStore, requireHttps: Boolean = false): MusicRepository =
        MusicRepository(http, store, scope, requireHttps = requireHttps, backoffMs = listOf(50, 100)).also { repos.add(it) }

    private suspend fun MusicRepository.await(cond: (RepoState) -> Boolean): RepoState =
        withTimeout(10_000) { state.first(cond) }

    @Test fun startWithoutSessionIsSignedOut() = runBlocking {
        val r = repo(MemoryStore())
        r.start()
        assertEquals(Phase.SignedOut, r.state.value.phase)
    }

    @Test fun signInStoresALongLivedTokenAndGoesOnline() = runBlocking {
        fake.tokens.add("SESSION")
        val store = MemoryStore()
        val r = repo(store)
        assertEquals(SignInResult.Success, r.signIn(fake.url(), "kev", "secret"))
        val s = r.await { it.phase == Phase.Online }
        assertEquals(listOf("Kitchen", "Deck"), s.players.map { it.name })
        // The stored token is the long-lived one, never the password.
        assertEquals("LONG", store.session?.token)
        assertTrue(fake.lastLoginBody!!.contains("builtin"))
        assertTrue(fake.lastLoginBody!!.contains("kev"))
        assertTrue(fake.received.any { it.first == "auth/token/create" })
    }

    @Test fun signInFallsBackToSessionTokenWhenTokenCreationFails() = runBlocking {
        // The session token is not accepted for websocket auth here, so token creation cannot run.
        val store = MemoryStore()
        val r = repo(store)
        assertEquals(SignInResult.Success, r.signIn(fake.url(), "kev", "secret"))
        assertEquals("SESSION", store.session?.token)
    }

    @Test fun badCredentialsStoreNothing() = runBlocking {
        fake.loginStatus = 400
        fake.loginBody = """{"error":"Invalid credentials"}"""
        val store = MemoryStore()
        val r = repo(store)
        assertEquals(SignInResult.InvalidCredentials, r.signIn(fake.url(), "kev", "wrong"))
        assertNull(store.session)
    }

    @Test fun plainHttpIsRefusedWhenHttpsRequired() = runBlocking {
        val r = repo(MemoryStore(), requireHttps = true)
        val result = r.signIn("http://music.example.com", "kev", "secret")
        assertTrue(result is SignInResult.Failed)
        assertNull(fake.lastLoginBody)
    }

    @Test fun garbageAddressIsRefused() = runBlocking {
        val result = repo(MemoryStore()).signIn("not a url", "kev", "secret")
        assertTrue(result is SignInResult.Failed)
    }

    @Test fun startWithStoredTokenConnectsAndAppliesPlayerEvents() = runBlocking {
        fake.tokens.add("good")
        val r = repo(MemoryStore(Session(fake.url(), "good")))
        r.start()
        r.await { it.phase == Phase.Online && it.players.size == 2 }
        fake.push("""{"event":"player_updated","object_id":"p1","data":{"player_id":"p1","name":"Kitchen","type":"player","playback_state":"playing","volume_level":55}}""")
        val s = r.await { st -> st.players.firstOrNull { it.playerId == "p1" }?.volumeLevel == 55 }
        assertEquals("playing", s.players.first { it.playerId == "p1" }.playbackState)
        assertEquals(2, s.players.size)
    }

    @Test fun playerAddedAndRemovedEventsChangeTheList() = runBlocking {
        fake.tokens.add("good")
        val r = repo(MemoryStore(Session(fake.url(), "good")))
        r.start()
        r.await { it.phase == Phase.Online }
        fake.push("""{"event":"player_added","object_id":"p3","data":{"player_id":"p3","name":"Patio","type":"player"}}""")
        r.await { st -> st.players.any { it.playerId == "p3" } }
        fake.push("""{"event":"player_removed","object_id":"p1"}""")
        val s = r.await { st -> st.players.none { it.playerId == "p1" } }
        assertEquals(setOf("p2", "p3"), s.players.map { it.playerId }.toSet())
    }

    @Test fun badStoredTokenSignsOutAndClearsTheStore() = runBlocking {
        val store = MemoryStore(Session(fake.url(), "expired"))
        val r = repo(store)
        r.start()
        val s = r.await { it.phase == Phase.SignedOut }
        assertTrue(s.message!!.contains("expired"))
        assertNull(store.session)
    }

    @Test fun droppedConnectionReconnectsAndKeepsRooms() = runBlocking {
        fake.tokens.add("good")
        val r = repo(MemoryStore(Session(fake.url(), "good")))
        r.start()
        r.await { it.phase == Phase.Online }
        assertEquals(1, fake.connections.get())
        fake.dropConnections()
        withTimeout(10_000) { while (fake.connections.get() < 2) delay(20) }
        val s = r.await { it.phase == Phase.Online && it.players.size == 2 }
        assertNotNull(s.players.firstOrNull { it.name == "Kitchen" })
        assertNotNull(r.api)
    }

    @Test fun playbackChangeRereadsTheQueue() = runBlocking {
        fake.tokens.add("good")
        fake.results["player_queues/get"] =
            """{"queue_id":"p1","display_name":"Kitchen","items":5,"state":"playing","current_item":{"queue_item_id":"i1","name":"Artist - Title"}}"""
        val r = repo(MemoryStore(Session(fake.url(), "good")))
        r.start()
        r.await { it.phase == Phase.Online }
        assertTrue(r.state.value.queues.isEmpty())
        fake.push("""{"event":"player_updated","object_id":"p1","data":{"player_id":"p1","name":"Kitchen","type":"player","playback_state":"playing"}}""")
        val s = r.await { it.queues.containsKey("p1") }
        assertEquals("Artist - Title", s.queues.getValue("p1").currentItem?.name)
        assertEquals(5, s.queues.getValue("p1").items)
    }

    @Test fun volumeOnlyChangeDoesNotRereadTheQueue() = runBlocking {
        fake.tokens.add("good")
        val r = repo(MemoryStore(Session(fake.url(), "good")))
        r.start()
        r.await { it.phase == Phase.Online }
        fake.push("""{"event":"player_updated","object_id":"p1","data":{"player_id":"p1","name":"Kitchen","type":"player","playback_state":"idle","volume_level":99}}""")
        r.await { st -> st.players.firstOrNull { it.playerId == "p1" }?.volumeLevel == 99 }
        delay(200)
        assertTrue(fake.received.none { it.first == "player_queues/get" })
    }
}
