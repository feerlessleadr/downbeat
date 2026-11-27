package com.feerlessleadr.downbeat.playback

import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.nowPlaying
import com.feerlessleadr.downbeat.ma.ImageUrls
import com.feerlessleadr.downbeat.ma.Player
import com.feerlessleadr.downbeat.ma.PlayerQueue
import com.feerlessleadr.downbeat.ui.roomLabel

enum class SessionState { None, Playing, Paused }

/**
 * What the lock screen and the notification show for the chosen rooms, worked out from the server's state. Pure, so it
 * can be tested without Android. [None] means nothing is playing or paused (idle).
 */
data class SessionSnapshot(
    val state: SessionState,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    /** Address of the cover on the user's server, or null. */
    val imageUrl: String?,
    /** The rooms, for example "Deck + Pool" or a group's name. */
    val roomText: String,
) {
    companion object {
        val NONE = SessionSnapshot(SessionState.None, "", "", "", 0L, 0L, null, "")
    }
}

fun sessionSnapshot(
    players: List<Player>,
    selectedRoomId: String?,
    hidden: Set<String>,
    queues: Map<String, PlayerQueue>,
    serverUrl: String?,
    nowEpochSeconds: Double,
): SessionSnapshot {
    val leader = RoomLogic.effectiveLeader(players, selectedRoomId, hidden) ?: return SessionSnapshot.NONE
    val state = when (leader.playbackState) {
        "playing" -> SessionState.Playing
        "paused" -> SessionState.Paused
        else -> return SessionSnapshot.NONE
    }
    val queue = queues[leader.playerId]
    val np = nowPlaying(leader, queue, nowEpochSeconds)
    if (!np.hasMedia) return SessionSnapshot.NONE
    val media = leader.currentMedia
    val image = serverUrl?.let { ImageUrls.rewriteHost(it, np.imageUrl) ?: ImageUrls.forImage(it, np.imageRef, 512) }
    return SessionSnapshot(
        state = state,
        title = np.title,
        artist = media?.artist?.takeIf { it.isNotBlank() }.orEmpty(),
        album = media?.album?.takeIf { it.isNotBlank() }.orEmpty(),
        durationMs = (np.durationSeconds ?: 0) * 1000L,
        positionMs = (np.positionSeconds * 1000).toLong().coerceAtLeast(0L),
        imageUrl = image,
        roomText = roomLabel(players, leader),
    )
}
