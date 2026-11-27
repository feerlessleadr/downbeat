package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.ProviderConfig
import com.feerlessleadr.downbeat.ma.ProviderManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class ProvStore(var session: Session?) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class ProviderControllerTest {
    private val fake = FakeMaServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: MusicRepository
    private lateinit var controller: ProviderController

    @Before fun setUp() {
        fake.start()
        fake.tokens.add("good")
        fake.results["players/all"] = "[]"
        fake.results["player_queues/all"] = "[]"
        fake.results["config/providers"] = "[]"
        repo = MusicRepository(OkHttpClient(), ProvStore(Session(fake.url(), "good")), scope, requireHttps = false, backoffMs = listOf(50))
        controller = ProviderController(repo)
    }

    @After fun tearDown() {
        repo.signOut()
        scope.cancel()
        fake.stop()
    }

    private fun online() = runBlocking {
        repo.start()
        withTimeout(10_000) { repo.state.first { it.phase == Phase.Online } }
    }

    private fun row(id: String = "emby--1", domain: String = "emby") =
        ProviderRow(instanceId = id, name = "Emby", detail = "", status = ProviderStatus.Working, error = null, domain = domain, type = "music")

    private fun last(command: String): JsonObject = fake.received.last { it.first == command }.second!!

    @Test fun reloadAndRemoveSendTheInstanceId() = runBlocking {
        online()
        assertNull(controller.reload("emby--1"))
        assertEquals("emby--1", last("config/providers/reload")["instance_id"]!!.jsonPrimitive.content)
        assertNull(controller.remove("emby--1"))
        assertEquals("emby--1", last("config/providers/remove")["instance_id"]!!.jsonPrimitive.content)
    }

    @Test fun turningOffSendsEnabledFalseInsideTheValues() = runBlocking {
        online()
        assertNull(controller.setEnabled(row(), false))
        val args = last("config/providers/save")
        assertEquals("emby", args["provider_domain"]!!.jsonPrimitive.content)
        assertEquals("emby--1", args["instance_id"]!!.jsonPrimitive.content)
        assertFalse(args["values"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertNull(controller.setEnabled(row(), true))
        assertTrue(last("config/providers/save")["values"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test fun syncAsksForOneProviderInstance() = runBlocking {
        online()
        assertNull(controller.sync("emby--1"))
        val providers: JsonArray = last("music/sync")["providers"]!!.jsonArray
        assertEquals(listOf("emby--1"), providers.map { it.jsonPrimitive.content })
    }

    @Test fun aRefusalComesBackAsTheServersMessage() = runBlocking {
        online()
        fake.errors["config/providers/remove"] = "Cannot remove a built-in provider"
        assertEquals("Cannot remove a built-in provider", controller.remove("builtin--1"))
    }

    @Test fun offlineCallsSaySo() = runBlocking {
        assertEquals("Not connected to the server.", controller.reload("emby--1"))
    }

    @Test fun onlyWhatTheManifestAllowsIsOffered() {
        val manifests = listOf(
            ProviderManifest(domain = "emby", type = "music", name = "Emby", allowDisable = true, builtin = false),
            ProviderManifest(domain = "builtin", type = "music", name = "Built-in", allowDisable = false, builtin = true),
            ProviderManifest(domain = "fanarttv", type = "metadata", name = "fanart.tv", allowDisable = true, builtin = true),
        )
        val configs = listOf(
            ProviderConfig(instanceId = "emby--1", domain = "emby", type = "music"),
            ProviderConfig(instanceId = "builtin--1", domain = "builtin", type = "music"),
            ProviderConfig(instanceId = "fanarttv--1", domain = "fanarttv", type = "metadata"),
            ProviderConfig(instanceId = "mystery--1", domain = "mystery", type = "player"),
            ProviderConfig(instanceId = "off--1", domain = "emby", type = "music", enabled = false),
        )
        val rows = providerGroups(configs, manifests).flatMap { it.rows }.associateBy { it.instanceId }
        val emby = rows["emby--1"]!!
        assertTrue(emby.canTurnOff && emby.canRemove && emby.canSync)
        val builtin = rows["builtin--1"]!!
        assertFalse(builtin.canTurnOff); assertFalse(builtin.canRemove); assertTrue(builtin.canSync)
        val fanart = rows["fanarttv--1"]!!
        assertTrue(fanart.canTurnOff); assertFalse(fanart.canRemove); assertFalse(fanart.canSync)
        // A provider with no manifest gets nothing risky.
        val mystery = rows["mystery--1"]!!
        assertFalse(mystery.canTurnOff); assertFalse(mystery.canRemove); assertFalse(mystery.canSync)
        // Off: can be turned on (the screen offers it when not enabled), but not synced.
        val off = rows["off--1"]!!
        assertFalse(off.enabled); assertFalse(off.canSync)
    }
}
