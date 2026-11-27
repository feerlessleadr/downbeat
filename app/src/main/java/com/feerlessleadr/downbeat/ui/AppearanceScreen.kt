package com.feerlessleadr.downbeat.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.feerlessleadr.downbeat.data.ColorMath
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.downbeat.R
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.Themes

/** Settings, Appearance: light, dark or the phone's setting, and the colours. Changes show at once. */
@Composable
fun AppearanceScreen(repo: MusicRepository, onBack: () -> Unit) {
    BackHandler { onBack() }
    val settings by repo.theme.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text("Mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp))
                Row(Modifier.padding(horizontal = 20.dp)) {
                    listOf(Themes.SYSTEM to "Phone setting", Themes.LIGHT to "Light", Themes.DARK to "Dark").forEach { (id, label) ->
                        FilterChip(
                            selected = settings.mode == id, onClick = { repo.setThemeMode(id) }, label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text("Accent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { repo.setAccentFollowsMusic(!settings.accentFollowsMusic) }.padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow the music", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Buttons and highlights take the colour of the cover that is playing. The rest of the app keeps your base colours, and the highlights return to them when nothing is playing.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.accentFollowsMusic, onCheckedChange = { repo.setAccentFollowsMusic(it) }, modifier = Modifier.padding(start = 12.dp))
                }
                HorizontalDivider()
                Text("Base colours", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    PaletteRow(
                        label = "Phone colours", detail = "From your wallpaper (Android 12 and later)",
                        selected = settings.palette == Themes.WALLPAPER, onClick = { repo.setThemePalette(Themes.WALLPAPER) },
                        swatch = {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
            Themes.SWATCHES.forEach { swatch ->
                item(key = swatch.id) {
                    PaletteRow(
                        label = swatch.label, detail = null,
                        selected = settings.palette == swatch.id, onClick = { repo.setThemePalette(swatch.id) },
                        swatch = { Box(Modifier.size(36.dp).clip(CircleShape).background(Color(swatch.argb))) },
                    )
                    HorizontalDivider()
                }
            }
            // Any colour on a gradient. Dragging changes the whole app live.
            item(key = "custom") {
                PaletteRow(
                    label = "Pick your own", detail = "Drag along the gradient to choose any colour",
                    selected = settings.palette == Themes.CUSTOM, onClick = { repo.setCustomColor(settings.customColor) },
                    swatch = {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(
                                Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(settings.customColor))) }
                    },
                )
                if (settings.palette == Themes.CUSTOM) HueBar(settings.customColor) { repo.setCustomColor(it) }
                HorizontalDivider()
            }
        }
    }
}

/**
 * A rainbow bar: touch or drag anywhere on it to pick that hue. The picked colour is a vivid mid-tone of the hue; the
 * theme then derives the app's colours from it (see ColorMath.seedColors).
 */
@Composable
private fun HueBar(color: Int, onPick: (Int) -> Unit) {
    val hue = ColorMath.toHsl(color)[0]
    var widthPx by remember { mutableFloatStateOf(1f) }
    val rainbow = remember { Brush.horizontalGradient((0..6).map { Color(ColorMath.hsl(it * 60f, 0.85f, 0.5f)) }) }
    val thumbPx = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .clip(RoundedCornerShape(20.dp))
            .background(rainbow)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun pick(x: Float) = onPick(ColorMath.hsl((x / widthPx).coerceIn(0f, 1f) * 359f, 0.85f, 0.5f))
                    pick(down.position.x)
                    down.consume()
                    drag(down.id) { change ->
                        change.consume()
                        pick(change.position.x)
                    }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((hue / 359f * widthPx - thumbPx / 2f).roundToInt(), 0) }
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color(color)),
        )
    }
}

@Composable
private fun PaletteRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit, swatch: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatch()
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}
