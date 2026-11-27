package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.LibrarySource
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.PlayMode
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.mediaSubtitle
import com.feerlessleadr.downbeat.data.opensDetail
import com.feerlessleadr.downbeat.ma.ImageUrls
import com.feerlessleadr.downbeat.ma.MaException
import com.feerlessleadr.downbeat.ma.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** (item, mode, startItem, shuffle). Plays in the rooms currently chosen (see the Rooms page and the Playing tab). */
typealias PlayAction = (MediaItem, PlayMode, MediaItem?, Boolean?) -> Unit

@Composable
fun rememberPlayAction(repo: MusicRepository, controller: PlaybackController, onMessage: (String) -> Unit): PlayAction {
    val scope = rememberCoroutineScope()
    return remember(repo, controller) {
        val action: PlayAction = { item, mode, start, shuffle ->
            val players = repo.state.value.players
            val leader = RoomLogic.effectiveLeader(players, repo.selectedRoomId.value, repo.hiddenRooms.value)
            if (leader == null) {
                onMessage("No room is available to play in.")
            } else {
                scope.launch {
                    val error = controller.playItem(leader, item, mode, start, shuffle)
                    // Starting music says nothing: the player bar appears. Play next and Add to queue have no other
                    // sign that they worked, so those two still confirm.
                    onMessage(
                        error ?: when (mode) {
                            PlayMode.Next -> "Playing next: ${item.name}"
                            PlayMode.Add -> "Added to the queue: ${item.name}"
                            PlayMode.Replace -> ""
                        },
                    )
                }
            }
        }
        action
    }
}

internal fun describe(e: Exception): String =
    if (e is MaException) e.message ?: "The server refused that." else "Something went wrong. Check your connection."

// ---- Rows ---------------------------------------------------------------------------------------------------

@Composable
private fun MediaRow(item: MediaItem, serverUrl: String?, onClick: () -> Unit, onPlay: PlayAction) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item, serverUrl, 52)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = mediaSubtitle(item)
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${item.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { menu = false; onPlay(item, PlayMode.Replace, null, null) })
                if (item.mediaType != "radio") {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; onPlay(item, PlayMode.Next, null, null) })
                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; onPlay(item, PlayMode.Add, null, null) })
                }
            }
        }
    }
}

@Composable
private fun Thumb(item: MediaItem, serverUrl: String?, sizeDp: Int) =
    ArtThumb(item.image, serverUrl, sizeDp, round = item.mediaType == "artist")

/** Square artwork with a placeholder; [round] makes it a circle (artists). */
@Composable
internal fun ArtThumb(image: com.feerlessleadr.downbeat.ma.MediaItemImage?, serverUrl: String?, sizeDp: Int, round: Boolean = false) {
    val url = serverUrl?.let { ImageUrls.forImage(it, image, sizeDp * 3) }
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    val fallback = painterResource(R.drawable.ic_nav_playing)
    Box(
        Modifier.size(sizeDp.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            // A fixed decode size and no fade keep scrolling smooth while many thumbnails arrive for the first time.
            val context = androidx.compose.ui.platform.LocalContext.current
            val px = (sizeDp * 3).coerceAtLeast(1)
            val request = remember(url, px) {
                coil3.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(coil3.size.Size(px, px))
                    .build()
            }
            AsyncImage(
                model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                placeholder = fallback, error = fallback,
            )
        } else {
            Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((sizeDp / 2).dp))
        }
    }
}

// ---- A list that loads more as you scroll ---------------------------------------------------------------------

