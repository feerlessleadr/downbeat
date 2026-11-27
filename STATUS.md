# STATUS (2026-11-26)

**Phase:** Connection layer built and checked against real server data. Design approved 2026-11-26. The app itself is still a placeholder screen. Public repo feerlessleadr/downbeat has the scaffold (commit 07f5136). Everything after that is uncommitted.

## Done this session
- Registered project; research; two authenticated sample runs read by Claude: `docs/music-assistant-api.md`, `docs/music-assistant-api-verified.md` (wins on conflicts).
- Design approved: `docs/design.md` (rooms list, Playing / Library / Search tabs, first-run room picker).
- Scaffold (Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, minSdk 26, package `com.feerlessleadr.downbeat`). Debug APK builds; never installed or launched.
- Connection layer: `ma/MaClient.kt` (websocket, token auth, string message ids, partial batches, events, AuthFailed vs Disconnected) and `ma/Models.kt` (Player, PlayerQueue, QueueItem, PlayerMedia, ServerInfo). 17 unit tests pass (12 against a scripted fake server shaped like the real protocol, 3 that decode the real samples, 1 smoke, plus error and null-result cases). Not yet run live against the real server (the sample script did, the Kotlin client did not).
- Real error shape (numeric `error_code` plus `details`), token-create result (plain string) and `player_updated` events (full Player in `data`) are now verified.

## Next action
1. Owner: delete the samples folder (see below). Say "commit" and I commit and push the connection layer.
2. Build the typed API layer on `MaClient`: players, queues, play/pause/next/volume, `set_members`, `play_media`, search, library lists. Treat `null` results as "not found". Re-read a queue after player events (no queue events captured yet).
3. Sign-in screen: server address, username and password, `auth/login` (shape unverified: capture it by logging in once for real), then `auth/token/create`; store only the token, encrypted.
4. Rooms model and Playing screen.

## Open questions
- TVs hidden by default with opt-in at first run (proposed in the design; confirm).
- Palette and real icon (placeholder in use).
- CI workflows (pull request build, signed release) not copied from dockge-for-android yet.

## Not yet verified
- `auth/login` result shape; queue events; token expiry in practice.
- Dynamic grouping and `play_media` in practice (all sample runs were read-only).
- The debug APK has never been installed or launched.

## Owner manual steps
- Delete `%TEMP%\downbeat-samples` (it holds unredacted MAC addresses, player names and a user id): `Remove-Item -Recurse "$env:TEMP\downbeat-samples"`.
- In the Music Assistant web UI, delete the token `downbeat-probe-2` if you created it, and any other token you made only for probing.
