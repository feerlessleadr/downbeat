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
- Install the new debug build over the old one and test.
- In the Music Assistant web UI, delete the old token named exactly `Downbeat` (created before token names became unique) and the token `downbeat-probe-2` if you made it. Tokens named `Downbeat xxxxxx` are removed by sign-out.
