package com.feerlessleadr.downbeat

import android.app.Application
import com.feerlessleadr.downbeat.data.KeystoreSessionStore
import com.feerlessleadr.downbeat.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DownbeatApp : Application() {
    lateinit var repository: MusicRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // A websocket ping every 30 seconds notices a dead connection (a phone changing networks) quickly.
        val http = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
        repository = MusicRepository(
            http = http,
            store = KeystoreSessionStore(this),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        repository.start()
    }
}
