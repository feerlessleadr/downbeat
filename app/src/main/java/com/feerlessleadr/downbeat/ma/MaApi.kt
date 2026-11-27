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

    // ---- Account -----------------------------------------------------------------------------------------

    /** Makes a long-lived token (the server's token screen says 10 years, not renewed by use). The result is the token. */
    suspend fun createLongLivedToken(name: String): String {
        val result = client.call("auth/token/create", args { put("name", name) })
        return (result as? JsonPrimitive)?.content ?: throw MaException(null, "No token in the server's answer")
    }

    /** The signed-in user's tokens (arguments checked against commands.json 2.10.13). */
    suspend fun tokens(): List<AuthToken> = list(AuthToken.serializer(), "auth/tokens")

    suspend fun revokeToken(tokenId: String) = run("auth/token/revoke", args { put("token_id", tokenId) })

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

    /** Explicit pause and resume. Unlike [playPause] they do nothing when already in that state, so a repeat is harmless. */
    suspend fun pause(queueId: String) = run("player_queues/pause", args { put("queue_id", queueId) })

    suspend fun resume(queueId: String) = run("player_queues/play", args { put("queue_id", queueId) })
    /** Stops playback and leaves the room idle (the queue keeps its items). */
    suspend fun stop(queueId: String) = run("player_queues/stop", args { put("queue_id", queueId) })

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
        /** URI of the item to start with inside [uris] (for example one track of an album). */
        startItem: String? = null,
        shuffle: Boolean? = null,
    ) = run(
        "player_queues/play_media",
        args {
            put("queue_id", queueId)
            if (uris.size == 1) put("media", uris.first()) else put("media", JsonArray(uris.map { JsonPrimitive(it) }))
            put("option", option)
            put("radio_mode", radioMode)
            if (startItem != null) put("start_item", startItem)
            if (shuffle != null) put("shuffle", shuffle)
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

    /** One artist with its metadata (pictures). */
    suspend fun artist(itemId: String, providerInstanceOrDomain: String): MediaItem? =
        one(
            MediaItem.serializer(), "music/artists/get",
            args { put("item_id", itemId); put("provider_instance_id_or_domain", providerInstanceOrDomain) },
        )

    /** Every configured provider instance (needs an administrator account). The form values are left out. */
    suspend fun providerConfigs(): List<ProviderConfig> =
        list(ProviderConfig.serializer(), "config/providers", args { put("include_values", false) })

    /** The settings form of one provider instance. Secret values may come back masked or empty (unverified). */
    suspend fun providerEntries(instanceId: String): List<ConfigEntry> =
        list(ConfigEntry.serializer(), "config/providers/get_entries", args { put("instance_id", instanceId) })

    /** Saves changed settings of an existing instance. Only the keys in [values] are sent. */
    suspend fun saveProviderValues(domain: String, instanceId: String, values: Map<String, JsonElement?>) = run(
        "config/providers/save",
        args {
            put("provider_domain", domain)
            put("instance_id", instanceId)
            put("values", buildJsonObject { values.forEach { (k, v) -> put(k, v ?: JsonNull) } })
        },
    )

    /** Runs a button of the settings form. The answer is a message or link (object), a list of fields, or nothing. */
    suspend fun invokeProviderAction(instanceId: String, action: String): JsonElement =
        client.call("config/providers/invoke_action", args { put("instance_id", instanceId); put("action", action) })

    /**
     * Starts adding a provider. For a provider that needs no input the instance is created at once and a finish step comes
     * back (the server's own description).
     */
    suspend fun startProviderSetup(domain: String): SetupFlowStep =
        one(SetupFlowStep.serializer(), "config/providers/setup", args { put("provider_domain", domain) })
            ?: throw MaException(null, "The server did not start the setup.")

    /** Sends the values of a form step and returns the next step. */
    suspend fun submitFlow(flowId: String, values: Map<String, JsonElement?>): SetupFlowStep =
        one(
            SetupFlowStep.serializer(), "config/flows/submit",
            args {
                put("flow_id", flowId)
                put("values", buildJsonObject { values.forEach { (k, v) -> put(k, v ?: JsonNull) } })
            },
        ) ?: throw MaException(null, "The server did not answer the setup step.")

    /** Reads the current step again (used to wait on an external or progress step). */
    suspend fun getFlow(flowId: String): SetupFlowStep =
        one(SetupFlowStep.serializer(), "config/flows/get", args { put("flow_id", flowId) })
            ?: throw MaException(null, "The setup is no longer running.")

    suspend fun abortFlow(flowId: String) = run("config/flows/abort", args { put("flow_id", flowId) })

    // ---- Server admin (docs/server-admin-research.md). All need an administrator account. ----

    suspend fun aboutServer(): ServerAbout =
        one(ServerAbout.serializer(), "info", args { }) ?: throw MaException(null, "The server gave no information.")

    suspend fun adminUsers(): List<AdminUser> = list(AdminUser.serializer(), "auth/users")

    suspend fun backgroundTasks(): List<BackgroundTask> = list(BackgroundTask.serializer(), "tasks/list")

    /** The application log as text (can be large). */
    suspend fun serverLog(): String = (client.call("logging/get") as? JsonPrimitive)?.content.orEmpty()

    /** Restarts one provider instance. */
    suspend fun reloadProvider(instanceId: String) = run("config/providers/reload", args { put("instance_id", instanceId) })

    /** Removes a provider instance (and with it what it contributed to the library). Cannot be undone. */
    suspend fun removeProvider(instanceId: String) = run("config/providers/remove", args { put("instance_id", instanceId) })

    /**
     * Turns an existing instance on or off through `config/providers/save` with `enabled` among the values. Whether the
     * server honours this key is not yet verified (docs/provider-setup-research.md), so callers read the list back.
     */
    suspend fun setProviderEnabled(domain: String, instanceId: String, enabled: Boolean) = run(
        "config/providers/save",
        args {
            put("provider_domain", domain)
            put("instance_id", instanceId)
            put("values", buildJsonObject { put("enabled", enabled) })
        },
    )

    /** Starts a library sync for one music provider instance. */
    suspend fun syncProvider(instanceId: String) =
        run("music/sync", args { put("providers", JsonArray(listOf(JsonPrimitive(instanceId)))) })

    /** Every kind of provider the server could add. */
    suspend fun providerManifests(): List<ProviderManifest> = list(ProviderManifest.serializer(), "providers/manifests")

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
