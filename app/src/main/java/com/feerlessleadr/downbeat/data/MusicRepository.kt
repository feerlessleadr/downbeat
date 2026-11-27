package com.feerlessleadr.downbeat.data

import com.feerlessleadr.downbeat.ma.LoginResult
import com.feerlessleadr.downbeat.ma.MaApi
import com.feerlessleadr.downbeat.ma.MaAuth
import com.feerlessleadr.downbeat.ma.MaClient
import com.feerlessleadr.downbeat.ma.MaEvent
import com.feerlessleadr.downbeat.ma.MaJson
import com.feerlessleadr.downbeat.ma.MaState
import com.feerlessleadr.downbeat.ma.Player
import com.feerlessleadr.downbeat.ma.PlayerQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

enum class Phase {
    /** Reading the stored session. */
    Loading,
    SignedOut,
    /** Connecting for the first time in this run. */
    Connecting,
    Online,
    /** The connection dropped; the last known rooms are still in the state while retrying. */
    Reconnecting,
}

data class RepoState(
    val phase: Phase = Phase.Loading,
    val serverUrl: String? = null,
    val players: List<Player> = emptyList(),
    /** Keyed by queue id, which equals the owning player's id. */
    val queues: Map<String, PlayerQueue> = emptyMap(),
    /** A short sentence to show on the sign-in screen (why the user is signed out). */
    val message: String? = null,
)

