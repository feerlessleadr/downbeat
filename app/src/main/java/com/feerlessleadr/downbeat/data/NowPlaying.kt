package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MediaItemImage
import com.feerlessleadr.downbeat.ma.Player
import com.feerlessleadr.downbeat.ma.PlayerQueue

/** What the Playing screen shows for the current leader. */
data class NowPlaying(
    val hasMedia: Boolean,
    val title: String,
    /** "Artist · Album", either part may be missing. */
    val subtitle: String,
    /** Server-provided artwork URL (still carries the server's own host; rewrite it before loading). */
    val imageUrl: String?,
    /** Fallback artwork reference from the queue item. */
    val imageRef: MediaItemImage?,
    val durationSeconds: Int?,
    val positionSeconds: Double,
    val playing: Boolean,
)

/** Buttons the current leader can honestly offer (from its `supported_features`, verified to differ a lot). */
data class Controls(
    val playPause: Boolean,
    val nextPrevious: Boolean,
    val seek: Boolean,
    val volume: Boolean,
)

/**
 * Next, previous and seek are queue commands that Music Assistant runs itself, so they are always offered (the web
 * UI shows them for players that do not list those features; confirmed on the owner's phone 2026-11-27).
 */
fun controlsFor(leader: Player) = Controls(
    playPause = leader.supports("pause"),
    nextPrevious = true,
    seek = true,
    volume = leader.supports("volume_set"),
)

/**
 * [nowEpochSeconds] is the current time in seconds since 1970. Position is the server's last known elapsed time plus
 * the time since that was recorded while playing, so the bar moves without waiting for server ticks (the player
 * reports `elapsed_time` and `elapsed_time_last_updated` in epoch seconds: verified).
 */
fun nowPlaying(leader: Player, queue: PlayerQueue?, nowEpochSeconds: Double): NowPlaying {
    val media = leader.currentMedia
    val item = queue?.currentItem
    val title = media?.title?.takeIf { it.isNotBlank() } ?: item?.name.orEmpty()
    val subtitle = listOfNotNull(media?.artist?.takeIf { it.isNotBlank() }, media?.album?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    val duration = media?.duration ?: item?.duration
    val playing = leader.playbackState == "playing"
    // The queue is the reference (the web UI uses it). A player's own clock can restart from zero after a seek.
    // Right after a skip the player reports the new song a moment before the queue does; a queue that still describes the
    // old song is ignored, otherwise the bar would show the old position and then jump back to the start.
    val queueMatches = item?.queueItemId.isNullOrEmpty() || media?.queueItemId.isNullOrEmpty() || item?.queueItemId == media?.queueItemId
    val useQueue = queue?.elapsedTime != null && queueMatches
    val elapsed = (if (useQueue) queue?.elapsedTime else leader.elapsedTime) ?: 0.0
    val stamp = if (useQueue) queue?.elapsedTimeLastUpdated else leader.elapsedTime?.let { leader.elapsedTimeLastUpdated }
    val live = if (playing && stamp != null) elapsed + (nowEpochSeconds - stamp).coerceAtLeast(0.0) else elapsed
    val position = if (duration != null && duration > 0) live.coerceIn(0.0, duration.toDouble()) else live.coerceAtLeast(0.0)
    return NowPlaying(
        hasMedia = title.isNotBlank(),
        title = title,
        subtitle = subtitle,
        imageUrl = media?.imageUrl,
        imageRef = item?.image,
        durationSeconds = duration,
        positionSeconds = position,
        playing = playing,
    )
}
