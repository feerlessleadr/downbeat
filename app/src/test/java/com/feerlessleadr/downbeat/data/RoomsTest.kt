package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomsTest {
    private fun p(id: String, name: String = id, type: String = "player", available: Boolean = true,
                  hide: Boolean = false, priv: Boolean = false, enabled: Boolean = true, state: String = "idle") =
        Player(playerId = id, name = name, type = type, available = available, hideInUi = hide, private = priv,
            enabled = enabled, playbackState = state)

    @Test fun keepsOnlyRealRoomsAndHidesTheRest() {
        val rooms = visibleRooms(
            listOf(
                p("1", "Kitchen"),
                p("2", "Web player", hide = true, priv = true),
                p("3", "Helper", type = "protocol"),
                p("4", "Disabled", enabled = false),
                p("5", "Backyard", type = "group"),
                p("6", "Pair", type = "stereo_pair"),
                p("7", "Light", type = "light"),
            ),
        )
        assertEquals(listOf("Backyard", "Kitchen", "Pair"), rooms.map { it.name })
    }

    @Test fun availableRoomsComeFirstThenByName() {
        val rooms = visibleRooms(listOf(p("1", "Zed"), p("2", "Alpha", available = false), p("3", "Beta")))
        assertEquals(listOf("Beta", "Zed", "Alpha"), rooms.map { it.name })
    }

    @Test fun statusSaysPlayingOnlyWhenPlaying() {
        assertEquals("Playing", statusLabel(p("1", state = "playing")))
        assertEquals("Paused", statusLabel(p("1", state = "paused")))
        assertEquals("Idle", statusLabel(p("1", state = "idle")))
        assertEquals("Unavailable", statusLabel(p("1", available = false, state = "playing")))
    }
}
