package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MediaItemImage
import com.feerlessleadr.downbeat.ma.Player
import com.feerlessleadr.downbeat.ma.PlayerMedia
import com.feerlessleadr.downbeat.ma.PlayerQueue
import com.feerlessleadr.downbeat.ma.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The topology mirrors the owner's real server: speakers that can group, static groups, TVs, a hidden web player. */
class RoomLogicTest {
    private val grouping = listOf("pause", "set_members", "volume_set", "seek", "next_previous")

    private fun player(
        id: String,
        type: String = "player",
        available: Boolean = true,
        state: String = "idle",
        members: List<String> = emptyList(),
        canGroupWith: List<String> = emptyList(),
        syncedTo: String? = null,
        activeGroup: String? = null,
        features: List<String> = grouping,
        title: String? = null,
        hide: Boolean = false,
        priv: Boolean = false,
    ) = Player(
        playerId = id, name = id, type = type, available = available, playbackState = state,
        groupMembers = members, canGroupWith = canGroupWith, syncedTo = syncedTo, activeGroup = activeGroup,
        supportedFeatures = features, hideInUi = hide, private = priv,
        currentMedia = title?.let { PlayerMedia(title = it) },
    )

    // Deck, Pool and Kitchen can sync with each other. "Backyard" is a static group of Deck and Pool.
    private val deck = player("Deck", canGroupWith = listOf("Pool", "Kitchen"))
    private val pool = player("Pool", canGroupWith = listOf("Deck", "Kitchen"))
    private val kitchen = player("Kitchen", canGroupWith = listOf("Deck", "Pool"))
    private val backyard = player("Backyard", type = "group", members = listOf("Deck", "Pool"), features = listOf("pause", "volume_set"))
    private val tv = player("TV", features = listOf("volume_set", "set_members"))
    private val web = player("Web", hide = true, priv = true)
    private val all get() = listOf(deck, pool, kitchen, backyard, tv, web)

    // ---- default and effective leader ---------------------------------------------------------------------

    @Test fun defaultPrefersAPlayingRoomThenAnAvailableOne() {
        assertEquals("Pool", RoomLogic.defaultSelection(listOf(player("Deck"), player("Pool", state = "playing"))))
        assertEquals("Kitchen", RoomLogic.defaultSelection(listOf(player("Deck", available = false), player("Kitchen"))))
        assertNull(RoomLogic.defaultSelection(emptyList()))
        // A static group is not the surprise default while a single room is available.
        assertEquals("Zed", RoomLogic.defaultSelection(listOf(player("Alpha", type = "group"), player("Zed"))))
        assertEquals("Alpha", RoomLogic.defaultSelection(listOf(player("Alpha", type = "group"))))
        // A real speaker (it can pause) beats a TV that sorts first but only has volume controls.
        assertEquals("Deck", RoomLogic.defaultSelection(listOf(player("AATV", features = listOf("volume_set")), player("Deck"))))
        // With no speaker available, the TV is still better than nothing.
        assertEquals("AATV", RoomLogic.defaultSelection(listOf(player("AATV", features = listOf("volume_set")))))
    }

    @Test fun leaderIsTheChosenRoomWhenAlone() {
        assertEquals("Kitchen", RoomLogic.effectiveLeader(all, "Kitchen")?.playerId)
    }

    @Test fun followerResolvesToItsLeader() {
        val players = listOf(player("Deck", members = listOf("Deck", "Pool")), player("Pool", syncedTo = "Deck"))
        assertEquals("Deck", RoomLogic.effectiveLeader(players, "Pool")?.playerId)
    }

    @Test fun memberOfAPlayingStaticGroupResolvesToTheGroup() {
        val players = listOf(player("Deck", activeGroup = "Backyard"), backyard.copy(playbackState = "playing"))
        // Backyard is a group, Deck belongs to it right now.
        assertEquals("Backyard", RoomLogic.effectiveLeader(players, "Deck")?.playerId)
    }

