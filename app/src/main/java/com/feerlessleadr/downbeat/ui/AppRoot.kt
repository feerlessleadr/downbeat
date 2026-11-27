package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.Phase
import com.feerlessleadr.downbeat.data.PlaybackController
import kotlinx.coroutines.launch

@Composable
fun AppRoot(repo: MusicRepository, controller: PlaybackController) {
    val state by repo.state.collectAsStateWithLifecycle()
    when (state.phase) {
        Phase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        Phase.SignedOut -> SignInScreen(repo, state.message)
        Phase.Connecting, Phase.Online, Phase.Reconnecting -> MainScaffold(repo, controller)
    }
}

private val TABS = listOf(
    Triple("Playing", R.drawable.ic_nav_playing, 0),
    Triple("Library", R.drawable.ic_nav_library, 1),
    Triple("Search", R.drawable.ic_nav_search, 2),
)

@Composable
private fun MainScaffold(repo: MusicRepository, controller: PlaybackController) {
    val state by repo.state.collectAsStateWithLifecycle()
    val configured by repo.roomsConfigured.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    // First run: once the rooms are known, ask which ones to show (before anything else).
    val firstRun = !configured && state.players.isNotEmpty()
    if (firstRun || showPicker) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            RoomPickerScreen(repo, firstRun = firstRun, onDone = { showPicker = false })
        }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onError: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TABS.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(painterResource(icon), contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> PlayingScreen(
                    repo, controller, onError = onError,
                    onSignOut = { scope.launch { repo.signOutAndRevoke() } },
                    onChooseRooms = { showPicker = true },
                )
                1 -> ComingSoon("Library", "Browse albums, artists, playlists and radio. Coming next.")
                else -> ComingSoon("Search", "Search your whole library. Coming next.")
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
