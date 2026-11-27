package com.feerlessleadr.downbeat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NavConfigTest {
    @Test fun aNewInstallShowsBrowsePlaylistsSearchAndRooms() {
        assertEquals(listOf("library", "playlists", "search", "rooms"), NavConfig.decode(null))
        assertEquals(NavConfig.DEFAULT, NavConfig.decode(""))
    }

    @Test fun unknownAndRepeatedIdsAreDroppedAndOrderIsKept() {
        assertEquals(listOf("rooms", "playlists", "library"), NavConfig.sanitize(listOf("rooms", "bogus", "playlists", "rooms", "library")))
    }

    @Test fun nothingBeyondTheMaximumIsKept() {
        assertEquals(4, NavConfig.MAX_ITEMS)
        assertEquals(NavConfig.MAX_ITEMS, NavConfig.sanitize(NavConfig.ALL).size)
        assertEquals(NavConfig.ALL.take(NavConfig.MAX_ITEMS), NavConfig.sanitize(NavConfig.ALL))
    }

    @Test fun anEmptyChoiceFallsBackToTheDefault() {
        assertEquals(NavConfig.DEFAULT, NavConfig.sanitize(emptyList()))
        assertEquals(NavConfig.DEFAULT, NavConfig.sanitize(listOf("nope")))
    }

    @Test fun encodingRoundTrips() {
        val ids = listOf("playlists", "playing", "rooms")
        assertEquals(ids, NavConfig.decode(NavConfig.encode(ids)))
    }

    @Test fun theRepositoryKeepsTheChosenBarAndSavesItCleaned() {
        val prefs = MemoryAppPrefs()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = MusicRepository(OkHttpClient(), object : SessionStore {
            override fun load(): Session? = null
            override fun save(session: Session) {}
            override fun clear() {}
        }, scope, prefs = prefs)
        assertEquals(NavConfig.DEFAULT, repo.navItems.value)
        repo.setNavItems(listOf("rooms", "junk", "albums"))
        assertEquals(listOf("rooms", "albums"), repo.navItems.value)
        assertEquals(listOf("rooms", "albums"), prefs.navItems)
        scope.cancel()
    }
}
