package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.produceState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.Phase
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.controlsFor
import com.feerlessleadr.downbeat.data.nowPlaying
import com.feerlessleadr.downbeat.ma.ImageUrls
import com.feerlessleadr.downbeat.ma.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEEK_HOLD_SECONDS = 5.0

/**
 * The Playing screen (docs/design.md): the artwork is the focus, then title, position, transport and volume. Under
 * them, where it plays ("Playing on") and one line for the next song that opens the queue. Nothing scrolls: the artwork
 * takes whatever height is left.
 */
@Composable
fun PlayingScreen(
    repo: MusicRepository,
    controller: PlaybackController,
    onError: (String) -> Unit,
    /** Set when this screen is opened from the player bar; shows a button that closes it. */
    onClose: (() -> Unit)? = null,
    /**
     * True when the screen fills the whole window (opened from the player bar): the picture then runs under the
     * status and navigation bars, and the content keeps inside the safe area. False inside the app's own padding.
     */
    edgeToEdge: Boolean = false,
) {
    val state by repo.state.collectAsStateWithLifecycle()
    val selected by repo.selectedRoomId.collectAsStateWithLifecycle()
    val hidden by repo.hiddenRooms.collectAsStateWithLifecycle()
    val leader = RoomLogic.effectiveLeader(state.players, selected, hidden)
    var showRooms by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    if (onClose != null) BackHandler { onClose() }

    if (showQueue && leader != null) {
        QueueScreen(repo, controller, leader, state.queues[leader.playerId], onBack = { showQueue = false }, onError = onError)
        return
    }

    // The picture behind the screen: the artist if the server has one, else the album art. The screen is always dark so
    // text stays readable over any picture.
    val serverUrl = repo.serverUrl
    val queueNow = leader?.let { state.queues[it.playerId] }
    val artistRef = queueNow?.currentItem?.artistRef()
    val artistImage by produceState<com.feerlessleadr.downbeat.ma.MediaItemImage?>(null, artistRef) {
        value = null
        value = artistRef?.let { repo.artistImage(it.first, it.second) }
    }
    val albumArt = serverUrl?.let { s -> ImageUrls.rewriteHost(s, leader?.currentMedia?.imageUrl) ?: ImageUrls.forImage(s, queueNow?.currentItem?.image, 512) }
    val backdrop = serverUrl?.let { ImageUrls.forImage(it, artistImage, 512) } ?: albumArt

    MaterialTheme(colorScheme = LocalDarkScheme.current) {
    // (Swiping down to minimise is handled by the layer this screen sits in, see AppRoot.)
    Box(Modifier.fillMaxSize()) {
    Backdrop(backdrop)
    // Status and navigation bar icons turn light while this screen is open, since the picture behind them is dark.
    val view = LocalView.current
    DisposableEffect(edgeToEdge) {
        val window = (view.context as? android.app.Activity)?.window
        if (edgeToEdge && window != null) {
            val insets = androidx.core.view.WindowCompat.getInsetsController(window, view)
            val wasLightStatus = insets.isAppearanceLightStatusBars
            val wasLightNav = insets.isAppearanceLightNavigationBars
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
            onDispose {
                insets.isAppearanceLightStatusBars = wasLightStatus
                insets.isAppearanceLightNavigationBars = wasLightNav
            }
        } else {
            onDispose {}
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().then(if (edgeToEdge) Modifier.safeDrawingPadding() else Modifier)) {
    // The artwork is the biggest square that fits once room is kept for the controls below it.
    val artSide = minOf(maxWidth - 48.dp, maxHeight - 400.dp).coerceAtLeast(140.dp)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = "Close") }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
        if (state.phase != Phase.Online) {
            Text(
                if (state.phase == Phase.Reconnecting) "Connection lost. Reconnecting…" else "Connecting…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (leader == null) {
            Spacer(Modifier.height(48.dp))
            Text("No rooms are available.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Rooms appear here when Music Assistant has a speaker that is switched on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LeaderPanel(
                repo, controller, leader, state.queues[leader.playerId], onError,
                roomText = roomLabel(state.players, leader),
                onRooms = { showRooms = true },
                onQueue = { showQueue = true },
                artSide = artSide,
            )
        }
    }
    }
    }

    if (showRooms && leader != null) {
        RoomsSheet(
            repo = repo,
            controller = controller,
            onError = onError,
            onDismiss = { showRooms = false },
        )
    }
    }
}

