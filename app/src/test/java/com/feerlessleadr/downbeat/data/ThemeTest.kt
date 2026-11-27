package com.feerlessleadr.downbeat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    private val seeds = listOf(
        0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(),
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(), 0xFF7B61D6.toInt(), 0xFFFF9800.toInt(),
    ) + Themes.SWATCHES.map { it.argb }

    @Test fun textColoursStayReadableOnTheirBackgroundsForAnySeed() {
        for (seed in seeds) for (dark in listOf(true, false)) {
            val c = ColorMath.seedColors(seed, dark)
            val label = "seed=${Integer.toHexString(seed)} dark=$dark"
            assertTrue("$label onPrimary", ColorMath.contrast(c.onPrimary, c.primary) >= 4.5)
            assertTrue("$label onPrimaryContainer", ColorMath.contrast(c.onPrimaryContainer, c.primaryContainer) >= 4.5)
            assertTrue("$label onSecondaryContainer", ColorMath.contrast(c.onSecondaryContainer, c.secondaryContainer) >= 4.5)
            assertTrue("$label onSurface", ColorMath.contrast(c.onSurface, c.surface) >= 7.0)
            assertTrue("$label onSurfaceVariant", ColorMath.contrast(c.onSurfaceVariant, c.surfaceVariant) >= 4.5)
            assertTrue("$label primary on surface", ColorMath.contrast(c.primary, c.surface) >= 3.0)
        }
    }

    @Test fun darkThemesAreDarkAndLightThemesAreLight() {
        for (seed in seeds) {
            assertTrue(ColorMath.toHsl(ColorMath.seedColors(seed, true).surface)[2] < 0.2f)
            assertTrue(ColorMath.toHsl(ColorMath.seedColors(seed, false).surface)[2] > 0.9f)
        }
    }

    @Test fun hslRoundTripsAndKeepsTheHue() {
        val red = ColorMath.hsl(0f, 1f, 0.5f)
        assertEquals(0xFFFF0000.toInt(), red)
        assertEquals(0xFF00FF00.toInt(), ColorMath.hsl(120f, 1f, 0.5f))
        val hsl = ColorMath.toHsl(0xFF1E88E5.toInt())
        assertEquals(208f, hsl[0], 2f)
    }

    @Test fun theDominantColourIsTheVividOneNotTheBackground() {
        // Mostly white with some strong blue: the blue wins.
        val pixels = IntArray(100) { if (it < 20) 0xFF1E5BD6.toInt() else 0xFFFFFFFF.toInt() }
        val dominant = ColorMath.dominantColor(pixels)!!
        val hsl = ColorMath.toHsl(dominant)
        assertTrue("hue ${hsl[0]}", hsl[0] in 200f..235f)
        assertTrue(hsl[1] > 0.4f)
    }

    @Test fun aBigAreaOfOneHueBeatsATinyPatchOfAVividOne() {
        // 60 percent muted red-brown, 3 percent vivid green, the rest black: the red wins (the green is only 3 percent).
        val pixels = IntArray(100) {
            when {
                it < 60 -> ColorMath.hsl(10f, 0.5f, 0.40f)
                it < 63 -> ColorMath.hsl(120f, 1.0f, 0.50f)
                else -> 0xFF000000.toInt()
            }
        }
        val hue = ColorMath.toHsl(ColorMath.dominantColor(pixels)!!)[0]
        assertTrue("hue $hue", hue < 30f || hue > 340f)
    }

    @Test fun aHueThatCoversAReasonableShareStillWinsOverMuddyColours() {
        // 30 percent vivid green, 70 percent grey: green is the only colourful hue.
        val pixels = IntArray(100) { if (it < 30) ColorMath.hsl(130f, 0.8f, 0.45f) else 0xFF808080.toInt() }
        val hue = ColorMath.toHsl(ColorMath.dominantColor(pixels)!!)[0]
        assertTrue("hue $hue", hue in 115f..145f)
    }

    @Test fun aTinyColourOnAMostlyGreyCoverIsStillUsedWhenItIsAboveTheCutoff() {
        // 5 percent blue on grey is above the 1 percent cutoff, so blue is chosen over plain grey.
        val pixels = IntArray(100) { if (it < 5) ColorMath.hsl(220f, 0.9f, 0.5f) else 0xFF808080.toInt() }
        assertTrue(ColorMath.toHsl(ColorMath.dominantColor(pixels)!!)[0] in 205f..235f)
    }

    @Test fun accentColoursOverTheBaseKeepTheBaseSurfacesAndStayReadable() {
        for (baseSeed in Themes.SWATCHES.map { it.argb }) for (accentSeed in seeds) for (dark in listOf(true, false)) {
            val base = ColorMath.seedColors(baseSeed, dark)
            val mixed = ColorMath.withAccent(base, accentSeed, dark)
            val label = "base=${Integer.toHexString(baseSeed)} accent=${Integer.toHexString(accentSeed)} dark=$dark"
            assertEquals(label, base.surface, mixed.surface)
            assertEquals(label, base.onSurface, mixed.onSurface)
            assertEquals(label, base.surfaceContainer, mixed.surfaceContainer)
            assertTrue("$label onPrimary", ColorMath.contrast(mixed.onPrimary, mixed.primary) >= 4.5)
            assertTrue("$label primary on surface", ColorMath.contrast(mixed.primary, mixed.surface) >= 2.8)
        }
    }

    @Test fun aGreyPictureGivesItsAverageAndNothingGivesNull() {
        val grey = ColorMath.dominantColor(IntArray(10) { 0xFF808080.toInt() })!!
        assertTrue(ColorMath.toHsl(grey)[1] < 0.05f)
        assertNull(ColorMath.dominantColor(IntArray(5) { 0x00000000 }))
        assertNull(ColorMath.dominantColor(IntArray(0)))
    }

    @Test fun aPickedColourBecomesTheBasePaletteAndIsSaved() {
        val prefs = MemoryAppPrefs()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = MusicRepository(OkHttpClient(), object : SessionStore {
            override fun load(): Session? = null
            override fun save(session: Session) {}
            override fun clear() {}
        }, scope, prefs = prefs)
        repo.setCustomColor(0x00E8742A)  // no alpha given: it is made opaque
        assertEquals(Themes.CUSTOM, repo.theme.value.palette)
        assertEquals(0xFFE8742A.toInt(), repo.theme.value.customColor)
        assertEquals(0xFFE8742A.toInt(), prefs.themeCustomColor)
        assertEquals(Themes.CUSTOM, prefs.themePalette)
        assertEquals(Themes.CUSTOM, Themes.sanitizePalette("custom"))
        scope.cancel()
    }

    @Test fun everyHueOfThePickerGivesReadableColoursInBothModes() {
        for (hue in 0..359 step 15) for (dark in listOf(true, false)) {
            val c = ColorMath.seedColors(ColorMath.hsl(hue.toFloat(), 0.85f, 0.5f), dark)
            assertTrue("hue $hue dark=$dark onPrimary", ColorMath.contrast(c.onPrimary, c.primary) >= 4.5)
            assertTrue("hue $hue dark=$dark primary on surface", ColorMath.contrast(c.primary, c.surface) >= 3.0)
            assertTrue("hue $hue dark=$dark onSurface", ColorMath.contrast(c.onSurface, c.surface) >= 7.0)
        }
    }

    @Test fun darkSurfacesAreOnlyFaintlyTintedAndAccentsAreVivid() {
        // A saturated blue seed: the dark surface carries only a faint hint of colour (between 0.08 and 0.2), not a heavy tint.
        val surface = ColorMath.seedColors(ColorMath.hsl(220f, 0.85f, 0.5f), true).surface
        assertTrue(ColorMath.toHsl(surface)[1] in 0.08f..0.2f)
        // And the dark accent is a mid-tone, not a pastel.
        val primary = ColorMath.seedColors(ColorMath.hsl(220f, 0.85f, 0.5f), true).primary
        assertTrue(ColorMath.toHsl(primary)[2] < 0.72f)
        assertTrue(ColorMath.toHsl(primary)[1] >= 0.75f)
    }

    @Test fun unknownThemeValuesFallBackToTheDefaults() {
        assertEquals(Themes.SYSTEM, Themes.sanitizeMode("neon"))
        assertEquals(Themes.DARK, Themes.sanitizeMode("dark"))
        assertEquals(Themes.DEFAULT_PALETTE, Themes.sanitizePalette("bogus"))
        // The old "music" palette is no longer a base colour (it became the accent switch).
        assertEquals(Themes.DEFAULT_PALETTE, Themes.sanitizePalette("music"))
        assertEquals(Themes.WALLPAPER, Themes.sanitizePalette("wallpaper"))
        assertEquals("ocean", Themes.sanitizePalette("ocean"))
    }

    @Test fun theRepositoryKeepsAndSavesTheChoices() {
        val prefs = MemoryAppPrefs()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = MusicRepository(OkHttpClient(), object : SessionStore {
            override fun load(): Session? = null
            override fun save(session: Session) {}
            override fun clear() {}
        }, scope, prefs = prefs)
        assertEquals(ThemeSettings(Themes.SYSTEM, "violet", false), repo.theme.value)
        repo.setThemeMode("dark")
        repo.setThemePalette("ocean")
        repo.setAccentFollowsMusic(true)
        assertEquals(ThemeSettings("dark", "ocean", true), repo.theme.value)
        assertEquals("dark", prefs.themeMode)
        assertEquals("ocean", prefs.themePalette)
        assertTrue(prefs.themeAccentMusic)
        repo.setThemePalette("junk")
        assertEquals(Themes.DEFAULT_PALETTE, repo.theme.value.palette)
        assertNotNull(repo.theme.value)
        scope.cancel()
    }
}
