# STATUS (2026-11-26)

**Phase:** Connection and command layers built. Design approved 2026-11-26. The app itself is still a placeholder screen. Pushed to feerlessleadr/downbeat: scaffold (07f5136) and the websocket client (61ae722). The typed API layer is uncommitted.

## Done this session
- Registered project; research; two authenticated sample runs read by Claude: `docs/music-assistant-api.md`, `docs/music-assistant-api-verified.md` (wins on conflicts).
- Design approved: `docs/design.md` (rooms list, Playing / Library / Search tabs, first-run room picker).
- Scaffold (Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, minSdk 26, package `com.feerlessleadr.downbeat`). Debug APK builds; never installed or launched.
- `ma/MaClient.kt` (websocket, token auth, string message ids, partial batches, events, AuthFailed vs Disconnected) and `ma/Models.kt`. Verified against real data: error shape (numeric `error_code` plus `details`), token-create result (plain string), `player_updated` events (full Player in `data`), 18 real players and queues decode.
- `ma/MaApi.kt` (players, queues, queue items, transport, shuffle/repeat, queue edit, play_media, volume, power, set_members, transfer, search, library lists, album/playlist/artist lookups), `ma/ImageUrls.kt` (artwork URL from `proxy_id`, host rewrite), `MediaItem`/`SearchResults` models. Command and argument names checked against the server's own `commands.json`.
- 29 unit tests pass (4 more real-sample tests are skipped because the samples folder was deleted, as requested).

## Next action
1. Commit the typed API layer when the owner says so.
2. App wiring: a repository that owns one `MaClient`, exposes players and queues as state, and re-reads a queue after `player_updated` (no queue events seen yet). Reconnect policy lives here (new `MaClient` per attempt).
3. Sign-in screen: server address, username and password, `auth/login` (result shape unverified; capture it by logging in once for real), then `auth/token/create`; store only the token, encrypted.
4. Rooms model (first-run room picker, static groups, feature gating) and the Playing screen.

## Open questions
- TVs hidden by default with opt-in at first run (proposed in the design; confirm).
- Palette and real icon (placeholder in use).
- CI workflows (pull request build, signed release) not copied from dockge-for-android yet.

## Not yet verified
- `auth/login` result shape; queue events; token expiry in practice.
- Every playback-changing command (`play_media`, `set_members`, volume, transport): the sample runs were read-only, so none has been sent to the real server.
- `MediaItem` and search decoding against real data: the one album sample read earlier matched the model, search items were not seen.
- The debug APK has never been installed or launched.

## Owner manual steps
- Samples folder is already deleted. To re-run real-data tests later, run the sample script again and set `$env:DOWNBEAT_SAMPLES` to its folder before `gradlew test`.
- In the Music Assistant web UI, delete the token `downbeat-probe-2` if you created it, and any other token you made only for probing.
