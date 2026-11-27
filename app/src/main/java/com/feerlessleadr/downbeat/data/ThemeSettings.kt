package com.feerlessleadr.downbeat.data

/** The person's colour choices. [mode] is light, dark or follow the phone; [palette] is a swatch id (see [Themes]). */
data class ThemeSettings(
    val mode: String = Themes.SYSTEM,
    /** The base colours: a swatch id or [Themes.WALLPAPER]. Surfaces and text come from these. */
    val palette: String = Themes.DEFAULT_PALETTE,
    /** When true the accent colours (buttons, highlights) follow the cover of what is playing, over the base colours. */
    val accentFollowsMusic: Boolean = false,
    /** The colour picked on the gradient, used when [palette] is [Themes.CUSTOM]. Opaque ARGB. */
    val customColor: Int = Themes.DEFAULT_CUSTOM,
)

/** A named colour a theme can be built from. [argb] is opaque. */
data class Swatch(val id: String, val label: String, val argb: Int)

object Themes {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    val MODES = listOf(SYSTEM, LIGHT, DARK)

    /** The old "match the music" palette id, kept only to read settings saved by an earlier build (see accentFollowsMusic). */
    const val MUSIC = "music"

    /** The phone's own wallpaper colours (Material You), on Android 12 and later. */
    const val WALLPAPER = "wallpaper"

    /** A colour picked freely on the gradient (see [ThemeSettings.customColor]). */
    const val CUSTOM = "custom"

    /** Where the picker starts: the same violet as the default swatch. */
    const val DEFAULT_CUSTOM = 0xFF7B61D6.toInt()

    const val DEFAULT_PALETTE = "violet"

    val SWATCHES = listOf(
        Swatch("violet", "Violet", 0xFF7B61D6.toInt()),
        Swatch("ocean", "Ocean", 0xFF1E88C8.toInt()),
        Swatch("teal", "Teal", 0xFF1BA39C.toInt()),
        Swatch("forest", "Forest", 0xFF3E9B4F.toInt()),
        Swatch("sunset", "Sunset", 0xFFE8742A.toInt()),
        Swatch("rose", "Rose", 0xFFD6457A.toInt()),
        Swatch("graphite", "Graphite", 0xFF6C7A89.toInt()),
    )

    fun sanitizeMode(id: String?): String = if (id in MODES) id!! else SYSTEM

    /** Keeps known swatches and the wallpaper palette; anything else (including the old "music" id) becomes the default. */
    fun sanitizePalette(id: String?): String =
        if (id == WALLPAPER || id == CUSTOM || SWATCHES.any { it.id == id }) id!! else DEFAULT_PALETTE

    fun swatch(id: String): Swatch = SWATCHES.firstOrNull { it.id == id } ?: SWATCHES.first()
}

/** The colours a theme needs, as opaque ARGB integers. */
data class SeedColors(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiary: Int, val onTertiary: Int,
    val background: Int, val onBackground: Int,
    val surface: Int, val onSurface: Int,
    val surfaceVariant: Int, val onSurfaceVariant: Int,
    val surfaceContainer: Int, val surfaceContainerHigh: Int,
    val outline: Int,
)

/**
 * Builds a readable light or dark colour set from one seed colour (an accent, or the main colour of an album cover).
 * Pure arithmetic on hue, saturation and lightness, so it can be unit tested. Text colours are chosen to keep a contrast
 * ratio of at least 4.5 against the colours they sit on.
 */
