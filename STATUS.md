# STATUS (2026-11-26)

**Phase:** The Playing screen and rooms sheet are on the owner's phone in testing. Round 1 (2026-11-27) worked: now-playing info, play/pause, repeat, volume, room chip and sheet opening. Fixed after round 1 (rebuilt, not yet retested): TV as default room, missing next/previous, seek not draggable, shuffle icon not updating, slow reconnect after screen off. Group tick/untick, per-room volume, room switching and mixed groups are still untested (owner will report later). Security baseline pushed in `3bd8fc8`; the Playing-screen code is still uncommitted.

## Verified on a real phone (owner, 2026-11-26)
- Sign-in with password works (this also proves `POST /auth/login` and long-lived token creation), rooms show with sensible states, the app recovers after wifi off and on, and a volume change in the web UI shows in the app.

## Built, uncommitted
- `data/RoomLogic.kt`: effective leader (a follower resolves to its leader, a member of a playing static group to the group), default room (playing, else first single room), ticked set, row states, and what a tap does (add to set, remove, switch). `data/NowPlaying.kt`: title, artist, album, artwork, live position, and which buttons the leader supports. `data/PlaybackController.kt`: turns taps and buttons into server commands and returns a message on failure. Saved room choice in `data/AppPrefs.kt`.
- `ui/PlayingScreen.kt` (room chip, artwork, seek bar, shuffle, previous, play/pause, next, repeat, one volume slider), `ui/RoomsSheet.kt` ("Play in": tick rooms, volume per ticked room), `ui/AppRoot.kt` (three tabs; Library and Search are "coming next" placeholders), artwork loading with Coil (the token is sent only to the server's own host).
- Tests: 84 pass, 4 skipped without the samples folder. New: `RoomLogicTest` (24), `PlaybackControllerTest` (10), `UiHelpersTest` (5).

## Next action
1. Owner: install the new debug build and try the Playing screen (checklist in the last message). Report what works and what doesn't.
2. Commit and push when the owner says so.
3. Library and Search tabs (browse, search, tap to play in the current rooms, Play next / Add to queue menu), then the queue view, first-run room picker (TVs off by default) and settings.

## Open questions
- TVs hidden by default with opt-in at first run (proposed in the design; confirm).
- Palette and real icon (placeholders in use).
- CI workflows (pull request build, signed release) not copied from dockge-for-android yet.
- Sign-out does not revoke the Downbeat token on the server.

## Not yet verified
- Every playback-changing command on the real server: play/pause, next, previous, seek, shuffle, repeat, volume, group volume, `set_members` (tick/untick), `transfer` (switch while playing). None has been sent yet.
- Artwork: whether `/imageproxy` needs the token (the app sends it to the server's host) and whether the Bearer scheme is right.
- Queue events (the app re-reads a queue when a player's state or item changes, and relies on that).
- Removing the leader of a sync set is deliberately not offered; behaviour of `set_members` when members are Chromecast and WiiM mixed is unknown.
- `MediaItem` and search decoding against real data; token expiry in practice.

## Owner manual steps
- Install the new debug build over the old one (same command as before) and test.
- In the Music Assistant web UI, delete the token `downbeat-probe-2` if you created it, and the token named `Downbeat` once you stop testing (sign-out does not revoke it).
