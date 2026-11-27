package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.ConfigEntry
import com.feerlessleadr.downbeat.ma.MaJson
import com.feerlessleadr.downbeat.ma.ProviderConfig
import com.feerlessleadr.downbeat.ma.ProviderManifest
import com.feerlessleadr.downbeat.ma.SetupFlowStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FlowStore(var session: Session?) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class SetupFlowTest {
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
        repo = MusicRepository(OkHttpClient(), FlowStore(Session(fake.url(), "good")), scope, requireHttps = false, backoffMs = listOf(50))
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

    private fun manifest(domain: String, type: String = "music", builtin: Boolean = false, multi: Boolean = false, stage: String = "stable", flow: Boolean = true) =
        ProviderManifest(domain = domain, type = type, name = domain.replaceFirstChar { it.uppercase() }, builtin = builtin, multiInstance = multi, stage = stage, hasSetupFlow = flow)

    @Test fun builtInAndAlreadyAddedProvidersAreNotOfferedUnlessTheyAllowSeveral() {
        val manifests = listOf(
            manifest("emby"), manifest("spotify"), manifest("builtin", builtin = true), manifest("filesystem", multi = true),
            manifest("cast", type = "player"), manifest("core", type = "core"), manifest("hass", type = "plugin", stage = "beta"),
        )
        val configs = listOf(
            ProviderConfig(instanceId = "emby--1", domain = "emby", type = "music"),
            ProviderConfig(instanceId = "filesystem--1", domain = "filesystem", type = "music"),
        )
        val groups = addableProviders(manifests, configs)
        assertEquals(listOf("Music sources", "Players", "Plugins"), groups.map { it.first })
        assertEquals(listOf("Filesystem", "Spotify"), groups.first().second.map { it.name })
        assertEquals("Beta", groups.last().second.single().stageLabel)
        assertEquals("", groups.first().second.first().stageLabel)
    }

    @Test fun aSetupFormSendsEveryVisibleEditableFieldThatHasAValue() {
        val entries = listOf(
            ConfigEntry(key = "url", value = JsonPrimitive("http://x")),
            ConfigEntry(key = "empty"),
            ConfigEntry(key = "note", type = "label", value = JsonPrimitive("hello")),
            ConfigEntry(key = "use_auth", type = "boolean", value = JsonPrimitive(true)),
            ConfigEntry(key = "user", dependsOn = "use_auth", value = JsonPrimitive("kev")),
            ConfigEntry(key = "hidden_dep", dependsOn = "missing", value = JsonPrimitive("zzz")),
        )
        val form = ConfigFormState(entries)
        assertEquals(setOf("url", "use_auth", "user"), form.submitValues().keys)
        form.set("use_auth", JsonPrimitive(false))
        assertEquals(setOf("url", "use_auth"), form.submitValues().keys)
    }

    @Test fun theStepShapesDecode() {
        val form = MaJson.decodeFromString(
            SetupFlowStep.serializer(),
            """{"flow_id":"f1","step_id":"auth","type":"form","title":"Sign in","entries":[{"key":"user","type":"string"}],
               "errors":{"user":"Required"},"last_step":false,"url":null,"image":null,"expires_at":null,"result":null}""",
        )
        assertEquals("form", form.type)
        assertEquals("Required", form.errors["user"])
        val external = MaJson.decodeFromString(SetupFlowStep.serializer(), """{"flow_id":"f1","step_id":"oauth","type":"external","url":"https://example.com/login"}""")
        assertEquals("https://example.com/login", external.url)
        val progress = MaJson.decodeFromString(SetupFlowStep.serializer(), """{"flow_id":"f1","step_id":"wait","type":"progress","progress":0.4,"progress_text":"Scanning"}""")
        assertEquals(0.4, progress.progress!!, 0.0001)
    }

    @Test fun startingSubmittingPollingAndAbortingUseTheFlowIdAndValues() = runBlocking {
        online()
        fake.results["config/providers/setup"] = """{"flow_id":"f1","step_id":"s1","type":"form","entries":[{"key":"url","type":"string"}]}"""
        val first = (controller.startSetup("emby") as FlowResult.Step).step
        assertEquals("f1", first.flowId)
        assertEquals("emby", fake.received.last { it.first == "config/providers/setup" }.second!!["provider_domain"]!!.jsonPrimitive.content)

        fake.results["config/flows/submit"] = """{"flow_id":"f1","step_id":"done","type":"finish","last_step":true}"""
        val next = (controller.submitStep("f1", mapOf("url" to JsonPrimitive("http://x"))) as FlowResult.Step).step
        assertEquals("finish", next.type)
        val sent: JsonObject = fake.received.last { it.first == "config/flows/submit" }.second!!
        assertEquals("f1", sent["flow_id"]!!.jsonPrimitive.content)
        assertEquals("http://x", sent["values"]!!.jsonObject["url"]!!.jsonPrimitive.content)

        fake.results["config/flows/get"] = """{"flow_id":"f1","step_id":"wait","type":"progress","progress":0.5}"""
        assertEquals("progress", (controller.refreshStep("f1") as FlowResult.Step).step.type)

        controller.abortSetup("f1")
        assertEquals("f1", fake.received.last { it.first == "config/flows/abort" }.second!!["flow_id"]!!.jsonPrimitive.content)
    }

    @Test fun aRefusedStartOrSubmitComesBackAsTheServersMessage() = runBlocking {
        online()
        fake.errors["config/providers/setup"] = "Provider already configured"
        assertEquals("Provider already configured", (controller.startSetup("emby") as FlowResult.Failed).message)
        fake.errors["config/flows/submit"] = "Flow expired"
        assertEquals("Flow expired", (controller.submitStep("f1", emptyMap()) as FlowResult.Failed).message)
    }

    @Test fun offlineSaysSo() = runBlocking {
        assertTrue(controller.startSetup("emby") is FlowResult.Failed)
        assertFalse(controller.refreshStep("f1") is FlowResult.Step)
    }
}
