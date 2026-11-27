package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.Phase
import com.feerlessleadr.downbeat.data.statusLabel
import com.feerlessleadr.downbeat.data.visibleRooms

@Composable
fun AppRoot(repo: MusicRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    when (state.phase) {
        Phase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        Phase.SignedOut -> SignInScreen(repo, state.message)
        Phase.Connecting, Phase.Online, Phase.Reconnecting -> RoomsPlaceholder(repo)
    }
}

/**
 * Temporary screen that proves the connection works: connection status and the list of rooms with live state.
 * Replaced by the real Playing / Library / Search tabs (docs/design.md).
 */
@Composable
private fun RoomsPlaceholder(repo: MusicRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    val rooms = visibleRooms(state.players)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Rooms", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { repo.signOut() }) { Text("Sign out") }
        }
        Text(
            when (state.phase) {
                Phase.Online -> "Connected"
                Phase.Reconnecting -> "Connection lost. Reconnecting…"
                else -> "Connecting…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.padding(top = 12.dp)) {
            items(rooms, key = { it.playerId }) { room ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium)
                    val volume = room.volumeLevel?.let { " · volume $it" }.orEmpty()
                    Text(
                        statusLabel(room) + volume,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
