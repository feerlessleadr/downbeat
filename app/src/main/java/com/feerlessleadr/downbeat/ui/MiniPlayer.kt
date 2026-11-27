package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
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
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.controlsFor
import com.feerlessleadr.downbeat.data.nowPlaying
import com.feerlessleadr.downbeat.ma.ImageUrls
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The floating bar above the bottom bar, shown on every screen while the chosen rooms are playing or paused. Tapping
 * it opens the full Playing screen. It shows nothing when nothing has been started.
 */
@Composable
fun MiniPlayer(
    repo: MusicRepository,
    controller: PlaybackController,
    onOpen: () -> Unit,
    onError: (String) -> Unit,
    /** A swipe up on the bar pulls the Playing screen up with the finger (see AppRoot). */
    /** Called with the bar's bounds in the window, so the Playing screen can grow out of it. */
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onSwipeStart: () -> Unit = {},
    onSwipeDelta: (Float) -> Unit = {},
    onSwipeEnd: () -> Unit = {},
) {
    val state by repo.state.collectAsStateWithLifecycle()
    val selected by repo.selectedRoomId.collectAsStateWithLifecycle()
    val hidden by repo.hiddenRooms.collectAsStateWithLifecycle()
    val leader = RoomLogic.effectiveLeader(state.players, selected, hidden) ?: return
    if (leader.playbackState != "playing" && leader.playbackState != "paused") return

    val playing = leader.playbackState == "playing"
    val now by produceState(System.currentTimeMillis() / 1000.0) {
        while (true) {
            value = System.currentTimeMillis() / 1000.0
            delay(1000)
        }
    }
    val np = nowPlaying(leader, state.queues[leader.playerId], now)
    if (!np.hasMedia) return
    val scope = rememberCoroutineScope()
    val serverUrl = repo.serverUrl
    val artUrl = serverUrl?.let { ImageUrls.rewriteHost(it, np.imageUrl) ?: ImageUrls.forImage(it, np.imageRef, 150) }
    val controls = controlsFor(leader)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { onSwipeStart() },
                onDragEnd = { onSwipeEnd() },
                onDragCancel = { onSwipeEnd() },
            ) { _, dy -> onSwipeDelta(dy) }
        },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Column {
            Row(
                Modifier.clickable(onClick = onOpen).padding(start = 8.dp, top = 8.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artUrl != null) {
                        AsyncImage(model = artUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(painterResource(R.drawable.ic_nav_playing), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(np.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        roomLabel(state.players, leader), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (controls.playPause) {
                    IconButton(onClick = { scope.launch { controller.playPause(leader)?.let(onError) } }) {
                        Icon(
                            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (playing) "Pause" else "Play",
                        )
                    }
                }
                IconButton(onClick = { scope.launch { controller.stop(leader)?.let(onError) } }) {
                    Icon(painterResource(R.drawable.ic_stop), contentDescription = "Stop")
                }
                IconButton(onClick = { scope.launch { controller.next(leader)?.let(onError) } }) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next")
                }
            }
            val duration = np.durationSeconds
            if (duration != null && duration > 0) {
                LinearProgressIndicator(
                    progress = { (np.positionSeconds / duration).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
