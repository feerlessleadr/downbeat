package com.feerlessleadr.downbeat.ui

import com.feerlessleadr.downbeat.ma.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class UiHelpersTest {
    private fun p(id: String, type: String = "player", members: List<String> = emptyList()) =
        Player(playerId = id, name = id, type = type, groupMembers = members)

    @Test fun aSingleRoomShowsItsName() {
        val k = p("Kitchen")
        assertEquals("Kitchen", roomLabel(listOf(k), k))
    }

    @Test fun twoRoomsShowBothNamesLeaderFirst() {
        val deck = p("Deck", members = listOf("Pool", "Deck"))
        assertEquals("Deck + Pool", roomLabel(listOf(deck, p("Pool")), deck))
    }

    @Test fun manyRoomsAreSummarised() {
        val deck = p("Deck", members = listOf("Deck", "Pool", "Kitchen", "Patio"))
        val all = listOf(deck, p("Pool"), p("Kitchen"), p("Patio"))
        assertEquals("Deck + 3 more", roomLabel(all, deck))
    }

    @Test fun aStaticGroupShowsItsOwnName() {
        val g = p("Backyard", type = "group", members = listOf("Deck", "Pool"))
        assertEquals("Backyard", roomLabel(listOf(g, p("Deck"), p("Pool")), g))
    }

    @Test fun timeIsFormattedAsMinutesAndSeconds() {
        assertEquals("0:00", formatTime(0.0))
        assertEquals("1:05", formatTime(65.9))
        assertEquals("12:00", formatTime(720.0))
        assertEquals("0:00", formatTime(-3.0))
    }
}
