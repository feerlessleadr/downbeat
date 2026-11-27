# Downbeat design (DRAFT v1, 2026-11-26, awaiting owner approval)

Goal: a sleek, simple Music Assistant remote. The official app's weakness is speaker and group selection, so that flow is the core of this design. Mockups were shown in chat; this file is the spec.

## Principles
- One concept for speakers: **rooms**. Hide players, queues, protocol players and group types (sync, permanent, universal). Show only players of type `player`, `stereo_pair`, `group`; drop `hide_in_ui`, `private`, disabled and (by default) unavailable ones.
- There is always a **current room set** (one or more rooms). Everything you play goes there. No separate "selected player" vs "queue".
- Three tabs only: **Playing**, **Library**, **Search**. A mini player on Library and Search. No side menu.
- Quiet Material 3: own palette (not wallpaper colors by default), large artwork, few controls on screen.

## Screens
1. **Playing**: top chip shows the current rooms ("Kitchen + Patio") and opens the Rooms sheet; queue icon opens the queue (reorder, remove, jump). Artwork, title, artist, seek bar, shuffle/previous/play-pause/next/repeat, one volume slider (group volume when several rooms).
2. **Rooms sheet** ("Play in"): one list of rooms. Each row: tick, name, short status ("Playing: Track", "Together with Kitchen", "Off"), volume control for ticked rooms. Ticking a room adds it to the current set (`players/cmd/set_members`, powers it on); unticking removes it. Tapping a room that is playing something else, while not ticked, offers "Play here instead" (switches the current set to that room, no queue juggling visible). A "Move music here" action is the same thing as `player_queues/transfer`.
3. **Library**: chips Albums, Artists, Playlists, Radio (Tracks reachable from search). Grid for albums/artists, list for others. Tapping an album/playlist/radio plays it in the current rooms (replacing the queue). Overflow on an item: Play next, Add to queue, Play in another room.
4. **Search**: one box, results grouped (Artists, Albums, Tracks, Playlists, Radio); same tap and overflow behavior.
5. **Settings** (from Playing's overflow): server address, sign in/out, theme, which rooms to hide.
6. **Sign-in**: server address, username and password (builtin provider). The app logs in, creates a long-lived token (expires after 1 year) and stores only the token (encrypted storage). On expiry or 401 it asks to sign in again.

## Mapping rooms onto the API (to verify with authenticated samples)
- Current set = the leader player (`synced_to` null) plus `group_members`. Effective queue id: `player_queues/get_active_queue(player_id)`.
- Play something: `player_queues/play_media(queue_id = leader, media = uri, option = replace)`.
- Grouping: `players/cmd/set_members(target_player = leader, player_ids_to_add/remove)`. If the user ticks a room that is currently playing elsewhere, that room leaves its old group first (server handles it).
- Volume: `players/cmd/volume_set` for one room, `players/cmd/group_volume` for the set.
- Buttons are enabled only if the player's `supported_features` allow (`pause`, `seek`, `next_previous`, `volume_set`, `power`).
- Live updates: events `player_updated`, `queue_updated`, `queue_time_updated`; elapsed time is derived from `elapsed_time` + `elapsed_time_last_updated`, not polled.

## Not in v1
Phone as a player, library management (add/remove, tagging), permanent group creation, audiobooks and podcasts, Android Auto. (Revised 2026-11-27: provider setup, server admin and lock screen controls are now in v1, see docs/roadmap.md M5 to M7. They sit behind Settings and show only to admin users, so the everyday screens stay unchanged.)

## Open design questions
- Should "Play" ever ask "Play where?" or always use the current rooms? (Draft: always use current rooms; the chip is always visible.)
- Last-used rooms when the app starts with nothing playing: default to the last set, or the first available room?
- Palette and icon: not designed yet.

## Revisions after the real server data (2026-11-26)
Source: `music-assistant-api-verified.md`. The owner's server has 18 players: many TVs and receivers, three static Chromecast groups whose members are also standalone players, two WiiM speakers, and one hidden web player.
- **First run asks which rooms to show**: one tick list of every non-hidden player, with TV-like players (only volume and members features) unticked by default. Also editable in Settings. Unavailable rooms are dimmed and not tickable.
- **Static groups appear as normal rooms**, listed after single rooms. Ticking a group selects it as a unit (it has its own queue and power). While a group is selected, its members show "In <group>" and are not individually tickable, since static groups cannot be edited. Ticking individual players uses dynamic `set_members`.
- **Controls follow `supported_features`**: hide pause, seek or next/previous when the room lacks them rather than show dead buttons.
- **Status text** uses `playback_state`: "Playing" only when it is `playing`; otherwise "Paused" or "Idle". Never trust a stale `current_media`.
- **Default room at start**: use the server's `activePlayerId` preference as a hint, falling back to the last room Downbeat used (stored locally).
- **Image URLs** carry the server's internal address; rewrite the host to the one the user typed.
