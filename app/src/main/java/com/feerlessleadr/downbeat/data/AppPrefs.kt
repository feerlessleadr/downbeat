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
}

class MemoryAppPrefs(
    override var lastRoomId: String? = null,
    override var roomsConfigured: Boolean = false,
    override var hiddenRoomIds: Set<String> = emptySet(),
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
}
