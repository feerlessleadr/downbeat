package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.visibleRooms

/**
 * "Which rooms do you want to see?" Shown once after the first connection and again from the menu on the Playing tab.
 * The first time, TVs and receivers (players that only have volume controls) start unticked.
 */
@Composable
fun RoomPickerScreen(repo: MusicRepository, firstRun: Boolean, onDone: () -> Unit) {
    val state by repo.state.collectAsStateWithLifecycle()
    val hidden by repo.hiddenRooms.collectAsStateWithLifecycle()
    val rooms = visibleRooms(state.players)
    // Start from the saved choice, or from the suggestion the first time. Rooms new to the server start ticked.
    var unticked by remember {
        mutableStateOf(if (firstRun) RoomLogic.suggestedHidden(state.players) else hidden)
    }
    val tickedCount = rooms.count { it.playerId !in unticked }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            if (firstRun) "Choose your rooms" else "Rooms to show",
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            if (firstRun) "Untick anything you won't play music in, such as TVs. You can change this later from the menu on the Playing tab."
            else "Untick the rooms you don't want to see.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(rooms, key = { it.playerId }) { room ->
                val ticked = room.playerId !in unticked
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { unticked = if (ticked) unticked + room.playerId else unticked - room.playerId }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ticked, onCheckedChange = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(room.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                room.type == "group" -> "Group"
                                !room.supports("pause") -> "Volume only (TV or receiver)"
                                else -> "Speaker"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!firstRun) TextButton(onClick = onDone) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    repo.setHiddenRooms(rooms.map { it.playerId }.filter { it in unticked }.toSet() + (hidden - rooms.map { it.playerId }.toSet()))
                    onDone()
                },
                enabled = tickedCount > 0,
            ) { Text("Done") }
        }
    }
}