    @Test fun missingHiddenOrUnavailableChoiceFallsBack() {
        assertEquals("Deck", RoomLogic.effectiveLeader(all, null)?.playerId)
        assertEquals("Deck", RoomLogic.effectiveLeader(all, "gone")?.playerId)
        assertEquals("Deck", RoomLogic.effectiveLeader(all, "Web")?.playerId)
        val withDeckOff = listOf(deck.copy(available = false), kitchen)
        assertEquals("Kitchen", RoomLogic.effectiveLeader(withDeckOff, "Deck")?.playerId)
    }

    @Test fun nothingToSelectGivesNull() {
        assertNull(RoomLogic.effectiveLeader(emptyList(), "x"))
        assertNull(RoomLogic.effectiveLeader(listOf(web), "Web"))
    }

    // ---- rows ---------------------------------------------------------------------------------------------

    @Test fun aSingleRoomIsTickedButCannotBeUnticked() {
        val rows = RoomLogic.rows(all, kitchen).associateBy { it.player.playerId }
        assertTrue(rows.getValue("Kitchen").ticked)
        assertFalse(rows.getValue("Kitchen").enabled)
        assertFalse(rows.getValue("Deck").ticked)
        assertTrue(rows.getValue("Deck").enabled)
        assertFalse("hidden player must not be a row", rows.containsKey("Web"))
    }

    @Test fun aSyncSetTicksEveryMemberAndAllowsUnticking() {
        val leader = deck.copy(groupMembers = listOf("Deck", "Pool"))
        val rows = RoomLogic.rows(all, leader).associateBy { it.player.playerId }
        assertTrue(rows.getValue("Pool").ticked)
        assertTrue(rows.getValue("Pool").enabled)
        assertEquals("Together with Deck", rows.getValue("Pool").subtitle)
        assertFalse(rows.getValue("Kitchen").ticked)
    }

    @Test fun aStaticGroupLocksItsMembers() {
        val rows = RoomLogic.rows(all, backyard).associateBy { it.player.playerId }
        assertTrue(rows.getValue("Deck").ticked)
        assertFalse(rows.getValue("Deck").enabled)
        assertEquals("In Backyard", rows.getValue("Deck").subtitle)
        assertTrue("other rooms stay tappable", rows.getValue("Kitchen").enabled)
    }

    @Test fun anUnavailableRoomIsNotTappable() {
        val rows = RoomLogic.rows(listOf(kitchen, deck.copy(available = false)), kitchen).associateBy { it.player.playerId }
        assertFalse(rows.getValue("Deck").enabled)
        assertEquals("Unavailable", rows.getValue("Deck").subtitle)
    }

    @Test fun anotherRoomPlayingShowsWhat() {
        val rows = RoomLogic.rows(listOf(kitchen, deck.copy(playbackState = "playing", currentMedia = PlayerMedia(title = "Song"))), kitchen)
        assertEquals("Playing · Song", rows.first { it.player.playerId == "Deck" }.subtitle)
    }

    // ---- actions ------------------------------------------------------------------------------------------

    @Test fun tickingARoomThatCanGroupAddsItToTheSet() {
        assertEquals(RoomAction.AddToSet("Kitchen", "Deck"), RoomLogic.actionFor(all, kitchen, "Deck"))
    }

    @Test fun untickingAMemberRemovesIt() {
        val leader = deck.copy(groupMembers = listOf("Deck", "Pool"))
        assertEquals(RoomAction.RemoveFromSet("Deck", "Pool"), RoomLogic.actionFor(all, leader, "Pool"))
    }

    @Test fun theLeaderAndTheOnlyRoomCannotBeUnticked() {
        assertEquals(RoomAction.None, RoomLogic.actionFor(all, kitchen, "Kitchen"))
        val leader = deck.copy(groupMembers = listOf("Deck", "Pool"))
        assertEquals(RoomAction.None, RoomLogic.actionFor(all, leader, "Deck"))
    }

