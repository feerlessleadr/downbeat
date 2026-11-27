# Downbeat roadmap (2026-11-27)

Each milestone ends with a debug build the owner tests on a phone before the next one starts.

## M0. Foundation (done, pushed)
Project docs, design, security baseline, websocket client, typed commands, sign-in with encrypted token, reconnect.

## M1. Playing screen and rooms (done, pushed `21051c9`)
Playing tab (artwork, seek, shuffle, previous, play/pause, next, repeat, volume), room chip, "Play in" sheet (tick rooms, per-room volume, switch rooms), foreground reconnect check, queue-based position.
Still to verify by the owner: tick/untick grouping, per-room volume, switching while playing, mixed Chromecast and WiiM groups.

## M2. Rooms complete: power and first-run picker (next)
- **Power per room**: a power button on every row of the "Play in" sheet for rooms that list `power` (room name, state "Off" or "On"). Tapping toggles that speaker. Rooms that cannot power show no button.
- **Power per static group**: the group row has its own power button (a group has its own power and queue); members show "In <group>" and are not toggled individually.
- **Power for the current set**: one "Turn off everything playing here" action at the bottom of the sheet (powers off each ticked room, or the group). Playing in an off room powers it on (the server does this on `set_members`; verify on the real server).
- Off rooms are shown dimmed, not hidden. Power state follows live updates (`powered` on the player).
- **First-run room picker**: choose which rooms appear; TVs and receivers (players with only volume controls) are off by default. Changeable later in Settings (M5).
- Sign-out revokes the server token.

## M3b. Navigation overhaul (from the owner's first Library test, 2026-11-27)
- Floating mini player on every screen while something plays or is paused; tap it to open the full Playing screen.
- Customizable bottom bar (up to five of: Library, Playlists, Albums, Artists, Radio, Tracks, Search, Rooms, Playing).
- Rooms as its own destination, usable when nothing plays.
- Lists and open pages keep their place across tab switches and Back.
- Open: library artwork is blank. Waiting for the owner to run `tools/Sample-Images.ps1` to see how the server describes those images.

## M3. Library and Search (start music from the app)
- Library tab: Albums, Artists, Playlists, Radio, Tracks as simple lists with artwork; open an album, playlist or artist to see its tracks.
- Search tab: one box, results grouped by type.
- Tap to play in the rooms ticked in the sheet. Long-press or a menu: Play next, Add to queue, Start radio.
- Verify real-library decoding (needs an owner sample run if anything fails to read).

## M4. Queue (built 2026-11-27, awaiting the owner's phone test)
- Queue page from a "Queue" row on the Playing screen: the songs with the current one marked, tap to jump, menu per row (Move up, Move down, Remove), Clear queue with a confirmation.
- Later, maybe: drag to reorder and swipe to remove.

## M5. Lock screen and notification controls (built 2026-11-27, awaiting the owner's phone test)
- A media notification and lock-screen controls (play/pause, next, previous, seek, title, artist, artwork) for the room currently selected, using an Android media session that mirrors Music Assistant's state (the phone is a remote, not a player).
- Needs a foreground service of type media playback while a room plays, and the notification permission on Android 13+ (asked in context, not at launch).
- The service keeps the server connection alive in the background only while something plays; it stops when playback stops or the user dismisses it, so idle battery use stays near zero.
- Headset and Bluetooth media buttons control the selected room.
- To verify: behaviour when the phone sleeps (the connection check from M1 applies), and Android battery restrictions on the owner's phone.

## M6. Provider setup (admin accounts only): research done, slice 1 (read-only list) built, awaiting the owner's phone test
- Settings, then Providers: list configured music, player and metadata providers with status (loaded, error), reload, enable and disable, remove, and a library sync button per music provider.
- Add a provider: list available provider types, then a form built from the server's own config entries (text, password, toggles, choices, and sign-in links or codes where a provider needs them). Save, then show the result or the server's error.
- Hidden entirely for users who are not admins (the signed-in user's role decides).
- Research first (before any code): the exact provider and config commands and the config-entry shapes are not verified yet. The owner runs one more read-only sample script, as before. Providers that need a web sign-in (for example OAuth) get a hand-off to the browser and may be limited at first.

## M7. Server admin (admin accounts only)
- Settings, then Server: server version and status, library sync, users (list, add, remove, change role), long-lived tokens (list and revoke), player settings (rename, hide, enable and disable), and a view of recent server log lines.
- Anything destructive asks first and says what it will do.
- Hidden for non-admin users. Research and a sample run precede the code, as in M6. Exact scope is trimmed to what the real server's commands allow.

## M8. Polish and settings
- Real palette, launcher icon, dark and light themes, empty and error states.
- Settings screen: rooms shown, server address, sign out, about, with Providers and Server entries from M6 and M7 for admins.
- (The mini player moved up to M3b and is done.) The three-dot menu becomes a proper settings screen.

## M9. Release
- CI: pull request build, signed release APK (key from GitHub secrets, never changes once released).
- Required CI check on `main`.
- README with Obtainium install steps, first tag only when the owner says "publish it".

## Not in v1
Phone as a player (audio playing on the phone itself), permanent group creation, library editing (tagging, add and remove items), audiobooks and podcasts, Android Auto.

## Order rationale
M2 comes before M3 because power touches the screen already built and rooms must behave before more screens depend on them. M3 and M4 make the app usable on its own. Lock screen controls (M5) come next because they are used daily, while provider setup and server admin (M6, M7) are rare tasks, kept behind Settings and shown only to admins so the everyday screens stay simple. Polish and release stay last so they cover everything.
