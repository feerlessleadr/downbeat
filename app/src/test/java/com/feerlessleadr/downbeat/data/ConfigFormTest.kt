package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.ConfigEntry
import com.feerlessleadr.downbeat.ma.MaJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class CfgStore(var session: Session?) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class ConfigFormTest {
    private fun e(
        key: String, type: String = "string", value: String? = null, required: Boolean = false, advanced: Boolean = false,
        dependsOn: String? = null, dependsOnValue: String? = null, dependsOnValueNot: String? = null,
        hidden: Boolean = false, readOnly: Boolean = false, range: List<Int>? = null,
    ) = ConfigEntry(
        key = key, type = type, value = value?.let { JsonPrimitive(it) }, required = required, advanced = advanced,
        dependsOn = dependsOn, dependsOnValue = dependsOnValue?.let { JsonPrimitive(it) },
        dependsOnValueNot = dependsOnValueNot?.let { JsonPrimitive(it) }, hidden = hidden, readOnly = readOnly, range = range,
    )

    @Test fun aFieldDependingOnAnotherShowsOnlyWhenItIsOn() {
        val form = ConfigFormState(listOf(e("use_login", "boolean"), e("user", dependsOn = "use_login")))
        assertFalse(form.isVisible(form.entries[1]))
        form.set("use_login", JsonPrimitive(true))
        assertTrue(form.isVisible(form.entries[1]))
        form.set("use_login", JsonPrimitive(false))
        assertFalse(form.isVisible(form.entries[1]))
    }

    @Test fun dependsOnValueAndDependsOnValueNotCompareTheValue() {
        val form = ConfigFormState(
            listOf(
                e("mode", value = "basic"),
                e("only_advanced_mode", dependsOn = "mode", dependsOnValue = "expert"),
                e("not_basic", dependsOn = "mode", dependsOnValueNot = "basic"),
            ),
        )
        assertFalse(form.isVisible(form.entries[1]))
        assertFalse(form.isVisible(form.entries[2]))
        form.set("mode", JsonPrimitive("expert"))
        assertTrue(form.isVisible(form.entries[1]))
        assertTrue(form.isVisible(form.entries[2]))
    }

    @Test fun hiddenFieldsNeverShowAndAdvancedOnesOnlyWhenAsked() {
        val form = ConfigFormState(listOf(e("a"), e("secret_hidden", hidden = true), e("adv", advanced = true)))
        assertEquals(listOf("a"), form.visibleEntries(showAdvanced = false).map { it.key })
        assertEquals(listOf("a", "adv"), form.visibleEntries(showAdvanced = true).map { it.key })
        assertTrue(form.hasAdvanced())
    }

    @Test fun requiredVisibleFieldsMustHaveAValueButBooleansAndHiddenOnesDoNot() {
        val form = ConfigFormState(
            listOf(
                e("host", required = true), e("flag", "boolean", required = true), e("opt", required = false),
                e("dep", required = true, dependsOn = "flag"), e("label_only", "label", required = true),
            ),
        )
        assertEquals(listOf("host"), form.missingRequired().map { it.key })
        form.set("flag", JsonPrimitive(true))
        assertEquals(listOf("host", "dep"), form.missingRequired().map { it.key })
        form.setText(form.entries[0], "  ")
        assertEquals(listOf("host", "dep"), form.missingRequired().map { it.key })
        form.setText(form.entries[0], "example")
        form.setText(form.entries[3], "x")
        assertTrue(form.missingRequired().isEmpty())
    }

    @Test fun numbersMustParseAndFitTheRange() {
        val form = ConfigFormState(listOf(e("port", "integer", range = listOf(1, 65535)), e("ratio", "float")))
        form.setText(form.entries[0], "8095")
        assertNull(form.problem(form.entries[0]))
        assertEquals(JsonPrimitive(8095L), form.value("port"))
        form.setText(form.entries[0], "99999")
        assertEquals("Enter a value from 1 to 65535.", form.problem(form.entries[0]))
        form.setText(form.entries[0], "abc")
        assertEquals("Enter a whole number.", form.problem(form.entries[0]))
        form.setText(form.entries[1], "1.5")
        assertNull(form.problem(form.entries[1]))
        form.setText(form.entries[1], "x")
        assertEquals("Enter a number.", form.problem(form.entries[1]))
        assertEquals(setOf("port", "ratio"), form.problems().keys)
    }

    @Test fun onlyChangedEditableValuesAreSentAndASecretNotEditedIsNeverSentBack() {
        val form = ConfigFormState(
            listOf(
                e("host", value = "old"), e("password", "secure_string", value = "********"), e("note", "label", value = "info"),
                e("locked", value = "x", readOnly = true), e("enabled_thing", "boolean"),
            ),
        )
        assertFalse(form.hasChanges())
        assertTrue(form.changedValues().isEmpty())
        form.setText(form.entries[0], "new")
        form.set("locked", JsonPrimitive("changed"))
        form.set("note", JsonPrimitive("changed"))
        form.set("enabled_thing", JsonPrimitive(true))
        assertEquals(setOf("host", "enabled_thing"), form.changedValues().keys)
        // Retyping the same text is not a change.
        form.setText(form.entries[0], "old")
        assertEquals(setOf("enabled_thing"), form.changedValues().keys)
        form.setText(form.entries[1], "newsecret")
        assertEquals(setOf("enabled_thing", "password"), form.changedValues().keys)
    }

    @Test fun listsAreEditedOneItemPerLine() {
        val entry = ConfigEntry(key = "paths", type = "splitted_string", value = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
        val form = ConfigFormState(listOf(entry))
        assertEquals("a\nb", form.text(entry))
        form.setText(entry, "a\n\n  c  \n")
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("c"))), form.value("paths"))
        assertTrue(form.isChanged(entry))
    }

    @Test fun theServersShapeDecodes() {
        val json = """[
          {"key":"enabled","type":"boolean","default_value":true,"required":false,"label":"Enabled","category":"generic","advanced":false,"value":true},
          {"key":"url","type":"string","required":true,"label":"Server address","value":"http://x","depends_on":"enabled","depends_on_value":true,
           "translation_key":"ignored"},
          {"key":"quality","type":"string","options":[{"value":"low","title":"Low"},{"value":"high","title":"High","disabled":true}],"value":"low"},
          {"key":"port","type":"integer","range":[1,65535],"default_value":8095,"value":null},
          {"key":"test","type":"action","action":"test_connection","action_label":"Test the connection"}
        ]"""
        val entries = MaJson.decodeFromString(ListSerializer(ConfigEntry.serializer()), json)
        assertEquals(5, entries.size)
        assertEquals("low", entries[2].options[0].value!!.jsonPrimitive.content)
        assertTrue(entries[2].options[1].disabled)
        assertEquals(listOf(1, 65535), entries[3].range)
        assertEquals("test_connection", entries[4].action)
        val form = ConfigFormState(entries)
        // The null value falls back to the default.
        assertEquals(JsonPrimitive(8095), form.value("port"))
    }

    @Test fun anActionAnswerIsAMessageALinkFieldsOrNothing() {
        val message = parseActionResult(MaJson.parseToJsonElement("""{"message":"Connected","open_url":null}"""))
        assertEquals("Connected", message.message)
        assertNull(message.openUrl)
        assertEquals("https://example.com/login", parseActionResult(MaJson.parseToJsonElement("""{"open_url":"https://example.com/login"}""")).openUrl)
        val fields = parseActionResult(MaJson.parseToJsonElement("""[{"key":"code","type":"pairing_code","value":"1234"}]"""))
        assertEquals("code", fields.entries!!.single().key)
        assertNull(parseActionResult(JsonNull).message)
        assertNull(parseActionResult(JsonArray(emptyList())).entries)
    }
}

