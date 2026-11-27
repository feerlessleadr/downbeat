package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.ProviderController
import com.feerlessleadr.downbeat.data.ProviderGroup
import com.feerlessleadr.downbeat.data.ProviderRow
import com.feerlessleadr.downbeat.data.ProviderStatus
import com.feerlessleadr.downbeat.data.providerGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Settings, Music sources and players (administrators only): every configured provider with its status and any error the
 * server reports. Tap one to reload it, turn it off or on, sync a music source's library, or remove it (after asking).
 * Adding providers and editing their settings come next (docs/provider-setup-research.md).
 */
@Composable
fun ProvidersScreen(repo: MusicRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(repo) { ProviderController(repo) }
    var groups by remember { mutableStateOf<List<ProviderGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    /** Reads the providers again. Returns false when it could not. */
    suspend fun load(): Boolean {
        val api = repo.api
        if (api == null) {
            error = "Not connected to the server."
            return false
        }
        loading = true
        try {
            val configs = api.providerConfigs()
            // The descriptions are a nicety: if the manifests cannot be read, the list still shows.
            val manifests = try { api.providerManifests() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            groups = providerGroups(configs, manifests)
            error = null
            updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = describe(e)
            return false
        } finally {
            loading = false
        }
    }

    LaunchedEffect(refresh) { load() }

    val selected = selectedId?.let { id -> groups?.flatMap { it.rows }?.firstOrNull { it.instanceId == id } }
    BackHandler { if (selected != null) { selectedId = null; message = null } else onBack() }

    // One action on the selected provider: run it, read the list back, and say what happened.
    fun act(row: ProviderRow, success: String, verify: ((ProviderRow?) -> String?)? = null, call: suspend () -> String?) {
        scope.launch {
            busy = true
            message = null
            val failure = call()
            if (failure != null) {
                message = failure
            } else {
                // Give the server a moment to apply it, then read the list back so the screen shows the truth.
                delay(800)
                load()
                val after = groups?.flatMap { it.rows }?.firstOrNull { it.instanceId == row.instanceId }
                message = verify?.invoke(after) ?: success
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected != null) { selectedId = null; message = null } else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                selected?.name ?: "Music sources and players", style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (selected == null) TextButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh") }
        }
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        val list = groups
        when {
            selected != null -> ProviderDetail(
                row = selected, busy = busy, message = message,
                onReload = { act(selected, "Reloaded.") { controller.reload(selected.instanceId) } },
                onToggle = {
                    val turnOn = !selected.enabled
                    act(
                        selected, if (turnOn) "Turned on." else "Turned off.",
                        verify = { after ->
                            if (after != null && after.enabled != turnOn) {
                                "The server accepted that but the provider did not change. Try the Music Assistant web page for this one."
                            } else {
                                null
                            }
                        },
                    ) { controller.setEnabled(selected, turnOn) }
                },
                onSync = { act(selected, "Library sync started. New items appear as it finishes.") { controller.sync(selected.instanceId) } },
                onRemove = { confirmRemove = true },
            )
            error != null && list == null -> Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            list == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text("Nothing is configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        (if (error != null) "Could not refresh: $error" else updatedAt?.let { "Updated $it" }).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                list.forEach { group ->
                    item(key = "h-${group.title}") {
                        Text(
                            group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(group.rows, key = { it.instanceId }) { row ->
                        Column(Modifier.fillMaxWidth().clickable { selectedId = row.instanceId; message = null }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    row.status.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                                    color = statusColor(row.status),
                                )
                            }
                            Text(
                                row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            row.error?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val toRemove = selected
    if (confirmRemove && toRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${toRemove.name}?") },
            text = {
                Text(
                    "This removes ${toRemove.name} from Music Assistant" +
                        (if (toRemove.type == "music") " and the music it added to your library" else "") +
                        ". It cannot be undone. You can add it again later, but it will have to be set up from the start.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        busy = true
                        val failure = controller.remove(toRemove.instanceId)
                        if (failure != null) {
                            message = failure
                        } else {
                            selectedId = null
                            delay(800)
                            load()
                        }
                        busy = false
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProviderDetail(
    row: ProviderRow,
    busy: Boolean,
    message: String?,
    onReload: () -> Unit,
    onToggle: () -> Unit,
    onSync: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.status.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = statusColor(row.status), modifier = Modifier.weight(1f))
        }
        Text(row.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        row.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onReload, enabled = !busy && row.enabled, modifier = Modifier.fillMaxWidth()) { Text("Reload") }
        if (row.canTurnOff || !row.enabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onToggle, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (row.enabled) "Turn off" else "Turn on") }
        }
        if (row.canSync) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSync, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sync library") }
        }
        if (row.canRemove) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Remove") }
        }
        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun statusColor(status: ProviderStatus): Color = when (status) {
    ProviderStatus.Working -> MaterialTheme.colorScheme.primary
    ProviderStatus.Problem, ProviderStatus.Incompatible -> MaterialTheme.colorScheme.error
    ProviderStatus.SignInNeeded -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
