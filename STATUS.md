# STATUS (2026-11-26)

**Phase:** Scaffolded. Design approved by the owner on 2026-11-26. The project builds: a placeholder "Downbeat" screen, no real features yet. Public repo feerlessleadr/downbeat exists (empty); nothing is committed or pushed.

## Done this session
- Registered project; decisions in DECISIONS.md.
- Research, unauthenticated probe and authenticated sample run: `docs/music-assistant-api.md`, `docs/music-assistant-api-verified.md` (wins on conflicts).
- Design approved: `docs/design.md` (rooms list, Playing / Library / Search tabs, first-run room picker).
- Scaffold: Gradle wrapper 9.8.1, AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, minSdk 26, package `com.feerlessleadr.downbeat`, placeholder icon and screen, one smoke test. `:app:testDebugUnitTest :app:assembleDebug` passed (6 minutes cold). Not installed or run on a device or emulator.

## Next action
1. Owner: say "commit" (and "push") to commit the scaffold and docs and push them to the empty GitHub repo. Nothing is committed yet.
2. Build the connection layer: a small Kotlin websocket client (OkHttp + kotlinx.serialization) with token auth, command/response matching by `message_id` (a string in replies), and events. Unit tests with MockWebServer. Extend the sample script first to capture login, token creation and events (still unverified).
3. Then sign-in screen, rooms model and the Playing screen.

## Open questions
- TVs hidden by default with opt-in at first run (proposed in the design; confirm).
- Palette and real icon (placeholder in use).
- CI workflows (pull request build, signed release) not copied from dockge-for-android yet.

## Not yet verified
- `auth/login` and `auth/token/create` result shapes, event payloads, error replies, token expiry in practice.
- Dynamic grouping and `play_media` in practice (the sample run was read-only).
- The debug APK has never been installed or launched.

## Owner manual steps
- `%TEMP%\downbeat-samples` holds unredacted MAC addresses, player names and a user id. Delete it when done; revoke the probe token in the Music Assistant web UI if it was made only for this.