class ProviderFormControllerTest {
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
        repo = MusicRepository(OkHttpClient(), CfgStore(Session(fake.url(), "good")), scope, requireHttps = false, backoffMs = listOf(50))
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

    private val row = ProviderRow(instanceId = "emby--1", name = "Emby", detail = "", status = ProviderStatus.Working, error = null, domain = "emby", type = "music")

    @Test fun theFormIsReadWithTheInstanceId() = runBlocking {
        online()
        fake.results["config/providers/get_entries"] = """[{"key":"url","type":"string","value":"http://x"}]"""
        val result = controller.entries("emby--1")
        assertEquals("url", (result as EntriesResult.Loaded).entries.single().key)
        assertEquals("emby--1", fake.received.last { it.first == "config/providers/get_entries" }.second!!["instance_id"]!!.jsonPrimitive.content)
    }

    @Test fun savingSendsOnlyTheGivenValuesAndNullsAreExplicit() = runBlocking {
        online()
        assertNull(controller.save(row, mapOf("url" to JsonPrimitive("http://y"), "port" to null)))
        val args = fake.received.last { it.first == "config/providers/save" }.second!!
        assertEquals("emby", args["provider_domain"]!!.jsonPrimitive.content)
        assertEquals("emby--1", args["instance_id"]!!.jsonPrimitive.content)
        val values: JsonObject = args["values"]!!.jsonObject
        assertEquals(setOf("url", "port"), values.keys)
        assertEquals("http://y", values["url"]!!.jsonPrimitive.content)
        assertTrue(values["port"] is JsonNull)
    }

    @Test fun aSaveRefusalAndAFormErrorComeBackAsMessages() = runBlocking {
        online()
        fake.errors["config/providers/save"] = "Invalid server address"
        assertEquals("Invalid server address", controller.save(row, mapOf("url" to JsonPrimitive("x"))))
        fake.errors["config/providers/get_entries"] = "No such provider"
        assertEquals("No such provider", (controller.entries("nope--1") as EntriesResult.Failed).message)
    }

    @Test fun runningAnActionReturnsItsOutcome() = runBlocking {
        online()
        fake.results["config/providers/invoke_action"] = """{"message":"Connected"}"""
        val outcome = controller.action("emby--1", "test_connection")
        assertEquals("Connected", outcome.message)
        val args = fake.received.last { it.first == "config/providers/invoke_action" }.second!!
        assertEquals("test_connection", args["action"]!!.jsonPrimitive.content)
        assertNotNull(args["instance_id"])
        fake.errors["config/providers/invoke_action"] = "Could not connect"
        assertEquals("Could not connect", controller.action("emby--1", "test_connection").error)
    }
}
