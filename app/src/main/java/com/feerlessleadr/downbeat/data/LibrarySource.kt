package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.MaException
import com.feerlessleadr.downbeat.ma.MediaItem
import com.feerlessleadr.downbeat.ma.SearchResults

/** What the Library and Search tabs read from the server. Every call throws when offline or when the server refuses. */
class LibrarySource(private val repo: MusicRepository) {

    private fun api() = repo.api ?: throw MaException(null, "Not connected to the server.")

    /** One page of a library list. [type] is albums, artists, playlists, radios or tracks. Sorted by name. */
    suspend fun page(type: String, offset: Int, limit: Int = PAGE_SIZE): List<MediaItem> =
        api().library(type, limit = limit, offset = offset, orderBy = "name")

    /** An album's or playlist's tracks, or an artist's albums. Other kinds have no children. */
    suspend fun children(parent: MediaItem): List<MediaItem> {
        val api = api()
        return when (parent.mediaType) {
            "album" -> api.albumTracks(parent.itemId, parent.provider)
            "playlist" -> api.playlistTracks(parent.itemId, parent.provider)
            "artist" -> api.artistAlbums(parent.itemId, parent.provider)
            else -> emptyList()
        }
    }

    suspend fun search(query: String): SearchResults = api().search(query.trim(), limit = 10, libraryOnly = true)

    companion object {
        const val PAGE_SIZE = 50
    }
}

/** Where a play request puts the music: replace the queue, or after the current track, or at the end. */
enum class PlayMode(val option: String) {
    Replace("replace"),
    Next("next"),
    Add("add"),
}

/** True for items that open a page of their own (albums, artists, playlists) instead of playing on tap. */
fun MediaItem.opensDetail(): Boolean = mediaType == "album" || mediaType == "artist" || mediaType == "playlist"

/** The second line of a list row. */
fun mediaSubtitle(item: MediaItem): String = when (item.mediaType) {
    "artist" -> "Artist"
    "album" -> listOfNotNull(item.artistNames.ifBlank { null }, item.year?.takeIf { it > 0 }?.toString()).joinToString(" · ")
    "track" -> listOfNotNull(item.artistNames.ifBlank { null }, item.album?.name?.ifBlank { null }).joinToString(" · ")
    "playlist" -> "Playlist"
    "radio" -> "Radio"
    else -> item.artistNames
}
