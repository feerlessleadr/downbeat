package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.ItemMapping
import com.feerlessleadr.downbeat.ma.MaException
import com.feerlessleadr.downbeat.ma.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class LibStore(var session: Session?) : SessionStore {
    override fun load() = session
    override fun save(session: Session) { this.session = session }
    override fun clear() { session = null }
}

class LibrarySourceTest {
    private val fake = FakeMaServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: MusicRepository
    private lateinit var source: LibrarySource

    private val albumJson = """{"item_id":"7","provider":"library","name":"Rumours","uri":"library://album/7","media_type":"album",
        "year":1977,"artists":[{"item_id":"1","name":"Fleetwood Mac"}],"metadata":{"images":[{"proxy_id":"abc"}]}}"""

    @Before fun setUp() {
        fake.start()
        fake.tokens.add("good")
        fake.results["players/all"] = "[]"
        fake.results["player_queues/all"] = "[]"
        repo = MusicRepository(OkHttpClient(), LibStore(Session(fake.url(), "good")), scope, requireHttps = false, backoffMs = listOf(50))
        source = LibrarySource(repo)
    }

    @After fun tearDown() {
        repo.signOut()
        scope.cancel()
        fake.stop()
    }

    private fun online() = runBlocking {
        repo.start()
        withTimeout(10_000) { repo.state.first { it.phase == Phase.Online } }
    }

    @Test fun aLibraryPageAsksForTheTypeOffsetAndNameOrder() = runBlocking {
        fake.results["music/albums/library_items"] = "[$albumJson]"
        online()
        val items = source.page("albums", offset = 100)
        assertEquals(listOf("Rumours"), items.map { it.name })
        assertEquals("Fleetwood Mac", items.first().artistNames)
        val args = fake.received.last { it.first == "music/albums/library_items" }.second!!
        assertEquals("100", args["offset"]!!.jsonPrimitive.content)
        assertEquals("50", args["limit"]!!.jsonPrimitive.content)
        assertEquals("name", args["order_by"]!!.jsonPrimitive.content)
    }

    @Test fun childrenDependOnTheKindOfParent() = runBlocking {
        fake.results["music/albums/album_tracks"] = """[{"item_id":"9","provider":"library","name":"Dreams","uri":"library://track/9","media_type":"track"}]"""
        fake.results["music/playlists/playlist_tracks"] = """[{"item_id":"8","provider":"library","name":"Other","uri":"library://track/8","media_type":"track"}]"""
        fake.results["music/artists/artist_albums"] = "[$albumJson]"
        online()
        val album = MediaItem(itemId = "7", provider = "library", mediaType = "album", uri = "library://album/7")
        assertEquals(listOf("Dreams"), source.children(album).map { it.name })
        val args = fake.received.last { it.first == "music/albums/album_tracks" }.second!!
        assertEquals("7", args["item_id"]!!.jsonPrimitive.content)
        assertEquals("library", args["provider_instance_id_or_domain"]!!.jsonPrimitive.content)
        assertEquals(listOf("Other"), source.children(album.copy(mediaType = "playlist")).map { it.name })
        assertEquals(listOf("Rumours"), source.children(album.copy(mediaType = "artist")).map { it.name })
        assertTrue(source.children(album.copy(mediaType = "track")).isEmpty())
    }

    @Test fun searchReturnsGroupedResultsAndTrimsTheQuery() = runBlocking {
        fake.results["music/search"] = """{"artists":[],"albums":[$albumJson],"tracks":[],"playlists":[],"radio":[],"genres":[]}"""
        online()
        val r = source.search("  mac ")
        assertEquals(listOf("Rumours"), r.albums.map { it.name })
        assertTrue(r.tracks.isEmpty())
        assertEquals("mac", fake.received.last { it.first == "music/search" }.second!!["search_query"]!!.jsonPrimitive.content)
    }

