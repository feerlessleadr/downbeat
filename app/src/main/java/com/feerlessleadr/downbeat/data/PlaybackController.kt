package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaApi
import com.feerlessleadr.downbeat.ma.MaException
import com.feerlessleadr.downbeat.ma.MediaItem
import com.feerlessleadr.downbeat.ma.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Turns button presses into server commands. Every function returns null on success or a short sentence to show the
 * person. Playback-changing commands here have not yet been sent to the real server (see STATUS.md).
 */
class PlaybackController(private val repo: MusicRepository) {

    suspend fun playPause(leader: Player): String? = run { it.playPause(leader.playerId) }
    /** Pause and resume that do nothing if the room is already in that state (used by the lock screen controls). */
    suspend fun pause(leader: Player): String? = run { it.pause(leader.playerId) }
    suspend fun resume(leader: Player): String? = run { it.resume(leader.playerId) }
    suspend fun stop(leader: Player): String? = run { it.stop(leader.playerId) }.also { if (it == null) repo.refreshQueueNow(leader.playerId) }
    suspend fun next(leader: Player): String? = run { it.next(leader.playerId) }
    suspend fun previous(leader: Player): String? = run { it.previous(leader.playerId) }
    /** The player takes a moment to restart its stream, so the queue is re-read twice to pick up the new position. */
    suspend fun seek(leader: Player, seconds: Int): String? {
        val error = run { it.seek(leader.playerId, seconds) }
        if (error == null) {
            delay(1_000)
            repo.refreshQueueNow(leader.playerId)
            delay(2_000)
            repo.refreshQueueNow(leader.playerId)
        }
        return error
    }

    // Queue events were not seen from the server, so re-read the queue after a change to show the new state.
    suspend fun toggleShuffle(leader: Player, current: Boolean): String? =
        run { it.shuffle(leader.playerId, !current) }.also { if (it == null) repo.refreshQueueNow(leader.playerId) }

    /** off, then all, then one, then off again. */
    suspend fun cycleRepeat(leader: Player, current: String): String? =
        run { it.repeat(leader.playerId, nextRepeat(current)) }.also { if (it == null) repo.refreshQueueNow(leader.playerId) }

    /** Volume of the whole current set (group volume when several rooms play together). */
    suspend fun setSetVolume(leader: Player, level: Int): String? = run {
        if (RoomLogic.currentSet(leader).size > 1) it.setGroupVolume(leader.playerId, level)
        else it.setVolume(leader.playerId, level)
    }

    /** Switches one room (or a static group) on or off. Playing in an off room is expected to power it on (unverified). */
    suspend fun setRoomPower(roomId: String, on: Boolean): String? = run { it.setPower(roomId, on) }

    /** "Turn everything off": switches off every visible room that can power and is on. Keeps going after a failure. */
    suspend fun powerOffAll(): String? {
        var firstError: String? = null
        for (id in RoomLogic.powerOffTargets(repo.state.value.players, repo.hiddenRooms.value)) {
            setRoomPower(id, false)?.let { if (firstError == null) firstError = it }
        }
        return firstError
    }

    /**
     * Plays [item] in the rooms led by [leader]. With [startItem] the whole [item] (an album or playlist) is queued and
     * playback starts at that track. Playing into a room that is switched off is expected to switch it on (unverified).
     */
    suspend fun playItem(
        leader: Player,
        item: MediaItem,
        mode: PlayMode = PlayMode.Replace,
        startItem: MediaItem? = null,
        shuffle: Boolean? = null,
    ): String? {
        if (item.uri.isBlank()) return "That item can't be played."
        val error = run {
            it.playMedia(leader.playerId, listOf(item.uri), mode.option, startItem = startItem?.uri, shuffle = shuffle)
        }
        if (error == null) repo.refreshQueueNow(leader.playerId)
        return error
    }

    // ---- Queue (docs/roadmap.md M4). The queue id is the leader's player id. ----

    /** Jumps to and plays the item at [index] in the queue. */
    suspend fun playQueueIndex(leader: Player, index: Int): String? =
        run { it.playIndex(leader.playerId, index) }.also { if (it == null) repo.refreshQueueNow(leader.playerId) }

    suspend fun removeFromQueue(leader: Player, queueItemId: String): String? =
        run { it.removeQueueItem(leader.playerId, queueItemId) }.also { if (it == null) repo.refreshQueueNow(leader.playerId) }

    /** Moves an item [shift] places (negative is earlier in the queue). */
    suspend fun moveInQueue(leader: Player, queueItemId: String, shift: Int): String? =
        run { it.moveQueueItem(leader.playerId, queueItemId, shift) }
            .also { if (it == null) repo.refreshQueueNow(leader.playerId) }
            ?.let(::friendlyQueueError)

    /** The server says "0 is already played/buffered" for a song it will not move; say what that means. */
    private fun friendlyQueueError(message: String): String =
        if ("already played" in message || "buffered" in message) {
            "That song can't be moved there: it has played, or it is already loading to play next."
        } else {
            message
        }

    suspend fun clearQueue(leader: Player): String? =
        run { it.clearQueue(leader.playerId) }.also { if (it == null) repo.refreshQueueNow(leader.playerId) }

    suspend fun setRoomVolume(roomId: String, level: Int): String? = run { it.setVolume(roomId, level) }

    /** What the rooms sheet does when a row is tapped. */
    suspend fun tapRoom(leader: Player, tappedId: String): String? {
        val players = repo.state.value.players
        return when (val action = RoomLogic.actionFor(players, leader, tappedId)) {
            RoomAction.None -> null
            is RoomAction.AddToSet -> run { it.setMembers(action.leader, add = listOf(action.room)) }
            is RoomAction.RemoveFromSet -> run { it.setMembers(action.leader, remove = listOf(action.room)) }
            is RoomAction.SwitchTo -> {
                // Move the music along only if something is playing; otherwise just point the app at the new room.
                val error = if (leader.playbackState == "playing") {
                    run { it.transferQueue(leader.playerId, action.room) }
                } else {
                    null
                }
                if (error == null) repo.selectRoom(action.room)
                error
            }
        }
    }

    private suspend fun run(block: suspend (MaApi) -> Unit): String? {
        val api = repo.api ?: return "Not connected to the server."
        return try {
            block(api)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaException) {
            e.message ?: "The server refused that."
        } catch (e: Exception) {
            "Something went wrong. Try again."
        }
    }

    companion object {
        fun nextRepeat(current: String): String = when (current) {
            "off" -> "all"
            "all" -> "one"
            else -> "off"
        }
    }
}
