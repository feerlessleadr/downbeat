package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.ServerController
import com.feerlessleadr.downbeat.data.TextResult
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.formatWhen
import com.feerlessleadr.downbeat.data.isThisPhone
import com.feerlessleadr.downbeat.data.logTail
import com.feerlessleadr.downbeat.data.taskError
import com.feerlessleadr.downbeat.data.taskSummary
import com.feerlessleadr.downbeat.data.tokenKind
import com.feerlessleadr.downbeat.ma.AdminUser
import com.feerlessleadr.downbeat.ma.AuthToken
import com.feerlessleadr.downbeat.ma.BackgroundTask
import com.feerlessleadr.downbeat.ma.MaApi
import com.feerlessleadr.downbeat.ma.ServerAbout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One part of the screen, loaded on its own so one refused read does not blank the rest. */
private class Part<T> {
    var value by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
}

private suspend fun <T> Part<T>.fill(api: MaApi?, call: suspend (MaApi) -> T) {
    loading = true
    if (api == null) {
        error = "Not connected to the server."
    } else {
        try {
            value = call(api)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = describe(e)
        }
    }
    loading = false
}

/**
 * Settings, Server (administrators only): about this server, its background tasks, its users, its access tokens and the
 * latest lines of its log. Read-only for now (docs/server-admin-research.md): tasks, tokens and users get actions next.
 */