    @Test fun staticGroupMembersCannotBeEdited() {
        assertEquals(RoomAction.None, RoomLogic.actionFor(all, backyard, "Deck"))
    }

    @Test fun aRoomThatCannotJoinSwitchesInstead() {
        // The TV is not in Kitchen's can_group_with, so it cannot be added: switch to it.
        assertEquals(RoomAction.SwitchTo("TV"), RoomLogic.actionFor(all, kitchen, "TV"))
        // A static group is never added into a dynamic set.
        assertEquals(RoomAction.SwitchTo("Backyard"), RoomLogic.actionFor(all, kitchen, "Backyard"))
        // From a static group, any other room means switching.
        assertEquals(RoomAction.SwitchTo("Kitchen"), RoomLogic.actionFor(all, backyard, "Kitchen"))
    }

    @Test fun aRoomWithoutSetMembersFeatureCannotJoin() {
        val noSync = kitchen.copy(supportedFeatures = listOf("pause"))
        assertEquals(RoomAction.SwitchTo("Deck"), RoomLogic.actionFor(all, noSync, "Deck"))
    }

    @Test fun unknownOrUnavailableTargetsDoNothing() {
        assertEquals(RoomAction.None, RoomLogic.actionFor(all, kitchen, "nope"))
        assertEquals(RoomAction.None, RoomLogic.actionFor(listOf(kitchen, deck.copy(available = false)), kitchen, "Deck"))
    }

    // ---- power, hidden rooms, first-run suggestion -----------------------------------------------------------

    private fun withPower(p: Player, powered: Boolean?) = p.copy(supportedFeatures = p.supportedFeatures + "power", powered = powered)

    @Test fun powerButtonShowsOnlyForRoomsThatListPowerAndAreReachable() {
        val d = withPower(deck, true)
        val rows = RoomLogic.rows(listOf(d, pool, withPower(kitchen, true).copy(available = false)), d)
        assertTrue(rows.first { it.player.playerId == "Deck" }.canPower)
        assertFalse(rows.first { it.player.playerId == "Pool" }.canPower)
        assertFalse(rows.first { it.player.playerId == "Kitchen" }.canPower)
    }

    @Test fun anOffRoomSaysOffAndAnUnknownPowerStateCountsAsOn() {
        val off = withPower(pool, false)
        val unknown = withPower(kitchen, null)
        val rows = RoomLogic.rows(listOf(deck, off, unknown), deck)
        val poolRow = rows.first { it.player.playerId == "Pool" }
        assertEquals("Off", poolRow.subtitle)
        assertFalse(poolRow.powered)
        assertTrue(rows.first { it.player.playerId == "Kitchen" }.powered)
    }

    @Test fun turnEverythingOffTargetsEveryVisibleRoomThatCanPowerAndIsOn() {
        val players = listOf(withPower(deck, true), withPower(pool, true), withPower(kitchen, false), kitchenless(), tv)
        // Kitchen is already off, "Plain" cannot power, the TV lists no power.
        assertEquals(listOf("Deck", "Pool"), RoomLogic.powerOffTargets(players))
    }

    private fun kitchenless() = player("Plain")

    @Test fun aStaticGroupCountsOnceAndItsMembersAreSkipped() {
        val g = withPower(backyard, true).copy(staticGroupMembers = listOf("Deck", "Pool"))
        val other = withPower(kitchen, true)
        // Two separate groups or rooms on at once: both are switched off, not just the selected one.
        assertEquals(
            listOf("Backyard", "Kitchen"),
            RoomLogic.powerOffTargets(listOf(g, withPower(deck, true), withPower(pool, true), other)),
        )
        assertEquals(emptyList<String>(), RoomLogic.powerOffTargets(listOf(g.copy(powered = false))))
    }

    @Test fun hiddenRoomsAreNotSwitchedOffByTurnEverythingOff() {
        assertEquals(listOf("Pool"), RoomLogic.powerOffTargets(listOf(withPower(deck, true), withPower(pool, true)), setOf("Deck")))
    }

