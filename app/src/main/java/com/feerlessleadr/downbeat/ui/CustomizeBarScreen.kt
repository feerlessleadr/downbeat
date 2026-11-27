package com.feerlessleadr.downbeat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.NavConfig

/**
 * Pick which destinations the bottom bar holds (up to [NavConfig.MAX_ITEMS]) and their order. Ticked ones come first,
 * in bar order: drag the handle of a ticked one up or down to move it. Settings is always the last button and is not
 * listed here.
 */
@Composable
fun CustomizeBarScreen(current: List<String>, onSave: (List<String>) -> Unit, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    val start = NavConfig.sanitize(current)
    // Ticked items first (the bar order), then the rest. Ticking or unticking regroups them.
    val order = remember { mutableStateListOf<String>().also { it.addAll(start + NavConfig.ALL.filter { id -> id !in start }) } }
    var ticked by remember { mutableStateOf(start.toSet()) }
    val atLimit = ticked.size >= NavConfig.MAX_ITEMS
    val listState = rememberLazyListState()

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { snapshotFlow { listState.layoutInfo }.collect { settling = false } }

    fun regroup(newTicked: Set<String>) {
        val bar = order.filter { it in newTicked }
        val rest = order.filter { it !in newTicked }
        order.clear()
        order.addAll(bar + rest)
        ticked = newTicked
    }

    fun onDrag(deltaY: Float) {
        val id = dragId ?: return
        dragOffset += deltaY
        if (settling) return
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val target = info.visibleItemsInfo.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as String)
        // Only among the ticked ones (they are listed first).
        if (to !in 0 until ticked.size || to == from) return
        val newBase = if (to > from) target.offset + target.size - dragged.size else target.offset
        order.removeAt(from)
        order.add(to, id)
        dragOffset -= (newBase - dragged.offset)
        settling = true
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text("Bottom bar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            "Pick up to ${NavConfig.MAX_ITEMS} places for the bar and drag them into the order you like. " +
                "Settings is always the last button. The player bar above it shows whenever music is playing.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(order.toList(), key = { it }) { id ->
                val dest = destinationFor(id) ?: return@items
                val isTicked = id in ticked
                val isDragged = dragId == id
                Surface(
                    modifier = if (isDragged) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset } else Modifier.animateItem(),
                    tonalElevation = if (isDragged) 8.dp else 0.dp,
                    shadowElevation = if (isDragged) 6.dp else 0.dp,
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = isTicked || !atLimit) { regroup(if (isTicked) ticked - id else ticked + id) }
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isTicked, onCheckedChange = null, enabled = isTicked || !atLimit)
                            Icon(painterResource(dest.icon), contentDescription = null, modifier = Modifier.padding(start = 12.dp))
                            Text(dest.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            if (isTicked && ticked.size > 1) {
                                Box(
                                    Modifier.size(48.dp).pointerInput(id) {
                                        detectDragGestures(
                                            onDragStart = { dragId = id; dragOffset = 0f },
                                            onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                                            onDragEnd = { dragId = null; dragOffset = 0f },
                                            onDragCancel = { dragId = null; dragOffset = 0f },
                                        )
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = "Drag to reorder ${dest.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSave(order.filter { it in ticked }) },
                enabled = ticked.isNotEmpty(),
            ) { Text("Done") }
        }
    }
}
