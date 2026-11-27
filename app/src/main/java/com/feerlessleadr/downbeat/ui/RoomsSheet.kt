package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.RoomRow
import com.feerlessleadr.downbeat.ma.Player
import kotlinx.coroutines.launch

/**
 * "Play in": one list of rooms. A tick means the room is part of what plays now. Ticking adds a room to play
 * together, unticking removes it, and tapping a room that cannot join switches to it (docs/design.md).
 * The rows are recomputed from live state, so the sheet follows whatever the server reports after each tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsSheet(
    repo: MusicRepository,
    controller: PlaybackController,
    leader: Player,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The leader can change after a tap (a switch), so resolve it again from live state on every pass.
    val selected by repo.selectedRoomId.collectAsStateWithLifecycle()
    val liveLeader = RoomLogic.effectiveLeader(state.players, selected) ?: leader
    val rows = RoomLogic.rows(state.players, liveLeader)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Play in", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(rows, key = { it.player.playerId }) { row ->
                RoomRowItem(
                    row = row,
                    onTap = { scope.launch { controller.tapRoom(liveLeader, row.player.playerId)?.let(onError) } },
                    onVolume = { level -> scope.launch { controller.setRoomVolume(row.player.playerId, level)?.let(onError) } },
                )
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoomRowItem(row: RoomRow, onTap: () -> Unit, onVolume: (Int) -> Unit) {
    val room = row.player
    Column(
        Modifier.fillMaxWidth().clickable(enabled = row.enabled, onClick = onTap).padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.ticked, onCheckedChange = null, enabled = row.enabled)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    room.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (room.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A volume slider for each ticked room that can set its own volume (a static group's own slider sets the group).
        if (row.ticked && room.available && room.supports("volume_set")) {
            val serverVolume = room.volumeLevel?.toFloat() ?: 0f
            var volume by remember(room.playerId, serverVolume) { mutableFloatStateOf(serverVolume) }
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { onVolume(volume.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.padding(start = 36.dp),
            )
        }
    }
}
