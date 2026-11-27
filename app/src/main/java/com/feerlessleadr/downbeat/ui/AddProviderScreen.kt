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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feerlessleadr.downbeat.data.AddableProvider
import com.feerlessleadr.downbeat.data.ConfigFormState
import com.feerlessleadr.downbeat.data.FlowResult
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.ProviderController
import com.feerlessleadr.downbeat.data.addableProviders
import com.feerlessleadr.downbeat.ma.SetupFlowStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Settings, Music sources and players, Add: the kinds of provider the server could add (not built in, not already set up
 * unless it allows several). Choosing one asks first, then runs the server's setup (see [SetupFlowScreen]).
 */
@Composable
fun AddProviderScreen(repo: MusicRepository, controller: ProviderController, onBack: () -> Unit, onAdded: () -> Unit) {
    var groups by remember { mutableStateOf<List<Pair<String, List<AddableProvider>>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<AddableProvider?>(null) }
    var running by remember { mutableStateOf<AddableProvider?>(null) }

    LaunchedEffect(Unit) {
        val api = repo.api
        if (api == null) {
            error = "Not connected to the server."
        } else {
            try {
                groups = addableProviders(api.providerManifests(), api.providerConfigs())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = describe(e)
            }
        }
    }

    val active = running
    if (active != null) {
        SetupFlowScreen(
            provider = active, controller = controller,
            onClose = { added -> running = null; if (added) onAdded() },
        )
        return
    }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Add a music source or player", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val list = groups
        when {
            error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            list == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text("Everything this server can add is already added.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                list.forEach { (title, items) ->
                    item(key = "h-$title") {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
                    }
                    items(items, key = { it.domain }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { chosen = p }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (p.stageLabel.isNotEmpty()) {
                                    Text(p.stageLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            if (p.description.isNotBlank()) {
                                Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    chosen?.let { p ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text("Add ${p.name}?") },
            text = {
                Text(
                    (p.description.takeIf { it.isNotBlank() }?.let { "$it\n\n" } ?: "") +
                        if (p.hasSetupSteps) "The server will ask for what it needs, step by step." else "This one needs no setup, so it is added right away.",
                )
            },
            confirmButton = { TextButton(onClick = { chosen = null; running = p }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Cancel") } },
        )
    }
}

/**
 * The server's step-by-step setup for one provider: form steps (filled in here and sent), external steps (open a web
 * sign-in, then wait), progress steps (wait), and the end (finished or stopped). Leaving stops the setup.
 */
@Composable
private fun SetupFlowScreen(provider: AddableProvider, controller: ProviderController, onClose: (added: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var step by remember { mutableStateOf<SetupFlowStep?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showProblems by remember { mutableStateOf(false) }
    val stepKey = step?.let { it.flowId + "/" + it.stepId }
    val store = remember(stepKey) { mutableStateMapOf<String, JsonElement?>() }
    val form = remember(stepKey) {
        step?.takeIf { it.type == "form" }?.let { s -> ConfigFormState(s.entries.filter { it.type != "action" }, store) }
    }

    LaunchedEffect(provider.domain) {
        when (val r = controller.startSetup(provider.domain)) {
            is FlowResult.Step -> step = r.step
            is FlowResult.Failed -> failure = r.message
        }
    }

    // Wait on an external or progress step: ask the server again every two seconds until the step changes.
    LaunchedEffect(step?.flowId, step?.stepId, step?.type) {
        val s = step ?: return@LaunchedEffect
        if (s.type == "external" || s.type == "progress") {
            while (true) {
                delay(2000)
                when (val r = controller.refreshStep(s.flowId)) {
                    is FlowResult.Step -> {
                        val changed = r.step.stepId != s.stepId || r.step.type != s.type
                        step = r.step
                        if (changed) break
                    }
                    is FlowResult.Failed -> {
                        message = r.message
                        break
                    }
                }
            }
        }
    }

    fun leave() {
        val s = step
        if (s != null && s.type != "finish" && s.type != "abort") scope.launch { controller.abortSetup(s.flowId) }
        onClose(s?.type == "finish")
    }
    BackHandler { leave() }

    fun submit() {
        val s = step ?: return
        val f = form ?: return
        showProblems = true
        if (f.problems().isNotEmpty()) {
            message = "Fix the marked fields first."
            return
        }
        scope.launch {
            busy = true
            message = null
            when (val r = controller.submitStep(s.flowId, f.submitValues())) {
                is FlowResult.Step -> step = r.step
                is FlowResult.Failed -> message = r.message
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Add ${provider.name}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val s = step
        when {
            failure != null -> Column(Modifier.padding(24.dp)) {
                Text(failure ?: "", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { onClose(false) }) { Text("Close") }
            }
            s == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            s.type == "form" && form != null -> {
                val problems = if (showProblems) form.problems() else form.entries.mapNotNull { e -> form.problem(e)?.let { e.key to it } }.toMap()
                val visible = form.visibleEntries(showAdvanced = true)
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            s.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            s.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    items(visible.size, key = { visible[it].key }) { i ->
                        val entry = visible[i]
                        FieldRow(entry, form, s.errors[entry.key] ?: problems[entry.key], busy, onAction = {}, onOpen = { runCatching { uri.openUri(it) } })
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { leave() }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { submit() }, enabled = !busy) { Text(if (s.lastStep == true) "Finish" else "Next") }
                }
            }
            s.type == "external" -> Column(Modifier.padding(24.dp)) {
                Text(s.title ?: "Continue in your browser", style = MaterialTheme.typography.titleMedium)
                s.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(16.dp))
                s.url?.let { link -> Button(onClick = { runCatching { uri.openUri(link) } }) { Text("Open in the browser") } }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp))
                    Text("  Waiting for you to finish there…", style = MaterialTheme.typography.bodyMedium)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { leave() }) { Text("Cancel") }
            }
            s.type == "progress" -> Column(Modifier.padding(24.dp)) {
                Text(s.title ?: "Working…", style = MaterialTheme.typography.titleMedium)
                Text(s.progressText ?: s.description ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                val p = s.progress
                if (p != null) LinearProgressIndicator(progress = { p.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { leave() }) { Text("Cancel") }
            }
            s.type == "finish" -> Column(Modifier.padding(24.dp)) {
                Text("${provider.name} was added.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                s.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onClose(true) }) { Text("Done") }
            }
            else -> Column(Modifier.padding(24.dp)) {
                Text(s.title ?: "The setup stopped", style = MaterialTheme.typography.titleMedium)
                Text(s.reason ?: s.description ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { onClose(false) }) { Text("Close") }
            }
        }
    }
}
