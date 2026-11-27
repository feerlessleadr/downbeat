package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.Player

/** One line in the rooms sheet. [ticked] means the room is part of what is playing now. */
data class RoomRow(
    val player: Player,
    val ticked: Boolean,
    /** False when tapping would do nothing (unavailable, the only ticked room, or locked into a static group). */
    val enabled: Boolean,
    val subtitle: String,
)

/** What a tap on a room does. The caller turns it into server commands. */
sealed interface RoomAction {
    /** Make [room] play together with the set led by [leader] (dynamic sync). */
    data class AddToSet(val leader: String, val room: String) : RoomAction
    data class RemoveFromSet(val leader: String, val room: String) : RoomAction
    /** Stop treating the current set as the target and use [room] alone (moving the music there if it is playing). */
    data class SwitchTo(val room: String) : RoomAction
    data object None : RoomAction
}

/**
 * The "rooms" idea from docs/design.md in code: a person picks where music plays; the app works out the leader,
 * the queue owner and the group commands. Pure functions, no I/O, so every rule here has a unit test.
 *
 * Facts used (verified on a real server, docs/music-assistant-api-verified.md): `synced_to` is the leader when a player
 * is a follower; `active_group` is the group player a member currently belongs to; a sync leader's `group_members`
 * includes itself; static groups cannot be edited (`can_group_with` is empty and `static_group_members` is set).
 */
object RoomLogic {

    /**
     * Which room to use when nothing was chosen: one that is playing, else the first available single room (a
     * static group is a poor surprise default), else any available room.
     */
    fun defaultSelection(rooms: List<Player>): String? =
        rooms.firstOrNull { it.available && it.playbackState == "playing" }?.playerId
            // A real speaker (it can pause) beats a TV or receiver that only has volume controls.
            ?: rooms.firstOrNull { it.available && it.type != "group" && it.supports("pause") }?.playerId
            ?: rooms.firstOrNull { it.available && it.type != "group" }?.playerId
            ?: rooms.firstOrNull { it.available }?.playerId
            ?: rooms.firstOrNull()?.playerId

    /**
     * The player that owns playback for the chosen room. A follower resolves to its leader and a member of a
     * playing static group resolves to that group, because their own queue is not the one that plays.
     * Falls back to the default room when the chosen one is missing, hidden or unavailable.
     */
    fun effectiveLeader(players: List<Player>, selectedId: String?): Player? {
        val rooms = visibleRooms(players)
        val byId = players.associateBy { it.playerId }
        val chosen = selectedId?.let { byId[it] }?.takeIf { it.available && it in rooms }
            ?: defaultSelection(rooms)?.let { byId[it] }
            ?: return null
        val targetId = chosen.activeGroup ?: chosen.syncedTo ?: chosen.playerId
        return byId[targetId] ?: chosen
    }

    /** Ids of every room that is playing together with [leader], leader included. */
    fun currentSet(leader: Player): Set<String> = leader.groupMembers.toSet() + leader.playerId

    fun rows(players: List<Player>, leader: Player): List<RoomRow> {
        val set = currentSet(leader)
        val lockedByGroup = leader.type == "group"
        return visibleRooms(players).map { room ->
            val ticked = room.playerId in set
            val enabled = room.available && !(ticked && (lockedByGroup || set.size == 1))
            val subtitle = when {
                ticked && room.playerId == leader.playerId -> activity(room)
                ticked && lockedByGroup -> "In ${leader.name}"
                ticked -> "Together with ${leader.name}"
                else -> activity(room)
            }
            RoomRow(room, ticked, enabled, subtitle)
        }
    }

    fun actionFor(players: List<Player>, leader: Player, tappedId: String): RoomAction {
        val tapped = players.firstOrNull { it.playerId == tappedId } ?: return RoomAction.None
        if (!tapped.available) return RoomAction.None
        val set = currentSet(leader)
        if (tappedId in set) {
            // The only ticked room cannot be unticked; a static group's members cannot be edited; removing the leader
            // of a sync set is not offered (untick the others instead).
            if (tappedId == leader.playerId || leader.type == "group") return RoomAction.None
            return RoomAction.RemoveFromSet(leader.playerId, tappedId)
        }
        val canJoin = leader.type != "group" && tapped.type != "group" &&
            leader.supports("set_members") && tapped.supports("set_members") &&
            tappedId in leader.canGroupWith
        return if (canJoin) RoomAction.AddToSet(leader.playerId, tappedId) else RoomAction.SwitchTo(tappedId)
    }

    private fun activity(room: Player): String {
        val base = statusLabel(room)
        val title = room.currentMedia?.title
        return if (room.available && room.playbackState == "playing" && !title.isNullOrBlank()) "$base · $title" else base
    }
}