@Composable
private fun ColumnScope.LeaderPanel(
    repo: MusicRepository,
    controller: PlaybackController,
    leader: Player,
    queue: com.feerlessleadr.downbeat.ma.PlayerQueue?,
    onError: (String) -> Unit,
    roomText: String,
    onRooms: () -> Unit,
    onQueue: () -> Unit,
    artSide: androidx.compose.ui.unit.Dp,
) {
    val scope = rememberCoroutineScope()
    fun run(block: suspend () -> String?) {
        scope.launch { block()?.let(onError) }
    }

    val playing = leader.playbackState == "playing"
    // Re-evaluate about twice a second so the position bar moves without waiting for the server (and a seek hold ends).
    val nowSeconds by produceState(System.currentTimeMillis() / 1000.0) {
        while (true) {
            value = System.currentTimeMillis() / 1000.0
            delay(500)
        }
    }
    val np = nowPlaying(leader, queue, nowSeconds)
    val controls = controlsFor(leader)
    val serverUrl = repo.serverUrl

    // Artwork: the largest square that fits in the height left over.
    val artUrl = serverUrl?.let { ImageUrls.rewriteHost(it, np.imageUrl) ?: ImageUrls.forImage(it, np.imageRef, 512) }

    // A carousel. The previous and next songs' covers sit right beside the current one (a small gap), so both show while
    // swiping. Past the threshold the neighbour slides into the middle and the command is sent; when the new song
    // arrives the covers are re-centred, which looks seamless because the cover that slid in is the new current one.
    val neighbours by produceState<Pair<com.feerlessleadr.downbeat.ma.QueueItem?, com.feerlessleadr.downbeat.ma.QueueItem?>>(
        null to null, leader.playerId, queue?.currentIndex, queue?.items,
    ) {
        val api = repo.api
        val cur = queue?.currentIndex
        if (api == null || cur == null) {
            value = null to null
        } else {
            try {
                val items = api.queueItems(leader.playerId, limit = 3, offset = (cur - 1).coerceAtLeast(0))
                value = (if (cur > 0) items.getOrNull(0) else null) to items.getOrNull(if (cur > 0) 2 else 1)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // No neighbours to show; swiping still works.
            }
        }
    }
    val prevUrl = serverUrl?.let { ImageUrls.forImage(it, neighbours.first?.image, 512) }
    val nextUrl = serverUrl?.let { ImageUrls.forImage(it, neighbours.second?.image, 512) }
    val density = LocalDensity.current
    val slidePx = with(density) { (artSide + 24.dp).toPx() }
    val fadePx = with(density) { 24.dp.toPx() }
    var artOffset by remember { mutableFloatStateOf(0f) }
    var pending by remember { mutableStateOf(false) }
    suspend fun slideArt(to: Float, ms: Int) = animate(artOffset, to, animationSpec = tween(ms)) { v, _ -> artOffset = v }
    LaunchedEffect(np.title, artUrl) {
        if (pending) {
            pending = false
            artOffset = 0f
        }
    }
    // The song never changed (end of the queue, or the server restarted the song instead): bring the cover back.
    suspend fun giveUpIfUnchanged() {
        delay(1200)
        if (pending) {
            pending = false
            slideArt(0f, 220)
        }
    }
    Box(
        Modifier.size(artSide).align(Alignment.CenterHorizontally)
            .pointerInput(leader.playerId) {
                val threshold = 72.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val d = artOffset
                        scope.launch {
                            if (pending) return@launch
                            if (d <= -threshold || d >= threshold) {
                                val forward = d < 0
                                pending = true
                                slideArt(if (forward) -slidePx else slidePx, 180)
                                val error = if (forward) controller.next(leader) else controller.previous(leader)
                                if (error != null) {
                                    onError(error)
                                    pending = false
                                    slideArt(0f, 220)
                                } else {
                                    giveUpIfUnchanged()
                                }
                            } else {
                                slideArt(0f, 180)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { if (!pending) slideArt(0f, 180) } },
                ) { change, dx ->
                    change.consume()
                    if (!pending) artOffset += dx
                }
            },
    ) {
        Cover(prevUrl, artSide, Modifier.graphicsLayer { translationX = artOffset - slidePx; alpha = (kotlin.math.abs(artOffset) / fadePx).coerceIn(0f, 1f) })
        Cover(artUrl, artSide, Modifier.graphicsLayer { translationX = artOffset })
        Cover(nextUrl, artSide, Modifier.graphicsLayer { translationX = artOffset + slidePx; alpha = (kotlin.math.abs(artOffset) / fadePx).coerceIn(0f, 1f) })
    }

    Column {
    Text(
        if (np.hasMedia) np.title else "Nothing playing",
        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    if (np.subtitle.isNotBlank()) {
        Text(np.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    // Position bar. A drag shows the dragged value; the seek is sent when the finger lifts.
    val duration = np.durationSeconds
    if (np.hasMedia && duration != null && duration > 0) {
        var drag by remember(leader.playerId, np.title) { mutableFloatStateOf(-1f) }
        // After a release the bar stays where it was dropped (and keeps moving if playing) for a few seconds, until
        // the server's own position catches up, so it never snaps back while the player restarts its stream.
        var held by remember(leader.playerId, np.title) { mutableStateOf<Pair<Float, Double>?>(null) }
        val hold = held?.takeIf { nowSeconds - it.second < SEEK_HOLD_SECONDS }
        val shown = when {
            drag >= 0f -> drag
            hold != null -> hold.first + if (playing) (nowSeconds - hold.second).toFloat() else 0f
            else -> np.positionSeconds.toFloat()
        }
        Slider(
            value = shown.coerceIn(0f, duration.toFloat()),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                val target = drag
                drag = -1f
                if (target >= 0f) {
                    held = target to System.currentTimeMillis() / 1000.0
                    run { controller.seek(leader, target.toInt()) }
                }
            },
            valueRange = 0f..duration.toFloat(),
            enabled = controls.seek,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown.toDouble()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(duration.toDouble()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Spacer(Modifier.height(24.dp))
    }
    }

    Column {
    val shuffle = queue?.shuffleEnabled == true
    val repeat = queue?.repeatMode ?: "off"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { run { controller.toggleShuffle(leader, shuffle) } }) {
            Icon(
                painterResource(R.drawable.ic_shuffle), contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (controls.nextPrevious) {
            IconButton(onClick = { run { controller.previous(leader) } }) {
                Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }
        }
        if (controls.playPause) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable { run { controller.playPause(leader) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp),
                )
            }
        }
        if (controls.nextPrevious) {
            IconButton(onClick = { run { controller.next(leader) } }) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next", modifier = Modifier.size(32.dp))
            }
        }
        IconButton(onClick = { run { controller.cycleRepeat(leader, repeat) } }) {
            Icon(
                painterResource(if (repeat == "one") R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                contentDescription = "Repeat $repeat",
                tint = if (repeat != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (controls.volume) {
        val together = RoomLogic.currentSet(leader).size > 1
        val serverVolume = (if (together) leader.groupVolume else leader.volumeLevel)?.toFloat() ?: 0f
        var volume by remember(serverVolume) { mutableFloatStateOf(serverVolume) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_volume), contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { run { controller.setSetVolume(leader, volume.toInt()) } },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }

    }

    Column(Modifier.padding(bottom = 8.dp)) {
        PlayingOnCard(roomText, onRooms)
        NextUpRow(queue, onQueue)
    }
}

/** "Playing on  Deck + Pool", a rounded card that opens the rooms list. */
@Composable
private fun PlayingOnCard(roomText: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_speaker), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Playing on", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    roomText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = "Choose rooms", tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** One line: the next song, or the song count when nothing is next. Tapping it opens the queue. */
@Composable
private fun NextUpRow(queue: com.feerlessleadr.downbeat.ma.PlayerQueue?, onClick: () -> Unit) {
    val next = queue?.nextItem?.name?.takeIf { it.isNotBlank() }
    val count = queue?.items ?: 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_playlist), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(if (next != null) "Up next" else "Queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                next ?: (if (count == 1) "1 song" else "$count songs"),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (next != null && count > 0) {
            Text(
                if (count == 1) "1 song" else "$count songs",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Open the queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Kitchen", "Kitchen + Patio", "Kitchen + 2 more". A static group shows its own name. */
internal fun roomLabel(players: List<Player>, leader: Player): String {
    if (leader.type == "group") return leader.name
    val names = RoomLogic.currentSet(leader)
        .mapNotNull { id -> players.firstOrNull { it.playerId == id }?.name }
        .sortedBy { if (it == leader.name) 0 else 1 }
    return when {
        names.size <= 2 -> names.joinToString(" + ")
        else -> "${names.first()} + ${names.size - 1} more"
    }.ifBlank { leader.name }
}

internal fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * The picture behind the Playing screen: blurred (where the phone supports it) and darkened so the controls stay
 * readable. Changes smoothly with the song. Without a picture it is a plain dark background.
 */
@Composable
private fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize().background(Color(0xFF121016))) {
        Crossfade(targetState = url, label = "backdrop") { u ->
            if (u != null) {
                AsyncImage(
                    model = u, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(10.dp),
                )
            }
        }
        // Lighter at the top, darker toward the controls, so the picture stays clear and the text stays readable.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Black.copy(alpha = 0.76f))),
            ),
        )
    }
}

/** One album cover (rounded, with a placeholder). */
@Composable
private fun Cover(url: String?, side: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(side).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_nav_playing), contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
