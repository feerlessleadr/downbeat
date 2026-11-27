package com.feerlessleadr.downbeat.data

/**
 * The bottom bar is whatever the person picks from these destinations (up to [MAX_ITEMS]), in the order they pick, plus a
 * fixed Settings button at the end that cannot be removed (so five buttons in all).
 * Ids are stored in the preferences, so never rename one.
 */
object NavConfig {
    const val MAX_ITEMS = 4

    /** Every destination the bar can hold, in the order the customize screen lists the unused ones. */
    val ALL = listOf("library", "playlists", "albums", "artists", "radios", "tracks", "search", "rooms", "playing")

    /** What a new install shows: browse, the playlists shortcut, search, and the rooms page. */
    val DEFAULT = listOf("library", "playlists", "search", "rooms")

    /** Drops unknown and repeated ids, keeps at most [MAX_ITEMS], and falls back to [DEFAULT] if nothing is left. */
    fun sanitize(ids: List<String>): List<String> =
        ids.filter { it in ALL }.distinct().take(MAX_ITEMS).ifEmpty { DEFAULT }

    fun encode(ids: List<String>): String = ids.joinToString(",")

    fun decode(text: String?): List<String> =
        if (text.isNullOrBlank()) DEFAULT else sanitize(text.split(",").map { it.trim() })
}