    @Test fun anArtistPictureIsLookedUpOnceAndPrefersTheThumb() = runBlocking {
        fake.results["music/artists/get"] = """{"item_id":"1","provider":"library","name":"Fleetwood Mac","media_type":"artist",
            "metadata":{"images":[{"type":"fanart","proxy_id":"wide"},{"type":"thumb","proxy_id":"square"}]}}"""
        online()
        assertEquals("square", repo.artistImage("1", "library")?.proxyId)
        assertEquals("square", repo.artistImage("1", "library")?.proxyId)
        assertEquals(1, fake.received.count { it.first == "music/artists/get" })
        val args = fake.received.last { it.first == "music/artists/get" }.second!!
        assertEquals("1", args["item_id"]!!.jsonPrimitive.content)
        assertEquals("library", args["provider_instance_id_or_domain"]!!.jsonPrimitive.content)
    }

    @Test fun anArtistWithoutPicturesGivesNullSoTheAlbumArtIsUsed() = runBlocking {
        fake.results["music/artists/get"] = """{"item_id":"2","provider":"library","name":"Nobody","media_type":"artist","metadata":{"images":[]}}"""
        online()
        assertEquals(null, repo.artistImage("2", "library"))
    }

    @Test fun aQueueItemExposesItsFirstArtist() {
        val json = """{"queue_item_id":"q1","name":"A - B","media_item":{"artists":[{"item_id":"1","provider":"library","name":"A"},{"item_id":"9","provider":"library"}]}}"""
        val item = com.feerlessleadr.downbeat.ma.MaJson.decodeFromString(com.feerlessleadr.downbeat.ma.QueueItem.serializer(), json)
        assertEquals("1" to "library", item.artistRef())
        val none = com.feerlessleadr.downbeat.ma.MaJson.decodeFromString(com.feerlessleadr.downbeat.ma.QueueItem.serializer(), """{"queue_item_id":"q2","name":"x"}""")
        assertEquals(null, none.artistRef())
        // A media_item of an unexpected shape must not break reading the queue.
        val odd = com.feerlessleadr.downbeat.ma.MaJson.decodeFromString(com.feerlessleadr.downbeat.ma.QueueItem.serializer(), """{"queue_item_id":"q3","name":"x","media_item":{"artists":"oops"}}""")
        assertEquals(null, odd.artistRef())
    }

    @Test fun offlineCallsFailWithAClearMessage() = runBlocking {
        try {
            source.page("albums", 0)
            throw AssertionError("expected a failure")
        } catch (e: MaException) {
            assertEquals("Not connected to the server.", e.message)
        }
    }

    @Test fun rowSubtitlesFollowTheKindOfItem() {
        val artist = ItemMapping(name = "Fleetwood Mac")
        val album = MediaItem(mediaType = "album", year = 1977, artists = listOf(artist))
        assertEquals("Fleetwood Mac · 1977", mediaSubtitle(album))
        assertEquals("Fleetwood Mac", mediaSubtitle(album.copy(year = 0)))
        assertEquals("Fleetwood Mac · Rumours", mediaSubtitle(MediaItem(mediaType = "track", artists = listOf(artist), album = ItemMapping(name = "Rumours"))))
        assertEquals("Artist", mediaSubtitle(MediaItem(mediaType = "artist")))
        assertEquals("Playlist", mediaSubtitle(MediaItem(mediaType = "playlist")))
        assertEquals("Radio", mediaSubtitle(MediaItem(mediaType = "radio")))
    }

    @Test fun onlyAlbumsArtistsAndPlaylistsOpenAPage() {
        assertTrue(MediaItem(mediaType = "album").opensDetail())
        assertTrue(MediaItem(mediaType = "artist").opensDetail())
        assertTrue(MediaItem(mediaType = "playlist").opensDetail())
        assertFalse(MediaItem(mediaType = "track").opensDetail())
        assertFalse(MediaItem(mediaType = "radio").opensDetail())
    }
}
