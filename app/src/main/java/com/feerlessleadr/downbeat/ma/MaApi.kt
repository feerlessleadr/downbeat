package com.feerlessleadr.downbeat.ma

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The commands Downbeat uses, with the argument names checked against the server's own /api-docs/commands.json
 * (server 2.10.13). Reads give null or an empty list when the server answers with a null result (an unknown id).
 *
 * Commands that change playback or grouping are untested against the real server (all sample runs were read-only).
 */
class MaApi(private val client: MaClient) {

    // ---- Players and queues ------------------------------------------------------------------------------

    suspend fun players(includeUnavailable: Boolean = true): List<Player> =
        list(Player.serializer(), "players/all", args { put("return_unavailable", includeUnavailable) })

    suspend fun queues(): List<PlayerQueue> = list(PlayerQueue.serializer(), "player_queues/all")

    suspend fun queue(queueId: String): PlayerQueue? =
        one(PlayerQueue.serializer(), "player_queues/get", args { put("queue_id", queueId) })

    /** The queue that actually plays for this player (a follower in a group resolves to its leader's queue). */
    suspend fun activeQueue(playerId: String): PlayerQueue? =
        one(PlayerQueue.serializer(), "player_queues/get_active_queue", args { put("player_id", playerId) })

    suspend fun queueItems(queueId: String, limit: Int = 100, offset: Int = 0): List<QueueItem> =
        list(
            QueueItem.serializer(), "player_queues/items",
            args { put("queue_id", queueId); put("limit", limit); put("offset", offset) },
        )

    // ---- Transport (on a queue id; a queue id equals the id of the player that owns it) ------------------------

    suspend fun playPause(queueId: String) = run("player_queues/play_pause", args { put("queue_id", queueId) })
    suspend fun next(queueId: String) = run("player_queues/next", args { put("queue_id", queueId) })
    suspend fun previous(queueId: String) = run("player_queues/previous", args { put("queue_id", queueId) })

    suspend fun seek(queueId: String, positionSeconds: Int) =
        run("player_queues/seek", args { put("queue_id", queueId); put("position", positionSeconds) })

    suspend fun shuffle(queueId: String, enabled: Boolean) =
        run("player_queues/shuffle", args { put("queue_id", queueId); put("shuffle_enabled", enabled) })

    /** [mode] is off, one or all. */
    suspend fun repeat(queueId: String, mode: String) =
        run("player_queues/repeat", args { put("queue_id", queueId); put("repeat_mode", mode) })

    suspend fun playIndex(queueId: String, index: Int) =
        run("player_queues/play_index", args { put("queue_id", queueId); put("index", index) })

    suspend fun removeQueueItem(queueId: String, queueItemId: String) =
        run("player_queues/delete_item", args { put("queue_id", queueId); put("item_id_or_index", queueItemId) })

    suspend fun clearQueue(queueId: String) = run("player_queues/clear", args { put("queue_id", queueId) })

    /** Moves [queueItemId] by [shift] positions (negative is up). */
    suspend fun moveQueueItem(queueId: String, queueItemId: String, shift: Int) =
        run(
            "player_queues/move_item",
            args { put("queue_id", queueId); put("queue_item_id", queueItemId); put("pos_shift", shift) },
        )

    // ---- Play something ------------------------------------------------------------------------------------

    /** [option]: play, replace, next, replace_next or add. [uris] come from MediaItem.uri. */
    suspend fun playMedia(
        queueId: String,
        uris: List<String>,
        option: String = "replace",
        radioMode: Boolean = false,
    ) = run(
        "player_queues/play_media",
        args {
            put("queue_id", queueId)
            if (uris.size == 1) put("media", uris.first()) else put("media", JsonArray(uris.map { JsonPrimitive(it) }))
            put("option", option)
            put("radio_mode", radioMode)
        },
    )

    // ---- Volume, power, grouping ---------------------------------------------------------------------------

    suspend fun setVolume(playerId: String, level: Int) =
        run("players/cmd/volume_set", args { put("player_id", playerId); put("volume_level", level.coerceIn(0, 100)) })

