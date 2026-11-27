package com.feerlessleadr.downbeat.data

import android.content.Context

/** Small non-secret settings that live on the phone. (The sign-in token is in [SessionStore], never here.) */
interface AppPrefs {
    /** The room the person last chose (a player id), or null. */
    var lastRoomId: String?
}

class MemoryAppPrefs(override var lastRoomId: String? = null) : AppPrefs

class SharedAppPrefs(context: Context) : AppPrefs {
    private val prefs = context.getSharedPreferences("downbeat_prefs", Context.MODE_PRIVATE)

    override var lastRoomId: String?
        get() = prefs.getString("last_room", null)
        set(value) {
            prefs.edit().apply { if (value == null) remove("last_room") else putString("last_room", value) }.apply()
        }
}
