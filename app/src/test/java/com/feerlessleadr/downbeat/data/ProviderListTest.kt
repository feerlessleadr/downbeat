package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaJson
import com.feerlessleadr.downbeat.ma.ProviderConfig
import com.feerlessleadr.downbeat.ma.ProviderError
import com.feerlessleadr.downbeat.ma.ProviderManifest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderListTest {
    private fun cfg(id: String, type: String, status: String? = "loaded", enabled: Boolean = true, name: String? = null, error: String? = null) =
        ProviderConfig(
            instanceId = id, domain = id.substringBefore("--"), type = type, enabled = enabled, name = name, status = status,
            lastError = error?.let { ProviderError(1, it) },
        )

    @Test fun statusFollowsTheServersStatusWord() {
        assertEquals(ProviderStatus.Working, providerStatus(cfg("emby", "music")))
        assertEquals(ProviderStatus.Starting, providerStatus(cfg("emby", "music", status = "loading")))
        assertEquals(ProviderStatus.SignInNeeded, providerStatus(cfg("spotify", "music", status = "auth_required")))
        assertEquals(ProviderStatus.Incompatible, providerStatus(cfg("x", "music", status = "incompatible")))
        assertEquals(ProviderStatus.Problem, providerStatus(cfg("x", "music", status = "error", error = "boom")))
        assertEquals(ProviderStatus.Off, providerStatus(cfg("x", "music", status = "loaded", enabled = false)))
        assertEquals(ProviderStatus.Off, providerStatus(cfg("x", "music", status = "disabled")))
        assertEquals(ProviderStatus.Working, providerStatus(cfg("x", "music", status = null)))
        assertEquals(ProviderStatus.Problem, providerStatus(cfg("x", "music", status = null, error = "boom")))
    }

    @Test fun groupsAreOrderedAndSortedAndUnnamedKindsAreLeftOut() {
        val configs = listOf(
            cfg("chromecast--1", "player", name = "Cast"), cfg("emby--1", "music", name = "Emby"),
            cfg("fanarttv--1", "metadata"), cfg("zeta--1", "music", name = "alpha music"), cfg("core--1", "core"),
            cfg("hass--1", "plugin"),
        )
        val groups = providerGroups(configs, emptyList())
        assertEquals(listOf("Music sources", "Players", "Metadata", "Plugins"), groups.map { it.title })
        assertEquals(listOf("alpha music", "Emby"), groups.first().rows.map { it.name })
    }

    @Test fun theNameFallsBackToTheDefaultNameThenTheManifestThenTheDomain() {
        val manifests = listOf(ProviderManifest(domain = "emby", name = "Emby Media Server", description = "Plays from Emby"))
        val rows = providerGroups(
            listOf(
                ProviderConfig(instanceId = "emby--1", domain = "emby", type = "music", defaultName = "Emby (default)"),
                ProviderConfig(instanceId = "emby--2", domain = "emby", type = "music"),
                ProviderConfig(instanceId = "plain--1", domain = "plain", type = "music"),
            ),
            manifests,
        ).first().rows.associateBy { it.instanceId }
        assertEquals("Emby (default)", rows["emby--1"]!!.name)
        assertEquals("Emby Media Server", rows["emby--2"]!!.name)
        assertEquals("plain", rows["plain--1"]!!.name)
        assertEquals("Plays from Emby", rows["emby--1"]!!.detail)
        assertEquals("Music source", rows["plain--1"]!!.detail)
    }

    @Test fun anErrorIsShownOnlyWhenTheProviderHasAProblem() {
        val rows = providerGroups(
            listOf(cfg("a--1", "music", status = "error", error = "No route to host"), cfg("b--1", "music", status = "loaded", error = "old error")),
            emptyList(),
        ).first().rows.associateBy { it.instanceId }
        assertEquals("No route to host", rows["a--1"]!!.error)
        assertNull(rows["b--1"]!!.error)
    }

    @Test fun theServersShapeDecodesAndIgnoresWhatItDoesNotNeed() {
        val json = """[{"values":{"k":{"key":"k","type":"string"}},"type":"music","domain":"emby","instance_id":"emby--1","enabled":true,
            "name":"Emby","default_name":"Emby","status":"loaded","last_error":null,"setup_data":{"a":1}}]"""
        val configs = MaJson.decodeFromString(ListSerializer(ProviderConfig.serializer()), json)
        assertEquals("emby--1", configs.single().instanceId)
        val manifestJson = """[{"type":"music","domain":"emby","name":"Emby","description":"d","codeowners":["x"],"stage":"stable",
            "requirements":[],"multi_instance":false,"builtin":false,"allow_disable":true,"has_setup_flow":true,"credits":[]}]"""
        val manifests = MaJson.decodeFromString(ListSerializer(ProviderManifest.serializer()), manifestJson)
        assertEquals(true, manifests.single().hasSetupFlow)
    }
}
