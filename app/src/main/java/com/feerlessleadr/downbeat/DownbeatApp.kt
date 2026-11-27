package com.feerlessleadr.downbeat

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.feerlessleadr.downbeat.data.KeystoreSessionStore
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.PlaybackController
import com.feerlessleadr.downbeat.data.SharedAppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DownbeatApp : Application(), SingletonImageLoader.Factory {
    private lateinit var http: OkHttpClient

    lateinit var repository: MusicRepository
        private set
    lateinit var controller: PlaybackController
        private set

    override fun onCreate() {
        super.onCreate()
        // A websocket ping every 30 seconds notices a dead connection (a phone changing networks) quickly.
        http = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
        repository = MusicRepository(
            http = http,
            store = KeystoreSessionStore(this),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            prefs = SharedAppPrefs(this),
        )
        controller = PlaybackController(repository)
        repository.start()
        // A network coming back (wifi on, switching networks) means the old socket is probably dead: check it now.
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = repository.onForeground()
            },
        )
    }

    /**
     * Artwork loader. Requests to the Music Assistant server carry the sign-in token (the server may require it for
     * images; not yet confirmed), and the token is never sent to any other host.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val images = http.newBuilder()
            // The server may fetch and resize a picture from the internet on first use, which can take a while.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val bearer = repository.bearerFor(request.url)
                chain.proceed(if (bearer == null) request else request.newBuilder().header("Authorization", bearer).build())
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { images })) }
            .build()
    }
}
