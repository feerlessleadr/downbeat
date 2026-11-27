package com.feerlessleadr.downbeat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feerlessleadr.downbeat.BuildConfig
import com.feerlessleadr.downbeat.R

/**
 * The Settings destination, always the last button on the bottom bar. Provider setup and server admin (docs/roadmap.md,
 * M6 and M7) will get entries here, for admin users only.
 */
@Composable
fun SettingsScreen(
    actions: AppMenuActions,
    serverUrl: String?,
    mediaControls: Boolean,
    onMediaControls: (Boolean) -> Unit,
    /** Shown only for administrator accounts (see MusicRepository.providerAdmin). */
    showProviders: Boolean,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text("This phone will forget your server and its sign-in, and its access on the server is removed. You will need your username and password to sign in again.")
            },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; actions.onSignOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp))
        SettingsRow("Appearance", "Light or dark, and the colours", R.drawable.ic_album, actions.onAppearance)
        HorizontalDivider()
        SettingsRow("Bottom bar", "Choose the buttons and their order", R.drawable.ic_nav_library, actions.onCustomizeBar)
        HorizontalDivider()
        SettingsRow("Rooms", "Choose which rooms are shown", R.drawable.ic_speaker, actions.onChooseRooms)
        HorizontalDivider()
        if (showProviders) {
            SettingsRow("Music sources and players", "See what is connected to Music Assistant, and change its settings", R.drawable.ic_radio, actions.onProviders)
            HorizontalDivider()
        }
        Row(
            Modifier.fillMaxWidth().clickable { onMediaControls(!mediaControls) }.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Lock screen controls", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Show what is playing, with play, pause and skip buttons, on the lock screen and in the notification shade",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = mediaControls, onCheckedChange = onMediaControls, modifier = Modifier.padding(start = 12.dp))
        }
        HorizontalDivider()
        SettingsRow("Sign out", "Forget this phone and remove its access on the server", R.drawable.ic_power) { confirmSignOut = true }
        HorizontalDivider()
        Text(
            "Downbeat ${BuildConfig.VERSION_NAME}, built " +
                java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
                    .format(java.time.Instant.ofEpochMilli(BuildConfig.BUILD_TIME).atZone(java.time.ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
