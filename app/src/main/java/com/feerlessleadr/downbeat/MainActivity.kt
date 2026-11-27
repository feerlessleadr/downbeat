package com.feerlessleadr.downbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import com.feerlessleadr.downbeat.ui.AppRoot

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
            // Placeholder colors. The real palette is a later design task (docs/design.md, open questions).
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(repository, controller)
                }
            }
        }
    }
}
