# STATUS (2026-11-27)

**Phase:** M2 (rooms complete: power, first-run room picker, revoke token on sign-out) is built and unit-tested (96 tests, 4 skipped without the samples folder) but not yet tried on a phone, and not committed. M0 and M1 are pushed (last pushed commit `881bf07`, the roadmap). Plan: `docs/roadmap.md`.

## Verified on a real phone (owner)
- Sign-in, rooms display, reconnect (also after screen off and wifi off/on, almost immediately), live volume.
- Now-playing info, artwork, play/pause, next/previous, seek (stays put, matches the web UI), shuffle and repeat icons, room chip and "Play in" sheet opening, volume slider.

## Built, uncommitted (M2)
- Power: a power button on each row of the "Play in" sheet (rooms that list `power` and are reachable, static groups included), "Off" shown and dimmed, and "Turn off everything playing here" at the bottom.
- First-run room picker (TVs and receivers unticked by default), reachable later from the Playing menu "Choose rooms". Hidden rooms never show or become the default.
- Sign-out revokes this install's server token (unique name per sign-in, stored in the encrypted session).

## M2 test results (owner, 2026-11-27)
- Works: room picker with TVs unticked, hidden rooms, "Choose rooms" menu, power buttons (backyard, inside, dobson speakers), switching a room off and on.
- Rooms without `power` in their features (Deck, Pool groups) show no button, as designed.
- Fixed after the test (rebuilt, not retested): "Turn everything off" now covers every visible room that is on (it skipped a second group before).
- Sign-out revoke not yet seen working: the owner's session predates token names. Retest after signing out and in once.