@Composable
fun ServerScreen(repo: MusicRepository, onBack: () -> Unit) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val about = remember { Part<ServerAbout>() }
    val tasks = remember { Part<List<BackgroundTask>>() }
    val users = remember { Part<List<AdminUser>>() }
    val tokens = remember { Part<List<AuthToken>>() }
    var refresh by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf<String?>(null) }
    var logLoading by remember { mutableStateOf(false) }
    var logError by remember { mutableStateOf<String?>(null) }
    val controller = remember(repo) { ServerController(repo) }
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var acting by remember { mutableStateOf(false) }
    var selectedTask by remember { mutableStateOf<BackgroundTask?>(null) }
    var taskLog by remember { mutableStateOf<String?>(null) }
    var selectedToken by remember { mutableStateOf<AuthToken?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    /** Runs one admin action, says what happened, and reads everything back shortly after. */
    fun act(success: String, call: suspend () -> String?) {
        scope.launch {
            acting = true
            message = null
            val failure = call()
            message = failure ?: success
            acting = false
            if (failure == null) {
                delay(800)
                refresh++
            }
        }
    }

    LaunchedEffect(refresh) {
        val api = repo.api
        about.fill(api) { it.aboutServer() }
        tasks.fill(api) { it.backgroundTasks() }
        users.fill(api) { it.adminUsers() }
        tokens.fill(api) { it.tokens() }
    }
    val busy = about.loading || tasks.loading || users.loading || tokens.loading

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Server", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh++ }, enabled = !busy) { Text("Refresh") }
        }
        if (busy || acting) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            // About
            item { SectionTitle("This server") }
            item {
                Part(about) { a ->
                    InfoLine("Name", a.name ?: "Music Assistant")
                    InfoLine("Version", a.serverVersion)
                    InfoLine("Status", a.status?.replaceFirstChar { it.uppercase() } ?: "Running")
                    InfoLine("Runs as", if (a.homeAssistantAddon) "A Home Assistant add-on" else "A standalone server")
                    InfoLine("Remote access", if (a.hasRemoteAccess) "On" else "Off")
                }
            }

            // Tasks
            item { SectionTitle("Background tasks" + (tasks.value?.let { " (${it.size})" } ?: "")) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(
                        "Tap a task to run it, stop it, retry it, switch its schedule or read its log.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        OutlinedButton(onClick = { act("Library sync started. It runs in the background.") { controller.syncAllLibraries() } }, enabled = !acting) { Text("Sync all libraries") }
                        OutlinedButton(onClick = { confirmClear = true }, enabled = !acting) { Text("Clear finished") }
                    }
                }
            }
            item {
                Part(tasks, emptyText = "No tasks.") { list ->
                    list.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { selectedTask = t }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name.ifBlank { t.id }, style = MaterialTheme.typography.titleSmall)
                                Text(taskSummary(t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                taskError(t)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            }
                            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }

            // Users
            item { SectionTitle("Users" + (users.value?.let { " (${it.size})" } ?: "")) }
            item {
                Part(users, emptyText = "No users.") { list ->
                    list.forEach { u ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.displayName?.takeIf { it.isNotBlank() } ?: u.username, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${u.username} · ${u.role.ifBlank { "user" }} · joined ${formatWhen(u.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!u.enabled) Text("Disabled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                        HorizontalDivider()
                    }
                }
            }

            // Tokens
            item { SectionTitle("Access tokens" + (tokens.value?.let { " (${it.size})" } ?: "")) }
            item {
                Text(
                    "Tap a token to revoke it. Whoever uses it is signed out.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item {
                Part(tokens, emptyText = "No tokens.") { list ->
                    list.sortedWith(compareByDescending<AuthToken> { isThisPhone(it, repo.thisTokenName) }.thenBy { it.name.lowercase() }).forEach { t ->
                        Column(Modifier.fillMaxWidth().clickable { selectedToken = t }.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (isThisPhone(t, repo.thisTokenName)) {
                                    Text("This phone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text(
                                "${tokenKind(t)} · created ${formatWhen(t.createdAt)} · last used ${formatWhen(t.lastUsedAt)}" +
                                    (t.expiresAt?.let { " · expires ${formatWhen(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            // Log
            item { SectionTitle("Application log") }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                logLoading = true
                                logError = null
                                try {
                                    log = logTail(repo.api?.serverLog() ?: throw IllegalStateException("offline"))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logError = if (repo.api == null) "Not connected to the server." else describe(e)
                                }
                                logLoading = false
                            }
                        },
                        enabled = !logLoading,
                    ) { Text(if (log == null) "Show the latest log lines" else "Reload the log") }
                    log?.let { text ->
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, "Share the log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Share these lines") }
                    }
                    if (logLoading) CircularProgressIndicator(Modifier.padding(top = 12.dp))
                    logError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    log?.let { text ->
                        SelectionContainer {
                            Text(
                                text.ifBlank { "The log is empty." },
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 12.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
            item { Column(Modifier.padding(bottom = 32.dp)) {} }
        }

    // ---- Dialogs ----
    selectedTask?.let { t ->
        val canSwitchSchedule = t.schedule != null && t.schedule !is kotlinx.serialization.json.JsonNull
        AlertDialog(
            onDismissRequest = { selectedTask = null; taskLog = null },
            title = { Text(t.name.ifBlank { t.id }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(taskSummary(t), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    taskError(t)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { selectedTask = null; act("Queued to run next.") { controller.runTask(t.id) } }) { Text("Run now") }
                    if (t.allowCancel) TextButton(onClick = { selectedTask = null; act("Stopping it.") { controller.cancelTask(t.id) } }) { Text("Stop") }
                    if (t.allowRetry) TextButton(onClick = { selectedTask = null; act("Retrying.") { controller.retryTask(t.id) } }) { Text("Retry") }
                    if (canSwitchSchedule) {
                        TextButton(onClick = { selectedTask = null; act("Automatic runs turned on.") { controller.setTaskEnabled(t.id, true) } }) { Text("Turn automatic runs on") }
                        TextButton(onClick = { selectedTask = null; act("Automatic runs turned off.") { controller.setTaskEnabled(t.id, false) } }) { Text("Turn automatic runs off") }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            taskLog = when (val r = controller.taskLog(t.id)) {
                                is TextResult.Text -> logTail(r.text).ifBlank { "This task has no log." }
                                is TextResult.Failed -> r.message
                            }
                        }
                    }) { Text("Show its log") }
                    taskLog?.let {
                        SelectionContainer {
                            Text(
                                it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 8.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedTask = null; taskLog = null }) { Text("Close") } },
        )
    }

    selectedToken?.let { t ->
        val mine = isThisPhone(t, repo.thisTokenName)
        AlertDialog(
            onDismissRequest = { selectedToken = null },
            title = { Text("Revoke ${t.name.ifBlank { "this token" }}?") },
            text = {
                Text(
                    if (mine) "This is the token this phone uses. Revoking it signs this phone out of Downbeat, and you will need your password to sign in again."
                    else "Whoever uses this token is signed out and must sign in again. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedToken = null
                    act("Revoked.") { controller.revokeToken(t.tokenId) }
                }) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { selectedToken = null }) { Text("Cancel") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear finished tasks?") },
            text = { Text("This removes finished tasks from the list. Tasks that run on a schedule stay.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; act("Cleared.") { controller.clearFinishedTasks() } }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shows a part's error, a spinner while it first loads, its empty text, or its content. */
@Composable
private fun <T> Part(part: Part<T>, emptyText: String? = null, content: @Composable (T) -> Unit) {
    val v = part.value
    when {
        part.error != null && v == null -> Text(part.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        v == null -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        v is List<*> && v.isEmpty() && emptyText != null -> Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        else -> Column { content(v) }
    }
}
