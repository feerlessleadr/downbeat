package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.gestures.scrollBy
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.ma.Player
import com.feerlessleadr.downbeat.ma.PlayerQueue
import com.feerlessleadr.downbeat.ma.QueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The queue of the rooms that are playing, shown under the controls on the Playing screen. It starts at the song that
 * is playing. Drag the handle of an upcoming song to reorder it, tap a song to jump to it, and use its menu to remove
 * it. Songs that already played are not shown, and the playing song cannot be dragged.
 */
@Composable
fun QueueSection(
    repo: MusicRepository,
    controller: PlaybackController,
    leader: Player,
    queue: PlayerQueue?,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when the queue is a page of its own: shows a back arrow and handles the Back button. */
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    var serverItems by remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val serverUrl = repo.serverUrl
    // Where the playing song is in the full list: found by its id (the index can disagree when the queue is shuffled).
    val currentId = queue?.currentItem?.queueItemId
    val currentPos = serverItems.indexOfFirst { currentId != null && it.queueItemId == currentId }.takeIf { it >= 0 }
        ?: (queue?.currentIndex ?: 0).coerceIn(0, (serverItems.size - 1).coerceAtLeast(0))

    // What is on screen. Equal to the server's order except while a song is being dragged.
    val working = remember { mutableStateListOf<QueueItem>() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    // After a swap the list needs one layout pass before the next swap is judged (positions are stale until then).
    var settling by remember { mutableStateOf(false) }

    // Read the queue again whenever it changes (items added or removed, the song moved on) or after our own edits.
    LaunchedEffect(leader.playerId, queue?.items, queue?.currentIndex, reload) {
        val api = repo.api
        if (api == null) {
            loadError = "Not connected to the server."
        } else {
            try {
                serverItems = api.queueItems(leader.playerId, limit = 500, offset = 0)
                loadError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = describe(e)
            }
        }
        loaded = true
    }
    // The whole queue is shown: songs that already played above the playing one (dimmed), then the upcoming songs.
    var scrolledFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(serverItems, currentPos) {
        if (dragKey == null) {
            working.clear()
            working.addAll(serverItems)
            // When the playing song changes (it ended, or a song was tapped), scroll so it is at the top.
            val id = serverItems.getOrNull(currentPos)?.queueItemId
            if (id != null && id != scrolledFor) {
                scrolledFor = id
                listState.scrollToItem(currentPos)
            }
        }
    }
    LaunchedEffect(Unit) { snapshotFlow { listState.layoutInfo }.collect { settling = false } }
    var warnedAbove by remember { mutableStateOf(false) }

    /** Swaps the dragged song with the one under its centre. Songs never go above or onto the playing one (or the played ones). */
    fun checkSwap() {
        val key = dragKey ?: return
        if (settling) return
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val from = working.indexOfFirst { it.queueItemId == key }
        val target = info.visibleItemsInfo.firstOrNull { it.key != key && center >= it.offset && center <= it.offset + it.size } ?: return
        val to = working.indexOfFirst { it.queueItemId == target.key }
        if (to <= currentPos) {
            if (!warnedAbove) {
                warnedAbove = true
                onError("A song can't go above or onto the one that's playing. Use Play next from the Library to queue it next.")
            }
            return
        }
        if (to >= 1 && to != from) {
            val newBase = if (to > from) target.offset + target.size - dragged.size else target.offset
            val item = working.removeAt(from)
            working.add(to, item)
            dragOffset -= (newBase - dragged.offset)
            settling = true
        }
    }

    // Keep scrolling while the finger is held near the top or bottom edge, so a song can be dragged a long way.
    LaunchedEffect(dragKey) {
        while (dragKey != null) {
            val info = listState.layoutInfo
            val dragged = info.visibleItemsInfo.firstOrNull { it.key == dragKey }
            if (dragged != null) {
                val top = dragged.offset + dragOffset
                val bottom = top + dragged.size
                val zone = dragged.size * 1.5f
                val speed = when {
                    top < info.viewportStartOffset + zone ->
                        -(((info.viewportStartOffset + zone) - top) / zone).coerceIn(0f, 1f) * 28f
                    bottom > info.viewportEndOffset - zone ->
                        ((bottom - (info.viewportEndOffset - zone)) / zone).coerceIn(0f, 1f) * 28f
                    else -> 0f
                }
                if (speed != 0f) {
                    // The list moves under the finger, so the song's offset grows by what was scrolled.
                    dragOffset += listState.scrollBy(speed)
                    checkSwap()
                }
            }
            delay(16)
        }
    }

    fun finishDrag(apply: Boolean) {
        val key = dragKey ?: return
        val to = working.indexOfFirst { it.queueItemId == key }
        val from = dragFrom
        dragKey = null
        dragOffset = 0f
        dragFrom = -1
        if (apply && from >= 0 && to >= 0 && to != from) {
            scope.launch {
                controller.moveInQueue(leader, key, to - from)?.let(onError)
                reload++
            }
        } else {
            reload++
        }
    }

    fun onDrag(deltaY: Float) {
        if (dragKey == null) return
        dragOffset += deltaY
        checkSwap()
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(
                "Queue", style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            val total = queue?.items ?: 0
            Text(
                if (total == 1) "1 song" else "$total songs",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Queue options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear queue") }, enabled = serverItems.isNotEmpty(),
                        onClick = { menuOpen = false; confirmClear = true },
                    )
                }
            }
        }
        when {
            loadError != null && working.isEmpty() -> Text(loadError ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            loaded && working.isEmpty() -> Text(
                "The queue is empty. Play something from the Library.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(working, key = { it.queueItemId }) { item ->
                    val position = working.indexOfFirst { it.queueItemId == item.queueItemId }
                    val isDragged = dragKey == item.queueItemId
                    QueueRow(
                        item = item,
                        number = position + 1,
                        isCurrent = position == currentPos,
                        isPlayed = position < currentPos,
                        serverUrl = serverUrl,
                        modifier = if (isDragged) {
                            Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                        } else {
                            Modifier.animateItem()
                        },
                        elevated = isDragged,
                        onPlay = { act(scope, onError, { reload++ }) { controller.playQueueIndex(leader, position) } },
                        onRemove = { act(scope, onError, { reload++ }) { controller.removeFromQueue(leader, item.queueItemId) } },
                        dragHandle = if (position > currentPos) {
                            Modifier.pointerInput(item.queueItemId) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = item.queueItemId
                                        dragFrom = working.indexOfFirst { it.queueItemId == item.queueItemId }
                                        dragOffset = 0f
                                        warnedAbove = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDrag(amount.y)
                                    },
                                    onDragEnd = { finishDrag(apply = true) },
                                    onDragCancel = { finishDrag(apply = false) },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the queue?") },
            text = { Text("This removes all ${serverItems.size} songs and stops playback in these rooms.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    act(scope, onError, { reload++ }) { controller.clearQueue(leader) }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun act(scope: kotlinx.coroutines.CoroutineScope, onError: (String) -> Unit, after: () -> Unit, block: suspend () -> String?) {
    scope.launch {
        block()?.let(onError)
        after()
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    number: Int,
    isCurrent: Boolean,
    isPlayed: Boolean,
    serverUrl: String?,
    modifier: Modifier,
    elevated: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    /** Null for the playing song, which cannot be dragged. */
    dragHandle: Modifier?,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Surface(modifier = modifier, tonalElevation = if (elevated) 8.dp else 0.dp, shadowElevation = if (elevated) 6.dp else 0.dp) {
        Row(
            Modifier.fillMaxWidth().graphicsLayer { alpha = if (isPlayed) 0.5f else 1f }.clickable(onClick = onPlay).padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isCurrent) "▶" else number.toString(), style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            ArtThumb(item.image, serverUrl, 40)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    item.name, style = MaterialTheme.typography.titleSmall, color = accent,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                item.duration?.takeIf { it > 0 }?.let {
                    Text(formatTime(it.toDouble()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (dragHandle != null) {
                Box(Modifier.size(40.dp).then(dragHandle), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = "Drag to reorder ${item.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(Modifier.size(40.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${item.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

/** The queue as a page of its own, opened from the "Up next" line on the Playing screen. */
@Composable
fun QueueScreen(
    repo: MusicRepository,
    controller: PlaybackController,
    leader: Player,
    queue: PlayerQueue?,
    onBack: () -> Unit,
    onError: (String) -> Unit,
) {
    QueueSection(repo, controller, leader, queue, onError, Modifier.fillMaxSize(), onBack = onBack)
}
