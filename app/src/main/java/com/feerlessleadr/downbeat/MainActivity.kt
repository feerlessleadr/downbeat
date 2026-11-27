package com.feerlessleadr.downbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.feerlessleadr.downbeat.ui.AppRoot
import com.feerlessleadr.downbeat.ui.DownbeatTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        // Back on screen: check the connection now instead of waiting for the keepalive to notice a dead one.
        (application as DownbeatApp).repository.onForeground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DownbeatApp
        val repository = app.repository
        val controller = app.controller
        setContent {
            DownbeatTheme(repository) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(repository, controller)
                }
            }
        }
    }
}