object ColorMath {
    fun seedColors(seedArgb: Int, dark: Boolean): SeedColors {
        val hsl = toHsl(seedArgb)
        val h = hsl[0]
        val rawS = hsl[1]
        // A grey cover stays grey; anything else is kept colourful but not neon.
        val s = if (rawS < 0.12f) rawS else rawS.coerceIn(0.35f, 0.85f)
        val tint = minOf(s, 0.3f)
        // Dark theme, like light, uses mid-tone, fully saturated accents and clearly tinted surfaces. (Pale accents at 78 percent
        // lightness and near-black surfaces looked washed out.) Grey covers stay grey.
        val sd = if (rawS < 0.12f) rawS else rawS.coerceIn(0.75f, 1.0f)
        // Surfaces carry only a faint hint of the colour, so the accents stand out against them instead of the whole screen
        // looking tinted.
        val tintD = if (rawS < 0.12f) rawS * 0.5f else (sd * 0.16f).coerceAtMost(0.16f)
        val h2 = (h + 60f) % 360f
        // The surfaces are fixed in lightness; the accents too. Text on them is then chosen by contrast, so every pair
        // stays readable whatever the hue (a pale yellow accent needs dark text even in the light theme).
        val surface = if (dark) hsl(h, tintD, 0.09f) else hsl(h, tint * 0.4f, 0.98f)
        val primary = if (dark) accent(h, sd, 0.60f, surface, true) else accent(h, s, 0.40f, surface, false)
        val primaryContainer = if (dark) hsl(h, sd * 0.85f, 0.34f) else hsl(h, s, 0.90f)
        val secondary = if (dark) accent(h, sd * 0.55f, 0.68f, surface, true) else accent(h, s * 0.45f, 0.40f, surface, false)
        val secondaryContainer = if (dark) hsl(h, sd * 0.6f, 0.30f) else hsl(h, s * 0.5f, 0.90f)
        val tertiary = if (dark) accent(h2, sd * 0.85f, 0.66f, surface, true) else accent(h2, s * 0.7f, 0.40f, surface, false)
        val surfaceVariant = if (dark) hsl(h, sd * 0.15f, 0.22f) else hsl(h, s * 0.25f, 0.90f)
        val lightText = hsl(h, 0.1f, 0.94f)
        val darkText = hsl(h, 0.15f, 0.10f)
        val preferred = if (dark) darkText else lightText // what sits on an accent: dark on light accents, and so on
        return SeedColors(
            primary = primary, onPrimary = pickOn(primary, preferred),
            primaryContainer = primaryContainer, onPrimaryContainer = pickOn(primaryContainer, if (dark) lightText else darkText),
            secondary = secondary, onSecondary = pickOn(secondary, preferred),
            secondaryContainer = secondaryContainer, onSecondaryContainer = pickOn(secondaryContainer, if (dark) lightText else darkText),
            tertiary = tertiary, onTertiary = pickOn(tertiary, preferred),
            background = surface, onBackground = pickOn(surface, if (dark) lightText else darkText, 7.0),
            surface = surface, onSurface = pickOn(surface, if (dark) lightText else darkText, 7.0),
            surfaceVariant = surfaceVariant, onSurfaceVariant = pickOn(surfaceVariant, if (dark) hsl(h, 0.1f, 0.78f) else hsl(h, 0.1f, 0.30f)),
            surfaceContainer = if (dark) hsl(h, tintD, 0.13f) else hsl(h, tint * 0.4f, 0.94f),
            surfaceContainerHigh = if (dark) hsl(h, tintD, 0.17f) else hsl(h, tint * 0.4f, 0.92f),
            outline = hsl(h, if (dark) 0.15f else 0.08f, if (dark) 0.55f else 0.45f),
        )
    }

    /**
     * An accent colour of the given hue that stands out from [bg] (contrast of at least 3): darker in the light theme and
     * lighter in the dark one until it does, which pale yellows and greens need.
     */
    private fun accent(h: Float, s: Float, startL: Float, bg: Int, dark: Boolean): Int {
        var l = startL
        var color = hsl(h, s, l)
        while (contrast(color, bg) < 3.0 && l in 0.05f..0.95f) {
            l += if (dark) 0.02f else -0.02f
            color = hsl(h, s, l)
        }
        return color
    }

    /** [preferred] if it reads on [bg] with at least [min] contrast, else whichever of white or black reads better. */
    private fun pickOn(bg: Int, preferred: Int, min: Double = 4.5): Int {
        if (contrast(preferred, bg) >= min) return preferred
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        return if (contrast(white, bg) >= contrast(black, bg)) white else black
    }

