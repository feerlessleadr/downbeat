package com.feerlessleadr.downbeat.data

import android.content.Context

/** Small non-secret settings that live on the phone. (The sign-in token is in [SessionStore], never here.) */
interface AppPrefs {
    /** The room the person last chose (a player id), or null. */
    var lastRoomId: String?

    /** True once the person has picked which rooms to show (the first-run picker, or later changes). */
    var roomsConfigured: Boolean

    /** Rooms the person chose not to see. A room that is new to the server is shown until hidden. */
    var hiddenRoomIds: Set<String>

    /** The bottom bar destinations in order (see [NavConfig]). */
    var navItems: List<String>

    /** light, dark or system (see [Themes]). */
    var themeMode: String

    /** A swatch id, "music" or "wallpaper" (see [Themes]). */
    var themePalette: String

    /** True when the accent colours follow the cover of what is playing. */
    var themeAccentMusic: Boolean

    /** The colour picked on the gradient (opaque ARGB). */
    var themeCustomColor: Int
}

class MemoryAppPrefs(
    override var lastRoomId: String? = null,
    override var roomsConfigured: Boolean = false,
    override var hiddenRoomIds: Set<String> = emptySet(),
    override var navItems: List<String> = NavConfig.DEFAULT,
    override var themeMode: String = Themes.SYSTEM,
    override var themePalette: String = Themes.DEFAULT_PALETTE,
    override var themeAccentMusic: Boolean = false,
    override var themeCustomColor: Int = Themes.DEFAULT_CUSTOM,
) : AppPrefs

class SharedAppPrefs(context: Context) : AppPrefs {
    private val prefs = context.getSharedPreferences("downbeat_prefs", Context.MODE_PRIVATE)

    override var lastRoomId: String?
        get() = prefs.getString("last_room", null)
        set(value) {
            prefs.edit().apply { if (value == null) remove("last_room") else putString("last_room", value) }.apply()
        }

    override var roomsConfigured: Boolean
        get() = prefs.getBoolean("rooms_configured", false)
        set(value) {
            prefs.edit().putBoolean("rooms_configured", value).apply()
        }

    override var hiddenRoomIds: Set<String>
        get() = prefs.getStringSet("hidden_rooms", emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("hidden_rooms", value).apply()
        }

    override var navItems: List<String>
        get() = NavConfig.decode(prefs.getString("nav_items", null))
        set(value) {
            prefs.edit().putString("nav_items", NavConfig.encode(NavConfig.sanitize(value))).apply()
        }

    override var themeMode: String
        get() = Themes.sanitizeMode(prefs.getString("theme_mode", null))
        set(value) {
            prefs.edit().putString("theme_mode", Themes.sanitizeMode(value)).apply()
        }

    override var themePalette: String
        get() = Themes.sanitizePalette(prefs.getString("theme_palette", null))
        set(value) {
            prefs.edit().putString("theme_palette", Themes.sanitizePalette(value)).apply()
        }

    // Settings saved by the first version of themes used the palette "music"; that becomes this switch being on.
    override var themeAccentMusic: Boolean
        get() = prefs.getBoolean("theme_accent_music", prefs.getString("theme_palette", null) == Themes.MUSIC)
        set(value) {
            prefs.edit().putBoolean("theme_accent_music", value).apply()
        }

    override var themeCustomColor: Int
        get() = prefs.getInt("theme_custom", Themes.DEFAULT_CUSTOM)
        set(value) {
            prefs.edit().putInt("theme_custom", value or (0xFF shl 24)).apply()
        }
}
