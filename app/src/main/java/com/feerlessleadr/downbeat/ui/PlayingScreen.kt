package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.feerlessleadr.downbeat.data.Phase
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.controlsFor
import com.feerlessleadr.downbeat.data.nowPlaying
import com.feerlessleadr.downbeat.ma.ImageUrls
import com.feerlessleadr.downbeat.ma.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEEK_HOLD_SECONDS = 5.0

/** Playing tab (docs/design.md): the room chip, artwork, transport, one volume slider. */
@Composable
fun PlayingScreen(
    repo: MusicRepository,
    controller: PlaybackController,
    onError: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val state by repo.state.collectAsStateWithLifecycle()
    val selected by repo.selectedRoomId.collectAsStateWithLifecycle()
    val leader = RoomLogic.effectiveLeader(state.players, selected)
    var showRooms by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoomChip(label = leader?.let { roomLabel(state.players, it) } ?: "No rooms", onClick = { showRooms = true }, enabled = leader != null)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                }
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
            LeaderPanel(repo, controller, leader, state.queues[leader.playerId], onError)
        }
    }

    if (showRooms && leader != null) {
        RoomsSheet(
            repo = repo,
            controller = controller,
            leader = leader,
            onError = onError,
            onDismiss = { showRooms = false },
        )
    }
}

@Composable
private fun RoomChip(label: String, onClick: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            painterResource(R.drawable.ic_expand_more), contentDescription = "Choose rooms",
            tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

@Composable
private fun LeaderPanel(
    repo: MusicRepository,
    controller: PlaybackController,
    leader: Player,
    queue: com.feerlessleadr.downbeat.ma.PlayerQueue?,
    onError: (String) -> Unit,
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

    Spacer(Modifier.height(16.dp))
    val artUrl = serverUrl?.let { ImageUrls.rewriteHost(it, np.imageUrl) ?: ImageUrls.forImage(it, np.imageRef, 800) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (artUrl != null) {
            AsyncImage(model = artUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_nav_playing), contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(Modifier.height(20.dp))
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

    Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(16.dp))
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
    Spacer(Modifier.height(24.dp))
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
