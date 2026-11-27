package com.feerlessleadr.downbeat.ma

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Decodes real server samples when the environment variable DOWNBEAT_SAMPLES points at the folder written by
 * tools/Sample-MusicAssistant.ps1. Skipped otherwise (the samples contain device identifiers and are never committed).
 */
class RealSamplesTest {
    private fun sample(name: String): File? {
        val dir = System.getenv("DOWNBEAT_SAMPLES") ?: return null
        return File(dir, name).takeIf { it.exists() }
    }

    @Test fun decodesRealPlayers() {
        val f = sample("10-players-all.json")
        assumeTrue("no samples", f != null)
        val result = MaJson.parseToJsonElement(f!!.readText()).jsonObject["result"] as JsonArray
        val players = MaJson.decodeFromJsonElement(ListSerializer(Player.serializer()), result)
        assertEquals(result.size, players.size)
        assertTrue(players.isNotEmpty())
        assertTrue(players.all { it.playerId.isNotBlank() && it.name.isNotBlank() })
        println("decoded ${players.size} players; types=" + players.groupingBy { it.type }.eachCount())
    }

    @Test fun decodesRealQueues() {
        val f = sample("11-queues-all.json")
        assumeTrue("no samples", f != null)
        val result = MaJson.parseToJsonElement(f!!.readText()).jsonObject["result"] as JsonArray
        val queues = MaJson.decodeFromJsonElement(ListSerializer(PlayerQueue.serializer()), result)
        assertEquals(result.size, queues.size)
        assertTrue(queues.any { it.currentItem != null })
        println("decoded ${queues.size} queues")
    }

    @Test fun decodesRealAlbumsAndSearch() {
        val albums = sample("13-albums.json")
        val search = sample("14-search.json")
        assumeTrue("no samples", albums != null && search != null)
        val a = MaJson.parseToJsonElement(albums!!.readText()).jsonObject["result"] as JsonArray
        val items = MaJson.decodeFromJsonElement(ListSerializer(MediaItem.serializer()), a)
        assertEquals(a.size, items.size)
        assertTrue(items.all { it.uri.isNotBlank() && it.name.isNotBlank() })
        val s = MaJson.decodeFromJsonElement(
            SearchResults.serializer(), MaJson.parseToJsonElement(search!!.readText()).jsonObject["result"]!!,
        )
        assertTrue(s.albums.isNotEmpty() || s.tracks.isNotEmpty())
        val withArt = (items + s.albums + s.tracks).count { it.image?.proxyId != null }
        println("albums=${items.size} searchAlbums=${s.albums.size} searchTracks=${s.tracks.size} withProxyArt=$withArt")
    }

    @Test fun decodesRealPlayerUpdatedEvents() {
        val f = sample("30-events.jsonl")
        assumeTrue("no samples", f != null)
        val lines = f!!.readLines().filter { it.isNotBlank() }
        assumeTrue("no events captured", lines.isNotEmpty())
        for (line in lines) {
            val obj = MaJson.parseToJsonElement(line).jsonObject
            if (obj["event"]?.toString()?.trim('"') == "player_updated") {
                val p = MaJson.decodeFromJsonElement(Player.serializer(), obj["data"]!!)
                assertNotNull(p.playerId)
                assertEquals(obj["object_id"]?.toString()?.trim('"'), p.playerId)
            }
        }
    }
}