## M3 (Library and Search) test results (owner, 2026-11-27)
- Works: all lists load and page, radio is empty (nothing in the library), album and artist pages, Play, Shuffle (order shuffled on the web), play next, add to queue, search, Back up the page stack, playing into an off room.
- Library artwork was blank: the server answers HTTP 400 for image size 156 and accepts 256 and 512, so image sizes are rounded to those. Confirmed fixed by the owner; the temporary "Check artwork" menu item is removed.
- Owner confirmed the M3b changes (mini player, bottom bar, Rooms, kept places) and the new Stop button on the mini player work.
## M4 queue
- Owner confirmed on the phone (round 1): queue page, jump to a song, move, remove, add from Library, clear, live sync with the web UI. Moving the playing song gave "0 is already played/buffered".
- Round 2 (owner): drag and drop works up and down; jump to a song works; remove and clear work. Problems found: no continuous scroll while dragging, silent snap-back when dropping above the playing song, queue list not returning to the top after a jump, and the inline queue design was rejected.
- Rebuilt (118 tests pass, not yet tried on a phone, uncommitted): artwork-first Playing screen with a "Playing on" card and one "Up next" line that opens the queue page; continuous auto-scroll while dragging; message when dropping above the playing song; the queue scrolls to the top when the playing song changes; Settings is a fixed last button on the bottom bar (the three-dot menus are gone; up to four customizable buttons); list thumbnails use a fixed decode size to cut first-load jerkiness (not confirmed fixed).
- Round 3 (owner): artwork-first layout, Up next, queue page, drag with edge scrolling, Settings, Bottom bar editor all work. Problems: squeezed bottom, no song count on the Up next line, queue showed one song after jumping, arrows in the editor, no sign-out confirmation, and a request for an artist/album background.
- Rebuilt (121 tests pass, not yet tried on a phone, uncommitted): spacing spread between groups, count on the Up next line, the whole queue shown (played songs dimmed, scrolled to the playing song), drag handles in the Bottom bar editor, sign-out confirmation, and a blurred artist-or-album background on the Playing screen (dark scheme).
- Round 4 (owner): spacing, Up next count, background, whole queue, drag editor, sign-out confirmation all work. The background was too blurred.
- Rebuilt (121 tests pass, not yet tried on a phone, uncommitted): background blur reduced (10 dp, lighter scrim); no message when music starts (Play next and Add to queue still confirm); Playing screen gestures: swipe the cover left for next, right for previous, swipe down anywhere to minimise (only when opened from the player bar).
- Round 5 (owner): clearer background good, no start message good, add to queue and play next still confirm (kept), swipe next/previous and down worked but snapped back or closed with no animation, and a small wiggle closed the screen.
- Rebuilt (121 tests pass, not yet tried on a phone, uncommitted): the cover slides off and the new cover slides in; the Playing screen is a layer that slides up and down and follows the finger on a swipe down (140 dp threshold).
- Round 6 (owner): better, but a full-screen black trailed the screen on a swipe down, nothing showed underneath, the next cover should appear right beside the current one, and a swipe up on the player bar should open the screen.
- Rebuilt (121 tests pass, not yet tried on a phone, uncommitted): one sliding layer shared by tap, swipe up on the player bar, close arrow, Back and swipe down (the app shows underneath, no black trail); a cover carousel with the previous and next covers beside the current one.
- Round 7 (owner): all animations work; still a small black bar at the top of the Playing screen; the screen should open out of the player bar; swipe down should complete after about an eighth of the screen; Library needs swiping between chips and a nicer top.
- Rebuilt (121 tests pass, not yet tried on a phone, uncommitted): picture under the status bar (no black strip, light status icons), the Playing screen grows out of and shrinks back into the player bar, swipe-down completes after an eighth of the screen, Library kinds are swipeable pages with underlined tabs and a large gradient title.
- Round 8 (owner): no black strip any more; Library tabs and swiping work; the Playing screen reveal looked worse because it wiped the screen instead of moving it; asked for colour themes including colours that follow the music.
- Rebuilt (128 tests pass, not yet tried on a phone, uncommitted): the Playing screen moves as one piece (small at the player bar, grows and travels to full size, reverse on close); Settings, Appearance with mode and colours (named colours, Phone colours, Match the music).
- Round 9 (owner, screenshots): the scale-from-the-bar version was rejected; the screen must move as one rigid piece. Changed to a plain full-size vertical slide whose top edge travels between the player bar's top and the window's top (no scaling, no clipping). Appearance and colour themes not yet reported on.
- Round 10 (owner): the slide is fine; Appearance works; asked how the colour is chosen (a small green patch won), whether colours return to base when playback stops, and suggested a base theme with a music-following accent.
- Rebuilt (132 tests pass, not yet tried on a phone, uncommitted): cover colour chosen by hue population like Material's Score; Appearance now has Mode, a "Follow the music" accent switch, and base colours; surfaces stay on the base, accents follow the music and fade back to the base when playback stops.
- Round 11 (owner): base colours were washed out in dark mode (light mode was fine); asked for a colour picker.
- Rebuilt (135 tests pass, not yet tried on a phone, uncommitted): richer dark theme (saturated mid-tone accents, clearly tinted surfaces); a "Pick your own" rainbow bar under Base colours in Appearance.
- Round 12 (owner): better but still washed out, "as if everything has a dark tint".
- Rebuilt (135 tests pass, not yet tried on a phone, uncommitted): the default purple surface tint is removed, surfaces carry only a faint hint of colour, dark accents are more vivid, and the overlay on the Playing screen picture is lighter.
- Round 13 (owner): softer tint is way better and the picker works; the Playing screen is now harder to read; the Library tabs sit too close to the list.
- Rebuilt (135 tests pass, not yet tried on a phone, uncommitted): stronger overlay on the Playing screen only; a divider and space under the Library tabs. The colour rules are written up in `C:\dev\_docs\DESIGN-STANDARDS.md` for future apps.
- Round 14 (owner): everything looks good; the fade back to the base colours after the music stops was too sharp. Rebuilt: eased fades, 1.4 s between covers and 3.4 s back to the base (not yet tried on a phone, uncommitted).
- Round 15 (owner): all good; committed and pushed as `c53f784` (everything through the colour themes).
## M5 lock screen and notification controls: pushed as `9a67e39`
- Android media session and notification in a foreground service while the chosen rooms play or pause: title, artist, cover, position, previous, play or pause, next, stop; lock screen seek bar and headset or Bluetooth buttons work; stops 30 s after playback ends; a Settings switch turns it off; the notification permission is asked once (Android 13 and later).
- Owner test (OnePlus): shade, lock screen, cover, seek bar, next, previous, stop and open-on-tap work; play and pause did not seem to respond and next seemed to start the song twice (probably the phone sending each tap twice). Fixed in code, not yet retested: explicit play and pause, and a 900 ms guard against repeated commands (146 tests pass).
- Owner (round 2): the notification buttons are fine; the "starts twice" on next seems to be the server taking a moment to load. Added one app-side fix: a queue that still describes the old song no longer drives the play position after a skip (147 tests pass, not yet retested).
- Still to report by the owner: long-running behaviour with the screen off, and changing rooms while the notification is showing.
## M3b navigation overhaul: built (114 tests pass), not yet tried on a phone, uncommitted
- Mini player on every screen while playing or paused (tap opens the Playing screen), customizable bottom bar (default Library, Playlists, Search, Rooms; Customize bottom bar is in the three-dot menu), Rooms destination, places kept across tab switches and Back, message when Shuffle starts.
- Unverified: all of it on a phone; library artwork (see above).

