package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.Player

private val ROOM_TYPES = setOf("player", "stereo_pair", "group")

/**
 * The players a person should ever see as rooms: real speakers, stereo pairs and static groups. Protocol helpers,
 * displays, lights and the like are dropped, and so are players the server marks hidden, private or disabled.
 * (Verified shape: the owner's server has a hidden private web player that must never show up.)
 * Available rooms come first, each half sorted by name.
 */
fun visibleRooms(players: List<Player>, hiddenByUser: Set<String> = emptySet()): List<Player> =
    players
        .filter { it.type in ROOM_TYPES && !it.hideInUi && !it.private && it.enabled && it.playerId !in hiddenByUser }
        .sortedWith(compareBy({ !it.available }, { it.name.lowercase() }))

/**
 * Short status text. "Playing" only when the player really is playing: a player can be idle while it still
 * reports the last item it played (verified on the owner's server).
 */
fun statusLabel(player: Player): String = when {
    !player.available -> "Unavailable"
    player.playbackState == "playing" -> "Playing"
    player.playbackState == "paused" -> "Paused"
    else -> "Idle"
}
