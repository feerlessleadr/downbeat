package com.feerlessleadr.downbeat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.downbeat.data.LibrarySource
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

/** Full-screen pages that replace the app content (the Playing screen is a separate layer, see below). */
private enum class Overlay { None, RoomPicker, CustomizeBar, Appearance }

@Composable
private fun MainScaffold(repo: MusicRepository, controller: PlaybackController) {
    val state by repo.state.collectAsStateWithLifecycle()
    val configured by repo.roomsConfigured.collectAsStateWithLifecycle()
    val navItems by repo.navItems.collectAsStateWithLifecycle()
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var tabId by rememberSaveable { mutableStateOf("") }

    // Everything the tabs need to come back as they were left is created here, above any overlay, so opening the room
    // picker or the Playing page never wipes scroll positions or open pages.
    val source = remember(repo) { LibrarySource(repo) }
    val browseStates = remember { mutableMapOf<String, BrowseState>() }
    val search = remember { SearchState() }
    fun browseFor(id: String) = browseStates.getOrPut(id) { BrowseState() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // A blank message shows nothing (starting music is silent; the player bar is the sign).
    val onError: (String) -> Unit = { message -> if (message.isNotBlank()) scope.launch { snackbar.showSnackbar(message) } }
    val menu = AppMenuActions(
        onAppearance = { overlay = Overlay.Appearance },
        onChooseRooms = { overlay = Overlay.RoomPicker },
        onCustomizeBar = { overlay = Overlay.CustomizeBar },
        onSignOut = { scope.launch { repo.signOutAndRevoke() } },
    )

    // The chosen buttons, then Settings, which is always last and cannot be removed.
    val dests = navItems.mapNotNull { destinationFor(it) }.ifEmpty { listOfNotNull(destinationFor("library")) } + SETTINGS
    val tab = dests.firstOrNull { it.id == tabId } ?: dests.first()

    // First run: once the rooms are known, ask which ones to show before anything else.
    val firstRun = !configured && state.players.isNotEmpty()
    val shown = if (firstRun) Overlay.RoomPicker else overlay

    // The Playing screen opened from the player bar is a layer over the whole app that grows out of the player bar and
    // shrinks back into it. One value drives it: 0 = closed (just the bar), 1 = fully open. A tap on the bar, a swipe up
    // on it, the close arrow, Back and a swipe down all move this same value, so they all animate the same way.
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val sheet = remember { Animatable(0f) }
    var layerComposed by remember { mutableStateOf(false) }
    var miniBounds by remember { mutableStateOf<Rect?>(null) }
    // Where the layer grows from: the player bar, or a strip near the bottom if the bar is not showing.
    val originRect = miniBounds ?: Rect(0f, screenHeightPx - 220f, screenWidthPx, screenHeightPx - 100f)
    var swipeTotal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(nowPlayingOpen) {
        if (nowPlayingOpen) {
            layerComposed = true
            sheet.animateTo(1f, tween(320))
        } else if (layerComposed) {
            sheet.animateTo(0f, tween(260))
            layerComposed = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            snackbarHost = { if (!nowPlayingOpen) SnackbarHost(snackbar) },
            bottomBar = {
                if (shown == Overlay.None) {
                    Column {
                        if (tab.id != "playing") {
                            MiniPlayer(
                                repo, controller, onOpen = { nowPlayingOpen = true }, onError = onError,
                                onBounds = { miniBounds = it },
                                onSwipeStart = { layerComposed = true; swipeTotal = 0f },
                                onSwipeDelta = { dy ->
                                    swipeTotal -= dy
                                    scope.launch { sheet.snapTo((swipeTotal / originRect.top.coerceAtLeast(1f)).coerceIn(0f, 1f)) }
                                },
                                onSwipeEnd = {
                                    scope.launch {
                                        if (sheet.value > 0.2f) {
                                            nowPlayingOpen = true
                                        } else {
                                            sheet.animateTo(0f, tween(180))
                                            if (!nowPlayingOpen) layerComposed = false
                                        }
                                    }
                                },
                            )
                        }
                        NavigationBar {
                            dests.forEach { d ->
                                NavigationBarItem(
                                    selected = tab.id == d.id,
                                    onClick = {
                                        if (tab.id != d.id) {
                                            tabId = d.id
                                        } else if (d.id == "search") {
                                            search.browse.resetOrRefresh()
                                        } else if (d.id != "rooms" && d.id != "playing" && d.id != "settings") {
                                            // Tapping the selected tab: close open pages first, then reload the list.
                                            browseFor(d.id).resetOrRefresh()
                                        }
                                    },
                                    icon = { Icon(painterResource(d.icon), contentDescription = null) },
                                    label = { Text(d.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                when (shown) {
                    Overlay.RoomPicker -> RoomPickerScreen(repo, firstRun = firstRun, onDone = { overlay = Overlay.None })
                    Overlay.Appearance -> AppearanceScreen(repo, onBack = { overlay = Overlay.None })
                    Overlay.CustomizeBar -> CustomizeBarScreen(
                        current = navItems,
                        onSave = { ids -> repo.setNavItems(ids); overlay = Overlay.None },
                        onCancel = { overlay = Overlay.None },
                    )
                    Overlay.None -> when {
                        tab.id == "search" -> SearchScreen(repo, source, controller, search, onError)
                        tab.id == "settings" -> SettingsScreen(menu, repo.serverUrl)
                        tab.id == "rooms" -> RoomsPage(repo, controller, onError)
                        tab.id == "playing" -> PlayingScreen(repo, controller, onError = onError)
                        else -> LibraryScreen(repo, source, controller, browseFor(tab.id), fixedType = tab.libraryType, onMessage = onError)
                    }
                }
            }
        }

        if (layerComposed) {
            val dark = Color(0xFF121016)
            val closeDistance = screenHeightPx / 8f
            val travel = originRect.top.coerceAtLeast(1f)
            // The swipe-down handling sits outside the moving layer, so touch positions do not move with it. The top edge
            // of the screen follows the finger down toward the player bar; letting go after about an eighth of the screen
            // height completes the close.
            Box(
                Modifier.fillMaxSize().pointerInput(travel) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            scope.launch {
                                if (total > closeDistance) {
                                    sheet.animateTo(0f, tween(200))
                                    nowPlayingOpen = false
                                } else {
                                    sheet.animateTo(1f, tween(180))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { sheet.animateTo(1f, tween(180)) } },
                    ) { _, dy ->
                        total = (total + dy).coerceAtLeast(0f)
                        scope.launch { sheet.snapTo((1f - total / travel).coerceIn(0f, 1f)) }
                    }
                },
            ) {
                // The whole screen is one rigid, full-size piece that only moves up and down: its top edge travels between
                // the top of the player bar (closed) and the top of the window (open). It is never scaled, clipped or
                // wiped, so the picture, title and controls all keep their size and move together, and under a finger it
                // simply follows the finger. It fades in over the first moments so no dark slab appears over the bar.
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val p = sheet.value
                            translationY = lerp(originRect.top, 0f, p)
                            alpha = (p / 0.12f).coerceIn(0f, 1f)
                        }
                        .background(dark),
                ) {
                    PlayingScreen(repo, controller, onError = onError, onClose = { nowPlayingOpen = false }, edgeToEdge = true)
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
                }
            }
        }
    }
}
