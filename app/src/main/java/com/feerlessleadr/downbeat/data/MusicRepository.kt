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
) {
    private val _state = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = _state.asStateFlow()

    @Volatile private var currentApi: MaApi? = null

    /** The command API of the live connection, or null while offline. */
    val api: MaApi? get() = currentApi

    private var loop: Job? = null

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
        // Swap the login token (renewed by use, 30 days) for a long-lived one (1 year) when the server allows it.
        val token = createLongLivedOrNull(url, sessionToken) ?: sessionToken
        val session = Session(url, token)
        store.save(session)
        connect(session)
        return SignInResult.Success
    }

    fun signOut(message: String? = null) {
        loop?.cancel()
        currentApi = null
        store.clear()
        _state.value = RepoState(phase = Phase.SignedOut, message = message)
    }

    private suspend fun createLongLivedOrNull(url: String, sessionToken: String): String? {
        val client = MaClient(http)
        return try {
            client.connect(url, sessionToken)
            val st = withTimeoutOrNull(15_000) { client.state.first { it !is MaState.Idle && it !is MaState.Connecting } }
            if (st is MaState.Ready) MaApi(client).createLongLivedToken(tokenName) else null
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
        _state.value = RepoState(phase = Phase.Connecting, serverUrl = session.serverUrl)
        loop = scope.launch { runConnectionLoop(session) }
    }

    private suspend fun runConnectionLoop(session: Session) {
        var attempt = 0
        while (scope.isActive) {
            val client = MaClient(http)
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
                        end = client.state.first { it is MaState.Disconnected || it is MaState.AuthFailed }
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
            delay(backoffMs[attempt.coerceAtMost(backoffMs.lastIndex)])
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
    }

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
