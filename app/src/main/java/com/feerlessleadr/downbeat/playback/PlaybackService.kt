package com.feerlessleadr.downbeat.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.feerlessleadr.downbeat.DownbeatApp
import com.feerlessleadr.downbeat.MainActivity
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.ma.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lock screen and notification controls. Downbeat is a remote, not a player: this keeps an Android media session and a
 * media notification in step with the rooms chosen in the app, and turns their buttons (and headset or Bluetooth media
 * buttons) into commands for the server. It runs as a foreground service only while something plays or is paused, and
 * stops itself shortly after playback ends, so idle battery use stays near zero.
 */
class PlaybackService : Service() {
    private lateinit var repo: MusicRepository
    private lateinit var playbackController: PlaybackController
    private lateinit var session: MediaSession
    private lateinit var manager: NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var idleJob: Job? = null
    private var art: Bitmap? = null
    private var artFor: String? = null
    private var last = SessionSnapshot.NONE

    override fun onCreate() {
        super.onCreate()
        val app = application as DownbeatApp
        repo = app.repository
        playbackController = app.controller
        manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback controls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows what is playing and lets you control it from the lock screen"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        session = MediaSession(this, "Downbeat").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = command("play", "session") { playbackController.resume(it) }
                override fun onPause() = command("pause", "session") { playbackController.pause(it) }
                override fun onSkipToNext() = command("next", "session") { playbackController.next(it) }
                override fun onSkipToPrevious() = command("previous", "session") { playbackController.previous(it) }
                override fun onSeekTo(pos: Long) = command("seek", "session") { playbackController.seek(it, (pos / 1000).toInt()) }
                override fun onStop() = command("stop", "session") { playbackController.stop(it) }
            })
            isActive = true
        }
        running = true
        try {
            startAsForeground(buildNotification(SessionSnapshot.NONE))
        } catch (e: Exception) {
            // Not allowed to start in the foreground right now (the app is in the background): give up quietly.
            stopSelf()
            return
        }
        scope.launch {
            combine(repo.state, repo.selectedRoomId, repo.hiddenRooms, repo.mediaControls) { _, _, _, enabled -> enabled }
                .collect { enabled -> if (enabled) refresh() else stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> command("play", "notification") { playbackController.resume(it) }
            ACTION_PAUSE -> command("pause", "notification") { playbackController.pause(it) }
            ACTION_PLAY_PAUSE -> command("playpause", "notification") { playbackController.playPause(it) }
            ACTION_NEXT -> command("next", "notification") { playbackController.next(it) }
            ACTION_PREVIOUS -> command("previous", "notification") { playbackController.previous(it) }
            ACTION_STOP -> {
                command("stop", "notification") { playbackController.stop(it) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_DISMISS -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        idleJob?.cancel()
        scope.cancel()
        runCatching { session.isActive = false; session.release() }
        super.onDestroy()
    }

    private val debouncer = CommandDebouncer { SystemClock.elapsedRealtime() }

    /**
     * Runs a server command on the chosen rooms (the leader of the set that is playing). A repeat of the same command
     * within a moment is dropped (see [CommandDebouncer]); [source] is only for the log. Errors are ignored here.
     */
    private fun command(name: String, source: String, block: suspend (Player) -> String?) {
        if (!debouncer.accept(name)) {
            Log.d(TAG, "media command $name from $source ignored (repeat)")
            return
        }
        Log.d(TAG, "media command $name from $source")
        val leader = RoomLogic.effectiveLeader(repo.state.value.players, repo.selectedRoomId.value, repo.hiddenRooms.value) ?: return
        scope.launch { block(leader) }
    }

    private fun currentSnapshot(): SessionSnapshot = sessionSnapshot(
        repo.state.value.players, repo.selectedRoomId.value, repo.hiddenRooms.value,
        repo.state.value.queues, repo.serverUrl, System.currentTimeMillis() / 1000.0,
    )

    /** Brings the session and notification in line with the server's state. */
    private fun refresh() {
        val snap = currentSnapshot()
        last = snap
        if (snap.state == SessionState.None) {
            // Nothing is playing: show that, and end the service after a short wait (a gap between songs is not the end).
            if (idleJob == null) idleJob = scope.launch { delay(IDLE_STOP_MS); stopSelf() }
        } else {
            idleJob?.cancel()
            idleJob = null
        }
        if (snap.imageUrl != artFor) {
            artFor = snap.imageUrl
            art = null
            snap.imageUrl?.let { url -> scope.launch { loadArt(url) } }
        }
        updateSession(snap)
        manager.notify(NOTIFICATION_ID, buildNotification(snap))
    }

    private suspend fun loadArt(url: String) {
        val bitmap = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(this@PlaybackService).data(url).size(Size(512, 512)).allowHardware(false).build()
            val result = SingletonImageLoader.get(this@PlaybackService).execute(request)
            if (result is SuccessResult) result.image.toBitmap() else null
        }
        // Only keep it if the song has not changed again meanwhile.
        if (bitmap != null && artFor == url) {
            art = bitmap
            updateSession(last)
            manager.notify(NOTIFICATION_ID, buildNotification(last))
        }
    }

    private fun updateSession(snap: SessionSnapshot) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snap.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, snap.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, snap.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, snap.durationMs)
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        val state = when (snap.state) {
            SessionState.Playing -> PlaybackState.STATE_PLAYING
            SessionState.Paused -> PlaybackState.STATE_PAUSED
            SessionState.None -> PlaybackState.STATE_STOPPED
        }
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, snap.positionMs, if (snap.state == SessionState.Playing) 1f else 0f, SystemClock.elapsedRealtime())
                .build(),
        )
    }

    private fun buildNotification(snap: SessionSnapshot): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playing = snap.state == SessionState.Playing
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav_playing)
            .setContentTitle(snap.title.ifBlank { "Downbeat" })
            .setContentText(snap.artist.ifBlank { snap.album })
            .setSubText(snap.roomText.ifBlank { null })
            .setContentIntent(open)
            .setDeleteIntent(servicePending(ACTION_DISMISS, 9))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .addAction(action(R.drawable.ic_skip_previous, "Previous", ACTION_PREVIOUS, 1))
            .addAction(
                if (playing) action(R.drawable.ic_pause, "Pause", ACTION_PAUSE, 2)
                else action(R.drawable.ic_play, "Play", ACTION_PLAY, 2),
            )
            .addAction(action(R.drawable.ic_skip_next, "Next", ACTION_NEXT, 3))
            .addAction(action(R.drawable.ic_stop, "Stop", ACTION_STOP, 4))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        art?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun action(icon: Int, title: String, action: String, code: Int): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, servicePending(action, code)).build()

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 41
        private const val IDLE_STOP_MS = 30_000L
        const val ACTION_PLAY_PAUSE = "com.feerlessleadr.downbeat.PLAY_PAUSE"
        const val ACTION_PLAY = "com.feerlessleadr.downbeat.PLAY"
        const val ACTION_PAUSE = "com.feerlessleadr.downbeat.PAUSE"
        private const val TAG = "DownbeatMedia"
        const val ACTION_NEXT = "com.feerlessleadr.downbeat.NEXT"
        const val ACTION_PREVIOUS = "com.feerlessleadr.downbeat.PREVIOUS"
        const val ACTION_STOP = "com.feerlessleadr.downbeat.STOP"
        const val ACTION_DISMISS = "com.feerlessleadr.downbeat.DISMISS"

        /** True while the service is alive; read by [PlaybackServiceStarter]. */
        @Volatile var running = false

        /** Starts the service in the foreground. Only allowed while the app is on screen; failures are ignored. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                // The system refuses a foreground start from the background; nothing to do.
            }
        }
    }
}
