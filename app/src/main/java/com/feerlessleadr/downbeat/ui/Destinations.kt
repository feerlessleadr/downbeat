package com.feerlessleadr.downbeat.ui

import com.feerlessleadr.downbeat.R

/**
 * One place the bottom bar can point to. [libraryType] is set for the direct shortcuts to a library list (albums,
 * artists, playlists, radios, tracks); "library" itself shows all of them behind chips.
 */
data class Destination(val id: String, val label: String, val icon: Int, val libraryType: String? = null)

val DESTINATIONS: List<Destination> = listOf(
    Destination("library", "Library", R.drawable.ic_nav_library),
    Destination("playlists", "Playlists", R.drawable.ic_playlist, libraryType = "playlists"),
    Destination("albums", "Albums", R.drawable.ic_album, libraryType = "albums"),
    Destination("artists", "Artists", R.drawable.ic_person, libraryType = "artists"),
    Destination("radios", "Radio", R.drawable.ic_radio, libraryType = "radios"),
    Destination("tracks", "Tracks", R.drawable.ic_music_note, libraryType = "tracks"),
    Destination("search", "Search", R.drawable.ic_nav_search),
    Destination("rooms", "Rooms", R.drawable.ic_speaker),
    Destination("playing", "Playing", R.drawable.ic_nav_playing),
)

fun destinationFor(id: String): Destination? = DESTINATIONS.firstOrNull { it.id == id }

/** Always the last button on the bar. It is not part of the customizable list ([NavConfig.ALL]). */
val SETTINGS = Destination("settings", "Settings", R.drawable.ic_settings)
