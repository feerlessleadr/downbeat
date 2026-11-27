package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.ProviderGroup
import com.feerlessleadr.downbeat.data.ProviderStatus
import com.feerlessleadr.downbeat.data.providerGroups
import kotlinx.coroutines.CancellationException

/**
 * Settings, Music sources and players (administrators only): every configured provider with its status and any error the
 * server reports. Read-only for now; changing, adding and removing come next (docs/provider-setup-research.md).
 */
@Composable
fun ProvidersScreen(repo: MusicRepository, onBack: () -> Unit) {
    BackHandler { onBack() }
    var groups by remember { mutableStateOf<List<ProviderGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        val api = repo.api
        if (api == null) {
            error = "Not connected to the server."
        } else {
            error = null
            try {
                val configs = api.providerConfigs()
                // The descriptions are a nicety: if the manifests cannot be read, the list still shows.
                val manifests = try { api.providerManifests() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                groups = providerGroups(configs, manifests)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = describe(e)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Music sources and players", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        val list = groups
        when {
            error != null && list == null -> Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            list == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Text("Nothing is configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                list.forEach { group ->
                    item(key = "h-${group.title}") {
                        Text(
                            group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(group.rows, key = { it.instanceId }) { row ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
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
}

@Composable
private fun statusColor(status: ProviderStatus): Color = when (status) {
    ProviderStatus.Working -> MaterialTheme.colorScheme.primary
    ProviderStatus.Problem, ProviderStatus.Incompatible -> MaterialTheme.colorScheme.error
    ProviderStatus.SignInNeeded -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
