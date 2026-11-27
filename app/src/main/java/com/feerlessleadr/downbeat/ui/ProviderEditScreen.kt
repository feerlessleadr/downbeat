package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.feerlessleadr.downbeat.data.ConfigFormState
import com.feerlessleadr.downbeat.data.EntriesResult
import com.feerlessleadr.downbeat.data.ProviderController
import com.feerlessleadr.downbeat.data.ProviderRow
import com.feerlessleadr.downbeat.ma.ConfigEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * A provider's settings, drawn from the form the server describes: switches, text, passwords, numbers, choices, buttons,
 * notes. Fields show only when their dependencies allow, advanced ones fold away, required and number fields are checked,
 * and only what was changed is saved. Passwords are hidden as they are typed and are never sent back unless edited.
 */
@Composable
fun ProviderEditScreen(
    row: ProviderRow,
    controller: ProviderController,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store = remember { mutableStateMapOf<String, JsonElement?>() }
    var form by remember { mutableStateOf<ConfigFormState?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showProblems by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    LaunchedEffect(row.instanceId) {
        when (val result = controller.entries(row.instanceId)) {
            is EntriesResult.Loaded -> form = ConfigFormState(result.entries, store)
            is EntriesResult.Failed -> loadError = result.message
        }
    }

    fun leave() {
        if (form?.hasChanges() == true) confirmDiscard = true else onBack()
    }
    BackHandler { leave() }

    fun runAction(entry: ConfigEntry) {
        val action = entry.action ?: return
        scope.launch {
            busy = true
            message = null
            val outcome = controller.action(row.instanceId, action)
            outcome.error?.let { message = it }
            outcome.message?.let { message = it }
            outcome.openUrl?.let { runCatching { uri.openUri(it) } }
            outcome.entries?.let { fresh ->
                store.clear()
                form = ConfigFormState(fresh, store)
            }
            busy = false
        }
    }

    fun save() {
        val f = form ?: return
        showProblems = true
        if (f.problems().isNotEmpty()) {
            message = "Fix the marked fields first."
            return
        }
        val changed = f.changedValues()
        val needsReload = f.entries.any { f.isChanged(it) && it.requiresReload }
        scope.launch {
            busy = true
            message = null
            val failure = controller.save(row, changed)
            if (failure != null) {
                message = failure
                busy = false
            } else {
                message = if (needsReload) "Saved. Reloading…" else "Saved."
                if (needsReload) controller.reload(row.instanceId)
                delay(700)
                busy = false
                onSaved()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings: ${row.name}", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val f = form
        when {
            loadError != null -> Text(loadError ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            f == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            else -> {
                val visible = f.visibleEntries(showAdvanced)
                val problems = if (showProblems) f.problems() else f.entries.mapNotNull { e -> f.problem(e)?.let { e.key to it } }.toMap()
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (visible.isEmpty()) {
                        item { Text("This provider has no settings.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
                    }
                    items(visible.size, key = { visible[it].key }) { index ->
                        val entry = visible[index]
                        val previous = visible.getOrNull(index - 1)
                        if (entry.category != "generic" && entry.category != previous?.category) {
                            Text(
                                entry.categoryLabel ?: entry.category.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                            )
                        }
                        FieldRow(entry, f, problems[entry.key], busy, onAction = { runAction(entry) }, onOpen = { runCatching { uri.openUri(it) } })
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (f.hasAdvanced()) {
                        TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide advanced" else "Show advanced") }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { save() }, enabled = !busy && f.hasChanges()) { Text("Save") }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
            text = { Text("The changes you made to ${row.name} have not been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
internal fun FieldRow(
    entry: ConfigEntry,
    form: ConfigFormState,
    problem: String?,
    busy: Boolean,
    onAction: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val title = (entry.label ?: entry.key) + if (entry.required && form.isEditable(entry) && entry.type != "boolean") " *" else ""
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (entry.type) {
            "divider" -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
            "label" -> Text(entry.label ?: entry.description.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            "alert" -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Text(entry.label ?: entry.description.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
            "action" -> {
                OutlinedButton(onClick = onAction, enabled = !busy) { Text(entry.actionLabel ?: entry.label ?: "Run") }
                entry.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            }
            "url" -> {
                val link = (form.value(entry.key) as? JsonPrimitive)?.contentOrNull
                Text(entry.label ?: entry.key, style = MaterialTheme.typography.titleSmall)
                if (link != null) TextButton(onClick = { onOpen(link) }) { Text("Open in the browser") }
            }
            "pairing_code" -> {
                Text(entry.label ?: entry.key, style = MaterialTheme.typography.titleSmall)
                Text(form.text(entry), style = MaterialTheme.typography.headlineSmall)
            }
            "icon", "image" -> Unit
            "boolean" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.label ?: entry.key, style = MaterialTheme.typography.titleMedium)
                        entry.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Switch(
                        checked = (form.value(entry.key) as? JsonPrimitive)?.booleanOrNull ?: false,
                        onCheckedChange = { form.set(entry.key, JsonPrimitive(it)) },
                        enabled = !entry.readOnly,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            else -> when {
                entry.options.isNotEmpty() && entry.multiValue -> MultiChoice(entry, form, title)
                entry.options.isNotEmpty() -> SingleChoice(entry, form, title, problem)
                else -> OutlinedTextField(
                    value = form.text(entry),
                    onValueChange = { form.setText(entry, it) },
                    label = { Text(title) },
                    singleLine = entry.type != "splitted_string",
                    minLines = if (entry.type == "splitted_string") 3 else 1,
                    readOnly = entry.readOnly,
                    isError = problem != null,
                    supportingText = { (problem ?: entry.description)?.let { Text(it) } },
                    visualTransformation = if (entry.type == "secure_string") PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (entry.type) {
                            "integer" -> KeyboardType.Number
                            "float" -> KeyboardType.Decimal
                            "secure_string" -> KeyboardType.Password
                            else -> KeyboardType.Text
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        entry.helpLink?.let { link -> if (entry.type != "url") TextButton(onClick = { onOpen(link) }) { Text("Help") } }
    }
}

@Composable
private fun SingleChoice(entry: ConfigEntry, form: ConfigFormState, title: String, problem: String?) {
    var open by remember { mutableStateOf(false) }
    val current = form.value(entry.key)
    val selected = entry.options.firstOrNull { (it.value as? JsonPrimitive)?.contentOrNull == (current as? JsonPrimitive)?.contentOrNull }
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box {
        OutlinedButton(onClick = { open = true }, enabled = !entry.readOnly, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.title ?: (current as? JsonPrimitive)?.contentOrNull ?: "Choose", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entry.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title ?: (option.value as? JsonPrimitive)?.contentOrNull.orEmpty()) },
                    enabled = !option.disabled,
                    onClick = { open = false; form.set(entry.key, option.value) },
                )
            }
        }
    }
    (problem ?: entry.description)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MultiChoice(entry: ConfigEntry, form: ConfigFormState, title: String) {
    val chosen = ((form.value(entry.key) as? JsonArray) ?: JsonArray(emptyList())).map { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    entry.options.forEach { option ->
        val key = (option.value as? JsonPrimitive)?.contentOrNull
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = key in chosen,
                enabled = !entry.readOnly && !option.disabled,
                onCheckedChange = { on ->
                    val next = if (on) chosen + key else chosen - key
                    form.set(entry.key, JsonArray(entry.options.filter { (it.value as? JsonPrimitive)?.contentOrNull in next }.mapNotNull { it.value }))
                },
            )
            Text(option.title ?: key.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
