package com.feerlessleadr.downbeat.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.feerlessleadr.downbeat.data.ColorMath
import com.feerlessleadr.downbeat.data.MusicRepository
import com.feerlessleadr.downbeat.data.RoomLogic
import com.feerlessleadr.downbeat.data.SeedColors
import com.feerlessleadr.downbeat.data.Themes
import com.feerlessleadr.downbeat.ma.ImageUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * The app's colours. A mode (light, dark or the phone's setting) and base colours: one of the named colours, or the
 * phone's wallpaper colours (Android 12 and later). The base colours make the surfaces, text and outlines. With
 * "accent follows the music" on, only the accent roles (buttons, highlights, selected items) take the colour of the cover
 * that is playing, and fade back to the base colours when nothing plays. The surfaces never change with the music.
 */
@Composable
fun DownbeatTheme(repo: MusicRepository, content: @Composable () -> Unit) {
    val settings by repo.theme.collectAsStateWithLifecycle()
    val dark = when (settings.mode) {
        Themes.DARK -> true
        Themes.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val useWallpaper = settings.palette == Themes.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseSeed = when (settings.palette) {
        Themes.WALLPAPER -> Themes.swatch(Themes.DEFAULT_PALETTE).argb
        Themes.CUSTOM -> settings.customColor
        else -> Themes.swatch(settings.palette).argb
    }
    val musicSeed = if (settings.accentFollowsMusic) rememberMusicSeed(repo) else null
    // With nothing playing the accent target is the base's own colour, so the highlights fade back to the base.
    // Slow and eased, so a change reads as a gentle shift and never a snap: about 1.4 s from one cover to the next, and
    // the slowest, about 3.4 s, when returning to the base colours after the music stops.
    val accent by animateColorAsState(
        targetValue = Color(musicSeed ?: baseSeed),
        animationSpec = tween(
            durationMillis = if (musicSeed == null) ACCENT_RETURN_MS else ACCENT_CHANGE_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "accentSeed",
    )

    fun schemeFor(isDark: Boolean): ColorScheme {
        if (useWallpaper) {
            val phone = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return if (musicSeed != null) withAccentRoles(phone, ColorMath.seedColors(accent.toArgb(), isDark)) else phone
        }
        val base = ColorMath.seedColors(baseSeed, isDark)
        return colorSchemeFrom(if (settings.accentFollowsMusic) ColorMath.withAccent(base, accent.toArgb(), isDark) else base, isDark)
    }
    val scheme = schemeFor(dark)
    // The Playing screen is always dark, in the same palette, so it asks for the dark version of the current colours.
    val darkScheme = schemeFor(true)

    // Status and navigation bar icons follow the chosen mode (the system only knows the phone's own setting).
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !dark
            insets.isAppearanceLightNavigationBars = !dark
        }
        onDispose {}
    }

    CompositionLocalProvider(LocalDarkScheme provides darkScheme) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** The dark colours of the current palette (see [DownbeatTheme]). */
val LocalDarkScheme = staticCompositionLocalOf<ColorScheme> { darkColorScheme() }

/** [scheme] with the accent roles replaced by those of [a] (used over the phone's own wallpaper colours). */
private fun withAccentRoles(scheme: ColorScheme, a: SeedColors): ColorScheme = scheme.copy(
    primary = Color(a.primary), onPrimary = Color(a.onPrimary),
    primaryContainer = Color(a.primaryContainer), onPrimaryContainer = Color(a.onPrimaryContainer),
    secondary = Color(a.secondary), onSecondary = Color(a.onSecondary),
    secondaryContainer = Color(a.secondaryContainer), onSecondaryContainer = Color(a.onSecondaryContainer),
    tertiary = Color(a.tertiary), onTertiary = Color(a.onTertiary),
)

private fun colorSchemeFrom(c: SeedColors, dark: Boolean): ColorScheme {
    fun k(argb: Int) = Color(argb)
    return if (dark) {
        darkColorScheme(
            primary = k(c.primary), onPrimary = k(c.onPrimary), primaryContainer = k(c.primaryContainer), onPrimaryContainer = k(c.onPrimaryContainer),
            secondary = k(c.secondary), onSecondary = k(c.onSecondary), secondaryContainer = k(c.secondaryContainer), onSecondaryContainer = k(c.onSecondaryContainer),
            tertiary = k(c.tertiary), onTertiary = k(c.onTertiary),
            background = k(c.background), onBackground = k(c.onBackground), surface = k(c.surface), onSurface = k(c.onSurface),
            surfaceVariant = k(c.surfaceVariant), onSurfaceVariant = k(c.onSurfaceVariant),
            surfaceContainer = k(c.surfaceContainer), surfaceContainerHigh = k(c.surfaceContainerHigh), outline = k(c.outline),
            // No default purple blended over raised surfaces, and the other surface roles follow the palette too.
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = k(c.surface), surfaceContainerLow = k(c.surface), surfaceContainerHighest = k(c.surfaceContainerHigh),
            surfaceDim = k(c.surface), surfaceBright = k(c.surfaceContainerHigh),
        )
    } else {
        lightColorScheme(
            primary = k(c.primary), onPrimary = k(c.onPrimary), primaryContainer = k(c.primaryContainer), onPrimaryContainer = k(c.onPrimaryContainer),
            secondary = k(c.secondary), onSecondary = k(c.onSecondary), secondaryContainer = k(c.secondaryContainer), onSecondaryContainer = k(c.onSecondaryContainer),
            tertiary = k(c.tertiary), onTertiary = k(c.onTertiary),
            background = k(c.background), onBackground = k(c.onBackground), surface = k(c.surface), onSurface = k(c.onSurface),
            surfaceVariant = k(c.surfaceVariant), onSurfaceVariant = k(c.onSurfaceVariant),
            surfaceContainer = k(c.surfaceContainer), surfaceContainerHigh = k(c.surfaceContainerHigh), outline = k(c.outline),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = k(c.surface), surfaceContainerLow = k(c.surface), surfaceContainerHighest = k(c.surfaceContainerHigh),
            surfaceDim = k(c.surface), surfaceBright = k(c.surfaceContainerHigh),
        )
    }
}

private val seedCache = HashMap<String, Int>()

/**
 * The main colour of the cover of the song that is playing in the chosen rooms. It keeps the previous colour while the next
 * cover loads, and returns to null (the base colours) a moment after playback stops.
 */
@Composable
private fun rememberMusicSeed(repo: MusicRepository): Int? {
    val state by repo.state.collectAsStateWithLifecycle()
    val selected by repo.selectedRoomId.collectAsStateWithLifecycle()
    val hidden by repo.hiddenRooms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val leader = RoomLogic.effectiveLeader(state.players, selected, hidden)
    val queue = leader?.let { state.queues[it.playerId] }
    val serverUrl = repo.serverUrl
    val url = if (leader != null && serverUrl != null && (leader.playbackState == "playing" || leader.playbackState == "paused")) {
        ImageUrls.rewriteHost(serverUrl, leader.currentMedia?.imageUrl) ?: ImageUrls.forImage(serverUrl, queue?.currentItem?.image, 256)
    } else {
        null
    }
    val seed by produceState<Int?>(null, url) {
        if (url != null) {
            value = loadSeed(context, url) ?: value
        } else {
            // Stopped: go back to the base colours, after a pause so the gap between two songs does not flash them.
            kotlinx.coroutines.delay(2500)
            value = null
        }
    }
    return seed
}

/** Loads a small copy of the picture through the app's image loader and reads its main colour. Remembered per picture. */
private suspend fun loadSeed(context: Context, url: String): Int? {
    seedCache[url]?.let { return it }
    return withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context).data(url).size(Size(48, 48)).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result is SuccessResult) {
            val bitmap = result.image.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            ColorMath.dominantColor(pixels)?.also {
                if (seedCache.size > 60) seedCache.clear()
                seedCache[url] = it
            }
        } else {
            null
        }
    }
}

/** How long the accent takes to move to the next cover's colour, in milliseconds. */
private const val ACCENT_CHANGE_MS = 1400

/** How long the accent takes to return to the base colours when the music stops, in milliseconds. */
private const val ACCENT_RETURN_MS = 3400
