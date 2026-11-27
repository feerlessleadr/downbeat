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

/** One access token of the signed-in user (`auth/tokens`). */
@Serializable
data class AuthToken(
    @SerialName("token_id") val tokenId: String,
    val name: String = "",
    @SerialName("is_long_lived") val isLongLived: Boolean = false,
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
    /** The song as the server sends it (kept raw so an unexpected shape can never break reading the queue). */
    @SerialName("media_item") val mediaItem: kotlinx.serialization.json.JsonObject? = null,
) {
    /** The first artist of the song as (item id, provider), or null. Used to look up the artist picture. */
    fun artistRef(): Pair<String, String>? {
        val artist = (mediaItem?.get("artists") as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonObject ?: return null
        val id = (artist["item_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val provider = (artist["provider"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        return id to provider
    }
}

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

@Serializable
data class MediaItemMetadata(
    val images: List<MediaItemImage> = emptyList(),
    val description: String? = null,
)

/** A compact reference to another item (an artist or album on a track, for example). */
@Serializable
data class ItemMapping(
    @SerialName("item_id") val itemId: String = "",
    val provider: String = "",
    val name: String = "",
    val uri: String = "",
    @SerialName("media_type") val mediaType: String = "",
    val image: MediaItemImage? = null,
    val year: Int? = null,
)

/**
 * One shape for artists, albums, tracks, playlists and radio stations, as returned by library lists and search.
 * Fields that a given type does not have stay at their defaults. [uri] is what play_media takes.
 */
@Serializable
data class MediaItem(
    @SerialName("item_id") val itemId: String = "",
    val provider: String = "",
    val name: String = "",
    val uri: String = "",
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("is_playable") val isPlayable: Boolean = true,
    val favorite: Boolean = false,
    val metadata: MediaItemMetadata = MediaItemMetadata(),
    val year: Int? = null,
    val duration: Int? = null,
    val artists: List<ItemMapping> = emptyList(),
    val album: ItemMapping? = null,
) {
    /** "Artist 1, Artist 2" or empty. */
    val artistNames: String get() = artists.joinToString(", ") { it.name }

    /** Best artwork reference: the item's own images first, then its album's. */
    val image: MediaItemImage? get() = metadata.images.firstOrNull() ?: album?.image
}

@Serializable
data class SearchResults(
    val artists: List<MediaItem> = emptyList(),
    val albums: List<MediaItem> = emptyList(),
    val tracks: List<MediaItem> = emptyList(),
    val playlists: List<MediaItem> = emptyList(),
    val radio: List<MediaItem> = emptyList(),
)

/** Why a provider is not working (`config/providers`, `last_error`). */
@Serializable
data class ProviderError(
    @SerialName("error_code") val errorCode: Int = 0,
    val message: String = "",
)

/**
 * One configured instance of a provider (a music source, a player provider, a metadata provider, a plugin) as listed by
 * `config/providers`. The form values are not read here (see docs/provider-setup-research.md).
 */
@Serializable
data class ProviderConfig(
    @SerialName("instance_id") val instanceId: String,
    val domain: String = "",
    /** music, player, metadata, plugin, core, audio_analysis. */
    val type: String = "",
    val enabled: Boolean = true,
    val name: String? = null,
    @SerialName("default_name") val defaultName: String? = null,
    /** loaded, loading, disabled, auth_required, incompatible, error. */
    val status: String? = null,
    @SerialName("last_error") val lastError: ProviderError? = null,
)

/** A kind of provider that can be added (`providers/manifests`). */
@Serializable
data class ProviderManifest(
    val domain: String,
    val type: String = "",
    val name: String = "",
    val description: String = "",
    val builtin: Boolean = false,
    @SerialName("multi_instance") val multiInstance: Boolean = false,
    @SerialName("allow_disable") val allowDisable: Boolean = true,
    @SerialName("has_setup_flow") val hasSetupFlow: Boolean = false,
)
