package com.feerlessleadr.downbeat.ma

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Models for the parts of the Music Assistant API Downbeat uses. Field names come from the server's own
 * /api-docs/schemas.json and a real authenticated run (docs/music-assistant-api-verified.md).
 * Enums stay plain strings so a new server value never breaks parsing. Every field has a default for the same reason.
 */
val MaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

@Serializable
data class ServerInfo(
    @SerialName("server_id") val serverId: String = "",
    @SerialName("server_version") val serverVersion: String = "",
    @SerialName("schema_version") val schemaVersion: Int = 0,
    @SerialName("min_supported_schema_version") val minSupportedSchemaVersion: Int = 0,
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("onboard_done") val onboardDone: Boolean = true,
)

@Serializable
data class MediaItemImage(
    val type: String = "",
    val path: String = "",
    val provider: String = "",
    @SerialName("remotely_accessible") val remotelyAccessible: Boolean = false,
    @SerialName("proxy_id") val proxyId: String? = null,
)

@Serializable
data class PlayerMedia(
    val uri: String = "",
    @SerialName("media_type") val mediaType: String = "",
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val duration: Int? = null,
    @SerialName("queue_item_id") val queueItemId: String? = null,
)

@Serializable
data class Player(
    @SerialName("player_id") val playerId: String,
    val provider: String = "",
    /** player, stereo_pair, group, protocol, display, visualizer, light, source, unknown. */
    val type: String = "player",
    val name: String = "",
    val available: Boolean = true,
    @SerialName("supported_features") val supportedFeatures: List<String> = emptyList(),
    /** idle, paused, playing, unknown. Can be idle while [currentMedia] still shows the last item. */
    @SerialName("playback_state") val playbackState: String = "idle",
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    @SerialName("elapsed_time_last_updated") val elapsedTimeLastUpdated: Double? = null,
    val powered: Boolean? = null,
    @SerialName("volume_level") val volumeLevel: Int? = null,
    @SerialName("volume_muted") val volumeMuted: Boolean? = null,
    @SerialName("group_members") val groupMembers: List<String> = emptyList(),
    @SerialName("static_group_members") val staticGroupMembers: List<String> = emptyList(),
    @SerialName("can_group_with") val canGroupWith: List<String> = emptyList(),
    @SerialName("synced_to") val syncedTo: String? = null,
    @SerialName("active_group") val activeGroup: String? = null,
    @SerialName("active_source") val activeSource: String? = null,
    @SerialName("current_media") val currentMedia: PlayerMedia? = null,
    val enabled: Boolean = true,
    @SerialName("hide_in_ui") val hideInUi: Boolean = false,
    val private: Boolean = false,
    @SerialName("group_volume") val groupVolume: Int? = null,
    val icon: String? = null,
) {
    fun supports(feature: String): Boolean = feature in supportedFeatures
}

@Serializable
data class QueueItem(
    @SerialName("queue_id") val queueId: String = "",
    @SerialName("queue_item_id") val queueItemId: String = "",
    /** The server builds this as "Artist - Title". */
    val name: String = "",
    val duration: Int? = null,
    val index: Int = 0,
    val available: Boolean = true,
    val image: MediaItemImage? = null,
)

@Serializable
data class PlayerQueue(
    /** Equals the player id of the player that owns the queue. */
    @SerialName("queue_id") val queueId: String,
    @SerialName("display_name") val displayName: String = "",
    val available: Boolean = true,
    /** Number of items in the queue (not the items themselves; fetch those with player_queues/items). */
    val items: Int = 0,
    @SerialName("shuffle_enabled") val shuffleEnabled: Boolean = false,
    /** off, one, all. */
    @SerialName("repeat_mode") val repeatMode: String = "off",
    val state: String = "idle",
    @SerialName("current_index") val currentIndex: Int? = null,
    @SerialName("current_item") val currentItem: QueueItem? = null,
    @SerialName("next_item") val nextItem: QueueItem? = null,
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    @SerialName("elapsed_time_last_updated") val elapsedTimeLastUpdated: Double? = null,
)
