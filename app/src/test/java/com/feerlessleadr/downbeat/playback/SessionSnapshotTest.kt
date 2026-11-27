package com.feerlessleadr.downbeat.playback

import com.feerlessleadr.downbeat.ma.MediaItemImage
import com.feerlessleadr.downbeat.ma.Player
import com.feerlessleadr.downbeat.ma.PlayerMedia
import com.feerlessleadr.downbeat.ma.PlayerQueue
import com.feerlessleadr.downbeat.ma.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSnapshotTest {
    private val features = listOf("pause", "set_members", "volume_set")

    private fun player(
        id: String, state: String = "playing", title: String? = "Come Together", artist: String? = "The Beatles",
        album: String? = "Abbey Road", duration: Int? = 259, imageUrl: String? = null,
        elapsed: Double? = 10.0, stamp: Double? = 1000.0,
    ) = Player(
        playerId = id, name = id, type = "player", playbackState = state, supportedFeatures = features,
        elapsedTime = elapsed, elapsedTimeLastUpdated = stamp,
        currentMedia = title?.let { PlayerMedia(title = it, artist = artist, album = album, duration = duration, imageUrl = imageUrl) },
    )

    private fun snap(
        players: List<Player>, selected: String? = null, hidden: Set<String> = emptySet(),
        queues: Map<String, PlayerQueue> = emptyMap(), server: String? = "https://music.example.com", now: Double = 1005.0,
    ) = sessionSnapshot(players, selected, hidden, queues, server, now)

    @Test fun aPlayingRoomGivesTitleArtistAlbumDurationAndALivePosition() {
        val s = snap(listOf(player("Deck")))
        assertEquals(SessionState.Playing, s.state)
        assertEquals("Come Together", s.title)
        assertEquals("The Beatles", s.artist)
        assertEquals("Abbey Road", s.album)
        assertEquals(259_000L, s.durationMs)
        assertEquals(15_000L, s.positionMs) // 10 s at the last update plus 5 s since
        assertEquals("Deck", s.roomText)
    }

    @Test fun aPausedRoomKeepsItsPositionStill() {
        val s = snap(listOf(player("Deck", state = "paused")))
        assertEquals(SessionState.Paused, s.state)
        assertEquals(10_000L, s.positionMs)
    }

    @Test fun anIdleRoomOrOneWithoutMediaShowsNothing() {
        assertEquals(SessionState.None, snap(listOf(player("Deck", state = "idle"))).state)
        assertEquals(SessionState.None, snap(listOf(player("Deck", title = null))).state)
        assertEquals(SessionState.None, snap(emptyList()).state)
    }

    @Test fun theCoverComesFromTheServersAddressWithTheTypedHost() {
        val s = snap(listOf(player("Deck", imageUrl = "http://192.0.2.7:8095/imageproxy/abc?size=512&fmt=jpg")))
        assertEquals("https://music.example.com/imageproxy/abc?size=512&fmt=jpg", s.imageUrl)
    }

    @Test fun withoutAPlayerCoverTheQueueItemsPictureIsUsed() {
        val queue = PlayerQueue(queueId = "Deck", currentItem = QueueItem(name = "x", image = MediaItemImage(proxyId = "q1")))
        val s = snap(listOf(player("Deck")), queues = mapOf("Deck" to queue))
        assertEquals("https://music.example.com/imageproxy/q1?size=512&fmt=jpg", s.imageUrl)
        assertNull(snap(listOf(player("Deck")), server = null).imageUrl)
    }

    @Test fun theChosenRoomsAreUsedAndHiddenOnesAreNot() {
        val deck = player("Deck", title = "Deck song")
        val pool = player("Pool", title = "Pool song")
        assertEquals("Pool song", snap(listOf(deck, pool), selected = "Pool").title)
        // The hidden room is skipped, so the other playing room is used instead.
        assertEquals("Pool song", snap(listOf(deck, pool), selected = "Deck", hidden = setOf("Deck")).title)
    }

    @Test fun roomsPlayingTogetherAreNamedTogether() {
        val deck = player("Deck").copy(groupMembers = listOf("Deck", "Pool"))
        val pool = player("Pool", state = "idle").copy(syncedTo = "Deck")
        val s = snap(listOf(deck, pool), selected = "Pool")
        assertEquals("Deck + Pool", s.roomText)
        assertTrue(s.state == SessionState.Playing)
    }
}