@Composable
private fun PagedItems(
    state: PagedState,
    pageSize: Int,
    loader: suspend (offset: Int) -> List<MediaItem>,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    row: @Composable (MediaItem) -> Unit,
) {
    val listState = state.listState
    val nearEnd by remember(state) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(state, nearEnd, state.items.size, state.end, state.error) {
        if (!state.end && state.error == null && nearEnd && !state.loading) {
            state.loading = true
            try {
                val page = loader(state.items.size)
                state.items.addAll(page)
                if (page.size < pageSize) state.end = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.error = describe(e)
            } finally {
                state.loading = false
            }
        }
    }
    LazyColumn(modifier.fillMaxSize(), state = listState) {
        item { header() }
        items(state.items, key = { it.uri.ifBlank { it.itemId } + it.mediaType }) { row(it) }
        item {
            when {
                state.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { state.error = null }) { Text("Try again") }
                }
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.end && state.items.isEmpty() -> Text(
                    "Nothing here yet.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

// ---- Detail page: an album, playlist or artist ----------------------------------------------------------------

@Composable
private fun DetailPage(
    item: MediaItem,
    browse: BrowseState,
    source: LibrarySource,
    serverUrl: String?,
    onPlay: PlayAction,
) {
    BackHandler { browse.pop() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { browse.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PagedItems(
            state = browse.pager("detail:" + item.uri.ifBlank { item.itemId }),
            pageSize = Int.MAX_VALUE,
            loader = { offset -> if (offset == 0) source.children(item) else emptyList() },
            header = {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(item, serverUrl, 96)
                        Column(Modifier.padding(start = 16.dp)) {
                            val sub = mediaSubtitle(item)
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onPlay(item, PlayMode.Replace, null, false) }) { Text("Play") }
                        OutlinedButton(onClick = { onPlay(item, PlayMode.Replace, null, true) }) { Text("Shuffle") }
                    }
                }
            },
        ) { child ->
            MediaRow(
                item = child, serverUrl = serverUrl, onPlay = onPlay,
                onClick = {
                    // A track plays inside its album or playlist, starting at that track. An album under an artist opens.
                    if (child.opensDetail()) browse.stack.add(child) else onPlay(item, PlayMode.Replace, child, null)
                },
            )
        }
    }
}

// ---- Library lists --------------------------------------------------------------------------------------------

private val LIBRARY_TYPES = listOf("albums" to "Albums", "artists" to "Artists", "playlists" to "Playlists", "radios" to "Radio", "tracks" to "Tracks")

/**
 * A library list. With [fixedType] it is a shortcut destination (Playlists, Albums, ...) with a title; without it, the
 * Library destination with chips to switch between the kinds.
 */
@Composable
fun LibraryScreen(
    repo: MusicRepository,
    source: LibrarySource,
    controller: PlaybackController,
    browse: BrowseState,
    fixedType: String?,
    onMessage: (String) -> Unit,
) {
    val play = rememberPlayAction(repo, controller, onMessage)
    val serverUrl = repo.serverUrl
    val top = browse.stack.lastOrNull()
    if (top != null) {
        DetailPage(top, browse, source, serverUrl, play)
        return
    }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        LibraryHeader(if (fixedType == null) "Library" else LIBRARY_TYPES.first { it.first == fixedType }.second)
        if (fixedType == null) {
            // The kinds are pages: swipe the list sideways or tap a tab, and the tab underline follows.
            val pagerState = rememberPagerState(
                initialPage = LIBRARY_TYPES.indexOfFirst { it.first == browse.libraryType }.coerceAtLeast(0),
            ) { LIBRARY_TYPES.size }
            // Remember which kind was showing, so the Library comes back to it.
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { browse.libraryType = LIBRARY_TYPES[it].first }
            }
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                LIBRARY_TYPES.forEachIndexed { index, (_, label) ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                label, style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            // A thin line and some air between the tabs and the list, so the first row does not feel squeezed against them.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
                LibraryList(LIBRARY_TYPES[page].first, browse, source, serverUrl, play)
            }
        } else {
            LibraryList(fixedType, browse, source, serverUrl, play)
        }
    }
}

/** One kind of library item as a list that loads more as you scroll. */
@Composable
private fun LibraryList(type: String, browse: BrowseState, source: LibrarySource, serverUrl: String?, play: PlayAction) {
    PagedItems(state = browse.pager("root:$type"), pageSize = LibrarySource.PAGE_SIZE, loader = { offset -> source.page(type, offset) }) { item ->
        MediaRow(
            item = item, serverUrl = serverUrl, onPlay = play,
            onClick = { if (item.opensDetail()) browse.stack.add(item) else play(item, PlayMode.Replace, null, null) },
        )
    }
}

/** A large title over a soft wash of the theme colour that fades out downward. */
@Composable
private fun LibraryHeader(title: String) {
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), Color.Transparent)),
        ),
    ) {
        Text(
            title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp),
        )
    }
}

// ---- Search ---------------------------------------------------------------------------------------------------

@Composable
fun SearchScreen(
    repo: MusicRepository,
    source: LibrarySource,
    controller: PlaybackController,
    search: SearchState,
    onMessage: (String) -> Unit,
) {
    val play = rememberPlayAction(repo, controller, onMessage)
    val serverUrl = repo.serverUrl
    val keyboard = LocalSoftwareKeyboardController.current

    // Search shortly after typing stops. Results stay in the state, so coming back shows them again.
    LaunchedEffect(search.query) {
        if (search.query.isBlank()) {
            search.results = null
            search.error = null
            return@LaunchedEffect
        }
        if (search.results != null && search.error == null && !search.busy && search.lastSearched == search.query) return@LaunchedEffect
        delay(400)
        search.busy = true
        search.error = null
        try {
            search.results = source.search(search.query)
            search.lastSearched = search.query
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            search.error = describe(e)
        } finally {
            search.busy = false
        }
    }

    val top = search.browse.stack.lastOrNull()
    if (top != null) {
        DetailPage(top, search.browse, source, serverUrl, play)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search.query, onValueChange = { search.query = it },
                label = { Text("Search your music") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val r = search.results
        val groups = if (r == null) emptyList() else listOf(
            "Artists" to r.artists, "Albums" to r.albums, "Tracks" to r.tracks, "Playlists" to r.playlists, "Radio" to r.radio,
        ).filter { it.second.isNotEmpty() }
        LazyColumn(Modifier.fillMaxSize(), state = search.listState) {
            if (search.busy) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            search.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (!search.busy && search.error == null && search.query.isNotBlank() && r != null && groups.isEmpty()) {
                item { Text("No results.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            groups.forEach { (title, list) ->
                item(key = "h-$title") {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { "$title-${it.uri.ifBlank { it.itemId }}" }) { item ->
                    MediaRow(
                        item = item, serverUrl = serverUrl, onPlay = play,
                        onClick = { if (item.opensDetail()) search.browse.stack.add(item) else play(item, PlayMode.Replace, null, null) },
                    )
                }
            }
        }
    }
}