sealed interface SignInResult {
    data object Success : SignInResult
    data object InvalidCredentials : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Owns the one live connection to Music Assistant and exposes what the screens need as state.
 *
 * - [start] reads the stored session and connects.
 * - [signIn] logs in over HTTPS, makes a long-lived token, stores it, and connects.
 * - A dropped connection retries with a growing delay. A refused token ends the session and signs the user out.
 * - Player events update [RepoState.players] directly. A queue is re-read when its player's state or media changes,
 *   because queue events were not seen in the sample runs (see docs/music-assistant-api-verified.md).
 */
class MusicRepository(
    private val http: OkHttpClient,
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val requireHttps: Boolean = true,
    private val backoffMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000),
    private val tokenName: String = "Downbeat",
    private val prefs: AppPrefs = MemoryAppPrefs(),
    private val queuePollMs: Long = 10_000,
) {
    private val _state = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = _state.asStateFlow()

    private val _selectedRoomId = MutableStateFlow(prefs.lastRoomId)

    /** The room the person chose (may be a follower or an unavailable room; see RoomLogic.effectiveLeader). */
    val selectedRoomId: StateFlow<String?> = _selectedRoomId.asStateFlow()

    @Volatile private var currentSession: Session? = null

    /**
     * The Authorization header value for requests to the server's own address (artwork), or null for any other host.
     * The token is only ever sent to the server the person signed in to.
     */
    fun bearerFor(url: okhttp3.HttpUrl): String? {
        val s = currentSession ?: return null
        val host = s.serverUrl.toHttpUrlOrNull()?.host ?: return null
        return if (url.host == host) "Bearer ${s.token}" else null
    }

    /** The name of the access token this phone made when signing in, or null for an older sign-in. */
    val thisTokenName: String? get() = currentSession?.tokenName

    /** The address the person typed, for building artwork URLs. */
    val serverUrl: String? get() = currentSession?.serverUrl

    fun selectRoom(id: String) {
        _selectedRoomId.value = id
        prefs.lastRoomId = id
    }

    private val _hiddenRooms = MutableStateFlow(prefs.hiddenRoomIds)

    /** Rooms the person chose not to see (TVs and receivers by default after the first-run picker). */
    val hiddenRooms: StateFlow<Set<String>> = _hiddenRooms.asStateFlow()

    private val _navItems = MutableStateFlow(NavConfig.sanitize(prefs.navItems))

    /** The bottom bar destinations the person chose, in order. */
    val navItems: StateFlow<List<String>> = _navItems.asStateFlow()

    fun setNavItems(ids: List<String>) {
        val clean = NavConfig.sanitize(ids)
        prefs.navItems = clean
        _navItems.value = clean
    }

    private val _theme = MutableStateFlow(ThemeSettings(Themes.sanitizeMode(prefs.themeMode), Themes.sanitizePalette(prefs.themePalette), prefs.themeAccentMusic, prefs.themeCustomColor or (0xFF shl 24)))

    /** The colour choices (light, dark or system, and the palette). */
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    fun setThemeMode(mode: String) {
        val clean = Themes.sanitizeMode(mode)
        prefs.themeMode = clean
        _theme.value = _theme.value.copy(mode = clean)
    }

    fun setThemePalette(palette: String) {
        val clean = Themes.sanitizePalette(palette)
        prefs.themePalette = clean
        _theme.value = _theme.value.copy(palette = clean)
    }

    fun setAccentFollowsMusic(on: Boolean) {
        prefs.themeAccentMusic = on
        _theme.value = _theme.value.copy(accentFollowsMusic = on)
    }

    /** Sets the picked colour and makes it the base palette, so the app follows the picker while it is dragged. */
    fun setCustomColor(argb: Int) {
        val opaque = argb or (0xFF shl 24)
        prefs.themeCustomColor = opaque
        prefs.themePalette = Themes.CUSTOM
        _theme.value = _theme.value.copy(palette = Themes.CUSTOM, customColor = opaque)
    }

    private val _mediaControls = MutableStateFlow(prefs.mediaControls)

    /** True when the lock screen and notification controls are on. */
    val mediaControls: StateFlow<Boolean> = _mediaControls.asStateFlow()

    fun setMediaControls(on: Boolean) {
        prefs.mediaControls = on
        _mediaControls.value = on
    }

    private val _roomsConfigured = MutableStateFlow(prefs.roomsConfigured)

    /** False until the person has been through the room picker once. */
    val roomsConfigured: StateFlow<Boolean> = _roomsConfigured.asStateFlow()

    fun setHiddenRooms(ids: Set<String>) {
        prefs.hiddenRoomIds = ids
        prefs.roomsConfigured = true
        _hiddenRooms.value = ids
        _roomsConfigured.value = true
    }

    @Volatile private var currentApi: MaApi? = null

    /** The command API of the live connection, or null while offline. */
    val api: MaApi? get() = currentApi

    private var loop: Job? = null

    /** Wakes the reconnect wait early (see [onForeground]). */
    private val nudges = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var currentClient: MaClient? = null

    fun start() {
        val session = store.load()
        if (session == null) {
            _state.value = RepoState(phase = Phase.SignedOut)
        } else {
            connect(session)
        }
    }

    suspend fun signIn(serverUrl: String, username: String, password: String): SignInResult {
        val base = serverUrl.trim().toHttpUrlOrNull()
            ?: return SignInResult.Failed("Enter the address of your Music Assistant server, for example https://music.example.com.")
        if (requireHttps && !base.isHttps) {
            return SignInResult.Failed("Use an https:// address. Downbeat doesn't send passwords over plain http.")
        }
        val url = normalize(serverUrl)
        val sessionToken = when (val r = MaAuth.login(http, url, username, password)) {
            is LoginResult.Success -> r.token
            LoginResult.InvalidCredentials -> return SignInResult.InvalidCredentials
            is LoginResult.Failed -> return SignInResult.Failed(r.message)
        }
        // Swap the login token (renewed by use, 30 days) for a long-lived one (years, not renewed) when the server allows it.
        // A unique name per sign-in lets sign-out revoke exactly this install's token, not another phone's.
        val name = "$tokenName ${java.util.UUID.randomUUID().toString().take(6)}"
        val longLived = createLongLivedOrNull(url, sessionToken, name)
        val session = Session(url, longLived ?: sessionToken, tokenName = if (longLived != null) name else null)
        store.save(session)
        connect(session)
        return SignInResult.Success
    }

    fun signOut(message: String? = null) {
        loop?.cancel()
        currentApi = null
        currentSession = null
        store.clear()
        _providerAdmin.value = null
        _state.value = RepoState(phase = Phase.SignedOut, message = message)
    }

    /**
     * Signs out and, when the server is reachable, revokes this install's token there first (best effort, at most a few
     * seconds). A session without a known token name (an older sign-in) is only forgotten on the phone.
     */
    suspend fun signOutAndRevoke() {
        val name = currentSession?.tokenName
        val api = currentApi
        if (name != null && api != null) {
            try {
                withTimeoutOrNull(4_000) {
                    api.tokens().filter { it.name == name }.forEach { api.revokeToken(it.tokenId) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or refused: the token stays valid on the server until it expires or is deleted in its UI.
            }
        }
        signOut()
    }

    private suspend fun createLongLivedOrNull(url: String, sessionToken: String, name: String): String? {
        val client = MaClient(http)
        return try {
            client.connect(url, sessionToken)
            val st = withTimeoutOrNull(15_000) { client.state.first { it !is MaState.Idle && it !is MaState.Connecting } }
            if (st is MaState.Ready) MaApi(client).createLongLivedToken(name) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }

    private fun connect(session: Session) {
        loop?.cancel()
        currentSession = session
        _state.value = RepoState(phase = Phase.Connecting, serverUrl = session.serverUrl)
        loop = scope.launch { runConnectionLoop(session) }
    }

    private suspend fun runConnectionLoop(session: Session) {
        var attempt = 0
        while (scope.isActive) {
            val client = MaClient(http)
            currentClient = client
            try {
                val outcome = coroutineScope {
                    val api = MaApi(client)
                    // Collect before connecting so no event is missed.
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        client.events.collect { event -> handleEvent(api, event) }
                    }
                    client.connect(session.serverUrl, session.token)
                    var end: MaState = client.state.first { it !is MaState.Idle && it !is MaState.Connecting }
                    if (end is MaState.Ready) {
                        currentApi = api
                        attempt = 0
                        loadSnapshot(api)
                        // Safety net against drift: queue events are not relied on, so re-read the queue of every
                        // playing room now and then (the play position and shuffle/repeat come from the queue).
                        val poll = launch {
                            while (true) {
                                delay(queuePollMs)
                                _state.value.players.filter { it.playbackState == "playing" }
                                    .forEach { refreshQueue(api, it.playerId) }
                            }
                        }
                        end = client.state.first { it is MaState.Disconnected || it is MaState.AuthFailed }
                        poll.cancel()
                    }
                    collector.cancel()
                    end
                }
                currentApi = null
                if (outcome is MaState.AuthFailed) {
                    signOut("Your sign-in expired. Sign in again.")
                    return
                }
            } catch (e: CancellationException) {
                currentApi = null
                throw e
            } catch (e: Exception) {
                currentApi = null
            } finally {
                client.close()
            }
            _state.update { it.copy(phase = Phase.Reconnecting) }
            // Wait the backoff time, or less if the app is nudged (foreground, network back).
            withTimeoutOrNull(backoffMs[attempt.coerceAtMost(backoffMs.lastIndex)]) { nudges.receive() }
            attempt++
        }
    }

    private suspend fun loadSnapshot(api: MaApi) {
        val players = api.players()
        val queues = api.queues()
        _state.update {
            it.copy(
                phase = Phase.Online,
                players = players,
                queues = queues.associateBy { q -> q.queueId },
                message = null,
            )
        }
        // Can this account manage providers? Asked by trying the read, not by guessing role names: a refusal from the
        // server means no. Any other failure (a dropped connection, an unexpected answer) keeps the last known answer.
        _providerAdmin.value = try {
            api.providerConfigs()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.feerlessleadr.downbeat.ma.MaException) {
            false
        } catch (e: Exception) {
            _providerAdmin.value
        }
    }

    private val _providerAdmin = MutableStateFlow<Boolean?>(null)

    /** True when this account may manage music sources and players, false when it may not, null until known. */
    val providerAdmin: StateFlow<Boolean?> = _providerAdmin.asStateFlow()

    private suspend fun handleEvent(api: MaApi, event: MaEvent) {
        val data = event.data
        when (event.event) {
            "player_updated", "player_added" -> {
                val player = decode(Player.serializer(), data) ?: return
                var refreshQueue = false
                _state.update { s ->
                    val old = s.players.firstOrNull { it.playerId == player.playerId }
                    refreshQueue = event.event == "player_updated" && old != null && mediaChanged(old, player)
                    val list = if (old == null) s.players + player else s.players.map { if (it.playerId == player.playerId) player else it }
                    s.copy(players = list)
                }
                if (refreshQueue) refreshQueue(api, player.playerId)
            }
            "player_removed" -> {
                val id = event.objectId ?: return
                _state.update { s -> s.copy(players = s.players.filterNot { it.playerId == id }, queues = s.queues - id) }
            }
            "queue_added", "queue_updated" -> {
                val queue = decode(PlayerQueue.serializer(), data)
                if (queue != null) {
                    _state.update { s -> s.copy(queues = s.queues + (queue.queueId to queue)) }
                } else {
                    event.objectId?.let { refreshQueue(api, it) }
                }
            }
            "queue_removed" -> event.objectId?.let { id -> _state.update { s -> s.copy(queues = s.queues - id) } }
            // queue_time_updated is ignored on purpose: screens work out the position from elapsed_time and
            // elapsed_time_last_updated instead of waiting for ticks.
        }
    }

    private fun mediaChanged(old: Player, new: Player): Boolean =
        old.playbackState != new.playbackState ||
            old.currentMedia?.queueItemId != new.currentMedia?.queueItemId ||
            old.currentMedia?.uri != new.currentMedia?.uri

    private val artistImages = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<com.feerlessleadr.downbeat.ma.MediaItemImage>>()

    /**
     * A picture of an artist for the Playing screen background: the "thumb" image if there is one, else the first
     * image, else null (the caller then uses the album art). Remembered per artist; a failed lookup is retried later.
     */
    suspend fun artistImage(itemId: String, provider: String): com.feerlessleadr.downbeat.ma.MediaItemImage? {
        val key = "$provider/$itemId"
        artistImages[key]?.let { return it.orElse(null) }
        val api = currentApi ?: return null
        return try {
            val images = api.artist(itemId, provider)?.metadata?.images.orEmpty()
            val pick = images.firstOrNull { it.type == "thumb" } ?: images.firstOrNull()
            artistImages[key] = java.util.Optional.ofNullable(pick)
            pick
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Re-reads one queue now (used after a command that changes it, since queue events are not relied on). */
    suspend fun refreshQueueNow(queueId: String) {
        currentApi?.let { refreshQueue(it, queueId) }
    }

    /**
     * Call when the app comes to the foreground or the network changes. While reconnecting it skips the wait. While
     * online it checks the connection with a quick request (a socket that slept with the screen off can look alive but
     * be dead) and reconnects at once if there is no answer, otherwise it refreshes the rooms.
     */
    fun onForeground() {
        scope.launch {
            val api = currentApi
            if (api == null) {
                nudges.trySend(Unit)
                return@launch
            }
            val ok = try {
                withTimeoutOrNull(3_000) {
                    loadSnapshot(api)
                    true
                } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                currentClient?.close()
                nudges.trySend(Unit)
            }
        }
    }

    private suspend fun refreshQueue(api: MaApi, queueId: String) {
        try {
            val queue = api.queue(queueId) ?: return
            _state.update { s -> s.copy(queues = s.queues + (queue.queueId to queue)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next player event or reconnect refreshes it again.
        }
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, data: kotlinx.serialization.json.JsonElement?): T? =
        try {
            if (data == null) null else MaJson.decodeFromJsonElement(serializer, data)
        } catch (e: Exception) {
            null
        }

    private fun normalize(serverUrl: String): String {
        val base = serverUrl.trim().toHttpUrlOrNull()!!
        return base.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
    }
}
