package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.feerlessleadr.downbeat.ma.MediaItem
import com.feerlessleadr.downbeat.ma.SearchResults

/**
 * Everything a list screen needs to come back exactly as it was left (scroll position, what was loaded, which page was
 * open). These objects live above the tab content, so switching tabs or opening an album and going back keeps the
 * place. They are not saved across process restarts.
 */
class PagedState {
    val items = mutableStateListOf<MediaItem>()
    var end by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val listState = LazyListState()
}

/** One destination's browsing: the pages opened on top of its list (albums, artists, playlists), and its loaded lists. */
class BrowseState {
    /** Pages opened from the list, last is on screen. Empty means the list itself. */
    val stack = mutableStateListOf<MediaItem>()

    /** Which chip is selected on the Library destination. */
    var libraryType by mutableStateOf("albums")

    private val pagers = HashMap<String, PagedState>()

    fun pager(key: String): PagedState = pagers.getOrPut(key) { PagedState() }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Tapping the selected tab again: close open pages first, then reload the list from the server. */
    fun resetOrRefresh() {
        if (stack.isNotEmpty()) stack.clear() else pagers.clear()
    }
}

class SearchState {
    val browse = BrowseState()
    var query by mutableStateOf("")
    var results by mutableStateOf<SearchResults?>(null)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    /** The query the current results belong to, so coming back to the tab does not search again. */
    var lastSearched by mutableStateOf("")
    val listState = LazyListState()
}
