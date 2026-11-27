package com.feerlessleadr.downbeat.ui

/** What the Settings screen can do. (The app-wide three-dot menu is gone: Settings is a button on the bottom bar.) */
class AppMenuActions(
    val onAppearance: () -> Unit,
    val onProviders: () -> Unit,
    val onServer: () -> Unit,
    val onChooseRooms: () -> Unit,
    val onCustomizeBar: () -> Unit,
    val onSignOut: () -> Unit,
)