## Next action
1. Owner: install the build and test M2 (checklist in the last message).
2. Commit and push when the owner says so.
3. M3 Library and Search, then M4 queue, M5 lock screen controls, M6 provider setup, M7 server admin, M8 polish and settings, M9 release.

## Open questions
- Palette and real icon (placeholders in use).
- CI workflows not copied from dockge-for-android yet (M9).
- Does playing into a powered-off room switch it on by itself? (Assumed from the server's set_members docs; verify.)

## Not yet verified
- Power on/off (single room, static group, "turn off everything"), token revoke on sign-out, the first-run picker flow.
- Group tick/untick (`set_members`), per-room volume, switching rooms while playing (`transfer`), mixed Chromecast and WiiM groups: owner will report.
- Queue events (the app re-reads queues after commands, every 10 s while playing, and on player changes).
- `MediaItem` and search decoding against real data; token expiry in practice.

## Owner manual steps
- Library artwork: the owner cannot run scripts, so the app has a temporary menu item, three-dot menu then "Check artwork", that shows how the server describes images and the status of one image request with and without the token (no token or address shown). Send a screenshot of it. Remove this item and `MusicRepository.artworkReport` once artwork works. (`tools/Sample-Images.ps1` does the same from a PC.)
- Install the new debug build over the old one and test.
- In the Music Assistant web UI, delete the old token named exactly `Downbeat` (created before token names became unique) and the token `downbeat-probe-2` if you made it. Tokens named `Downbeat xxxxxx` are removed by sign-out.

## M6 provider setup: slice 1 pushed as `87792f6` and confirmed on the phone; slice 2 (actions) built, 160 tests pass, not yet tried on a phone, uncommitted
- Research written up in `docs/provider-setup-research.md` (from the server's public API description). Settings now shows "Music sources and players" for administrator accounts: a read-only list of every provider with its status and any error. Slices 2 to 4 (actions, edit forms, adding through the setup flow) come after the owner checks slice 1.
- To verify on the phone: that the row appears for the owner's account, that the list matches the web UI (names, kinds, statuses), and what a Problem looks like.
- Slice 2 (actions) pushed as `ea20c9a` and confirmed on the phone.
- Slice 3 (settings form) pushed as `a3cebf5` and confirmed on the phone.
- Slice 4 (add a provider) pushed as `e38b964`; M6 is done pending the owner's own tests of adding a real provider.

## M7 server admin: slice 1 built (186 tests pass, not yet tried on a phone, uncommitted)
- Research in `docs/server-admin-research.md`. Settings now has a "Server" row (administrators) showing: this server, background tasks, users, access tokens (this phone marked) and the log on demand. Read-only. To verify on the phone: each section against the web UI, and what any section says if it cannot be read.
