package com.feerlessleadr.downbeat.playback

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.feerlessleadr.downbeat.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Starts [PlaybackService] when the chosen rooms start playing while the app is on screen. Android only allows a
 * foreground service to start from the foreground, so playback begun from another app while Downbeat is closed shows
 * no lock screen controls until Downbeat is opened. Once running, the service keeps going in the background.
 */
class PlaybackServiceStarter(
    private val app: Application,
    private val repo: MusicRepository,
    scope: CoroutineScope,
) {
    @Volatile private var startedActivities = 0

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                maybeStart()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        scope.launch {
            combine(repo.state, repo.selectedRoomId, repo.hiddenRooms, repo.mediaControls) { _, _, _, _ -> }
                .collect { maybeStart() }
        }
    }

    private fun maybeStart() {
        if (startedActivities <= 0 || PlaybackService.running || !repo.mediaControls.value) return
        val state = repo.state.value
        val snap = sessionSnapshot(
            state.players, repo.selectedRoomId.value, repo.hiddenRooms.value, state.queues, repo.serverUrl,
            System.currentTimeMillis() / 1000.0,
        )
        if (snap.state == SessionState.Playing) PlaybackService.start(app)
    }
}