    suspend fun setGroupVolume(playerId: String, level: Int) =
        run("players/cmd/group_volume", args { put("player_id", playerId); put("volume_level", level.coerceIn(0, 100)) })

    suspend fun setPower(playerId: String, on: Boolean) =
        run("players/cmd/power", args { put("player_id", playerId); put("powered", on) })

    /** Adds and removes members of the group led by [target]. The server powers added players on. */
    suspend fun setMembers(target: String, add: List<String> = emptyList(), remove: List<String> = emptyList()) =
        run(
            "players/cmd/set_members",
            args {
                put("target_player", target)
                if (add.isNotEmpty()) put("player_ids_to_add", JsonArray(add.map { JsonPrimitive(it) }))
                if (remove.isNotEmpty()) put("player_ids_to_remove", JsonArray(remove.map { JsonPrimitive(it) }))
            },
        )

    /** Moves the queue (and playback) from one player to another. */
    suspend fun transferQueue(from: String, to: String, autoPlay: Boolean = true) =
        run(
            "player_queues/transfer",
            args { put("source_queue_id", from); put("target_queue_id", to); put("auto_play", autoPlay) },
        )

    // ---- Library and search --------------------------------------------------------------------------------

    suspend fun search(query: String, limit: Int = 10, libraryOnly: Boolean = false): SearchResults {
        val result = client.call(
            "music/search",
            args { put("search_query", query); put("limit", limit); put("library_only", libraryOnly) },
        )
        return if (result is JsonNull) SearchResults() else MaJson.decodeFromJsonElement(SearchResults.serializer(), result)
    }

    /** [type] is artists, albums, tracks, playlists or radios. */
    suspend fun library(
        type: String,
        limit: Int = 50,
        offset: Int = 0,
        search: String? = null,
        favoriteOnly: Boolean = false,
        orderBy: String? = null,
    ): List<MediaItem> {
        require(type in LIBRARY_TYPES) { "Unknown library type" }
        return list(
            MediaItem.serializer(), "music/$type/library_items",
            args {
                put("limit", limit)
                put("offset", offset)
                if (search != null) put("search", search)
                if (favoriteOnly) put("favorite", true)
                if (orderBy != null) put("order_by", orderBy)
            },
        )
    }

    suspend fun albumTracks(itemId: String, providerInstanceOrDomain: String): List<MediaItem> =
        list(
            MediaItem.serializer(), "music/albums/album_tracks",
            args { put("item_id", itemId); put("provider_instance_id_or_domain", providerInstanceOrDomain) },
        )

    suspend fun playlistTracks(itemId: String, providerInstanceOrDomain: String): List<MediaItem> =
        list(
            MediaItem.serializer(), "music/playlists/playlist_tracks",
            args { put("item_id", itemId); put("provider_instance_id_or_domain", providerInstanceOrDomain) },
        )

    suspend fun artistAlbums(itemId: String, providerInstanceOrDomain: String): List<MediaItem> =
        list(
            MediaItem.serializer(), "music/artists/artist_albums",
            args { put("item_id", itemId); put("provider_instance_id_or_domain", providerInstanceOrDomain) },
        )

    // ---- Plumbing ------------------------------------------------------------------------------------------

    private suspend fun run(command: String, args: JsonObject? = null) {
        client.call(command, args)
    }

    private suspend fun <T> list(serializer: KSerializer<T>, command: String, args: JsonObject? = null): List<T> {
        val result = client.call(command, args)
        if (result is JsonNull) return emptyList()
        return MaJson.decodeFromJsonElement(ListSerializer(serializer), result)
    }

    private suspend fun <T : Any> one(serializer: KSerializer<T>, command: String, args: JsonObject): T? {
        val result: JsonElement = client.call(command, args)
        return if (result is JsonNull) null else MaJson.decodeFromJsonElement(serializer, result)
    }

    private fun args(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    companion object {
        val LIBRARY_TYPES = setOf("artists", "albums", "tracks", "playlists", "radios")
    }
}