    @Test fun hiddenRoomsAreNotListedAndNeverChosenAsTheDefault() {
        val players = listOf(deck, pool)
        assertEquals(listOf("Pool"), RoomLogic.rows(players, pool, setOf("Deck")).map { it.player.playerId })
        assertEquals("Pool", RoomLogic.effectiveLeader(players, "Deck", setOf("Deck"))?.playerId)
        assertEquals("Pool", RoomLogic.effectiveLeader(players, null, setOf("Deck"))?.playerId)
    }

    @Test fun firstRunSuggestionHidesTvsButKeepsSpeakersAndGroups() {
        assertEquals(setOf("TV"), RoomLogic.suggestedHidden(all))
    }

    // ---- now playing and controls -------------------------------------------------------------------------

    @Test fun positionAdvancesWhilePlayingOnly() {
        val base = player("A", state = "playing").copy(
            elapsedTime = 10.0, elapsedTimeLastUpdated = 1000.0,
            currentMedia = PlayerMedia(title = "T", artist = "Ar", album = "Al", duration = 200),
        )
        val playing = nowPlaying(base, null, 1005.0)
        assertEquals(15.0, playing.positionSeconds, 0.001)
        assertEquals("Ar · Al", playing.subtitle)
        assertTrue(playing.playing)
        val paused = nowPlaying(base.copy(playbackState = "paused"), null, 1005.0)
        assertEquals(10.0, paused.positionSeconds, 0.001)
        assertFalse(paused.playing)
    }

    @Test fun theQueuePositionWinsOverAPlayerClockThatRestartedAfterASeek() {
        val p = player("A", state = "playing").copy(
            elapsedTime = 0.0, elapsedTimeLastUpdated = 1000.0, currentMedia = PlayerMedia(title = "T", duration = 200),
        )
        val q = PlayerQueue(queueId = "A", elapsedTime = 90.0, elapsedTimeLastUpdated = 1000.0)
        assertEquals(93.0, nowPlaying(p, q, 1003.0).positionSeconds, 0.001)
    }

    @Test fun positionIsClampedToTheTrackLength() {
        val p = player("A", state = "playing").copy(
            elapsedTime = 195.0, elapsedTimeLastUpdated = 1000.0, currentMedia = PlayerMedia(title = "T", duration = 200),
        )
        assertEquals(200.0, nowPlaying(p, null, 1100.0).positionSeconds, 0.001)
    }

    @Test fun idlePlayerStillShowsItsLastItemButIsNotPlaying() {
        val p = player("A", state = "idle").copy(currentMedia = PlayerMedia(title = "Old song"))
        val np = nowPlaying(p, null, 0.0)
        assertTrue(np.hasMedia)
        assertFalse(np.playing)
    }

    @Test fun fallsBackToTheQueueItemWhenThePlayerHasNoMedia() {
        val q = PlayerQueue(
            queueId = "A", elapsedTime = 30.0, elapsedTimeLastUpdated = 500.0,
            currentItem = QueueItem(name = "Artist - Title", duration = 100, image = MediaItemImage(proxyId = "p")),
        )
        val np = nowPlaying(player("A", state = "playing"), q, 510.0)
        assertEquals("Artist - Title", np.title)
        assertEquals(40.0, np.positionSeconds, 0.001)
        assertEquals("p", np.imageRef?.proxyId)
        assertEquals(100, np.durationSeconds)
    }

    @Test fun nothingPlayingHasNoMedia() {
        assertFalse(nowPlaying(player("A"), null, 0.0).hasMedia)
    }

    @Test fun controlsFollowTheFeatureList() {
        val c = controlsFor(tv)
        // Next, previous and seek are queue commands the server runs, so they show even when the player lists no features.
        assertFalse(c.playPause); assertTrue(c.nextPrevious); assertTrue(c.seek); assertTrue(c.volume)
        val g = controlsFor(kitchen)
        assertTrue(g.playPause); assertTrue(g.nextPrevious); assertTrue(g.seek); assertTrue(g.volume)
    }
}