    /**
     * The colour of a small picture that suits a theme, chosen the way Material's own colour extraction does it: how much
     * of the picture each hue covers matters most, and vividness only breaks near-ties. So a big area of red beats a tiny
     * patch of bright green. Grey, black and white pixels are ignored when a colourful hue exists; with none, the plain
     * average of the picture is returned. [pixels] are ARGB values (nearly transparent ones are ignored). Null if empty.
     */
    fun dominantColor(pixels: IntArray): Int? {
        val bins = 360
        val count = IntArray(bins)
        val chromaSum = DoubleArray(bins)
        val rSum = DoubleArray(bins)
        val gSum = DoubleArray(bins)
        val bSum = DoubleArray(bins)
        var total = 0
        var ar = 0.0; var ag = 0.0; var ab = 0.0
        for (p in pixels) {
            if (((p ushr 24) and 0xFF) < 32) continue
            total++
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            ar += r; ag += g; ab += b
            val hsl = toHsl(p)
            val chroma = (1f - kotlin.math.abs(2f * hsl[2] - 1f)) * hsl[1] * 100f
            if (chroma < CUTOFF_CHROMA) continue
            val bin = hsl[0].toInt().coerceIn(0, bins - 1)
            count[bin]++
            chromaSum[bin] += chroma
            rSum[bin] += r; gSum[bin] += g; bSum[bin] += b
        }
        if (total == 0) return null
        var bestScore = Double.NEGATIVE_INFINITY
        var bestBin = -1
        for (h in 0 until bins) {
            // The share of the picture within 30 degrees of this hue, so nearby shades add up as one colour.
            var windowCount = 0
            var windowChroma = 0.0
            for (d in -15 until 15) {
                val i = (h + d + bins) % bins
                windowCount += count[i]
                windowChroma += chromaSum[i]
            }
            val proportion = windowCount.toDouble() / total
            if (proportion <= CUTOFF_PROPORTION) continue
            val chroma = windowChroma / windowCount
            val chromaScore = (chroma - TARGET_CHROMA) * (if (chroma < TARGET_CHROMA) 0.1 else 0.3)
            val score = proportion * 100.0 * 0.7 + chromaScore
            if (score > bestScore) {
                bestScore = score
                bestBin = h
            }
        }
        if (bestBin < 0) return argb(ar / total, ag / total, ab / total)
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0
        for (d in -15 until 15) {
            val i = (bestBin + d + bins) % bins
            r += rSum[i]; g += gSum[i]; b += bSum[i]; n += count[i]
        }
        return argb(r / n, g / n, b / n)
    }

    private const val CUTOFF_CHROMA = 8f
    private const val CUTOFF_PROPORTION = 0.01
    private const val TARGET_CHROMA = 50.0

    private fun argb(r: Double, g: Double, b: Double): Int =
        (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /**
     * [base] with its accent colours (the primary and secondary roles and their text colours) replaced by those of
     * [accentSeed]. The surfaces, text and outlines stay the base theme's, so the app keeps its look while the highlights
     * change, which is how Material keeps a theme stable while its accent varies.
     */
    fun withAccent(base: SeedColors, accentSeed: Int, dark: Boolean): SeedColors {
        val a = seedColors(accentSeed, dark)
        return base.copy(
            primary = a.primary, onPrimary = a.onPrimary, primaryContainer = a.primaryContainer, onPrimaryContainer = a.onPrimaryContainer,
            secondary = a.secondary, onSecondary = a.onSecondary, secondaryContainer = a.secondaryContainer, onSecondaryContainer = a.onSecondaryContainer,
            tertiary = a.tertiary, onTertiary = a.onTertiary,
        )
    }

    /** WCAG contrast ratio between two opaque colours (1 to 21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    /** [hue in degrees, saturation 0..1, lightness 0..1]. */
    fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val d = max - min
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return floatArrayOf(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
    }

    fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val hp = (((h % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }


}
