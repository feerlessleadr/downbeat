# DECISIONS (append-only, newest at the bottom)

### 2026-11-26, Start Downbeat: a simple Music Assistant client
- **Context**: Owner wants an Android app for Music Assistant, named Downbeat, that may borrow functionality from other open-source Music Assistant apps but is simple and sleek to use.
- **Options considered**: Backend = Music Assistant server only, Emby music library only, or both. Scope = remote control only versus also playing audio on the phone.
- **Decision**: Backend is the Music Assistant server only. v1 = Now Playing + queue, browse + search library, multi-room / player groups. Phone-as-player, provider setup and server admin are out of v1. Stack: Kotlin, Jetpack Compose (Material 3), minSdk 26, like the owner's other apps. Standalone, public repo feerlessleadr/downbeat (not created yet).
- **Rationale**: A focused remote is what "simple and sleek" asks for; Emby is already covered by Embidextrous and mixing sources would add scope. Same stack reuses known tooling.
- **Impact**: New folder `C:\dev\downbeat`; WORKSPACE.md updated. No change to other projects. External dependency only: the Music Assistant server.

### 2026-11-26, Package name, repo, distribution, and the speaker-picking problem
- **Context**: Owner answered the open questions and said the official Music Assistant Android app is the most feature complete, but selecting speakers/groups and deciding what plays where is convoluted.
- **Decision**: Package `com.feerlessleadr.downbeat`. Public repo feerlessleadr/downbeat created 2026-11-26 (empty, nothing pushed). Sideload via Obtainium like the other apps. The official app is the feature reference; Downbeat's main differentiator is a much simpler "play here / on these speakers" flow.
- **Rationale**: Owner's stated pain point with the existing app.
- **Impact**: STATUS.md open questions closed; research in `docs/music-assistant-api.md`.

### 2026-11-26, Verified server API and drafted the "rooms" design
- **Context**: Owner supplied the server address (kept only in WORKSPACE.md) and asked to verify the API, then design.
- **Decision**: Unauthenticated probe confirmed server 2.10.13, schema 65, builtin and Home Assistant auth providers, and exact command/schema names (`docs/music-assistant-api.md` section 6). Corrected the research: `PlayerType` has no `multi_channel`. Authenticated checks are left to `tools/Sample-MusicAssistant.ps1`, run by the owner, so Claude never handles the password or token. Draft design in `docs/design.md`: one "rooms" list with tick-to-play-together, three tabs (Playing, Library, Search), always play to the current rooms.
- **Rationale**: Removes the player/queue/group-type concepts that make the official app convoluted. Probe script keeps credentials with the owner.
- **Impact**: New `tools/` and `docs/design.md`; WORKSPACE.md connection 18 now records the address.

### 2026-11-26, Design revised after the authenticated run
- **Context**: The owner ran the sample script; the real server has 18 players (TVs, static Chromecast groups, WiiM speakers, a hidden web player) with very different feature sets.
- **Decision**: Rooms list gets a first-run "which rooms to show" step (TVs off by default), static groups shown as normal rooms and locked while selected, controls gated on `supported_features`, status from `playback_state`, default room hinted by the server's `activePlayerId`. Findings recorded in `docs/music-assistant-api-verified.md`.
- **Rationale**: The real data shows a naive "list every player" screen would be cluttered and some buttons would be dead.
- **Impact**: `docs/design.md` revision section; no other project affected.

### 2026-11-26, Design approved and project scaffolded
- **Context**: Owner approved `docs/design.md` and asked to scaffold.
- **Decision**: Copied the build conventions from dockge-for-android (wrapper, AGP, Kotlin, Compose BOM, minSdk 26, JVM 17, release signing from environment only, debug-signed release blocked). Left out Socket.IO and Navigation for now (Music Assistant uses a plain websocket; navigation is three tabs and can be added when needed). Cleartext traffic is off in the manifest, so the app needs an HTTPS server address.
- **Rationale**: Known-good tooling; fewer dependencies until they are needed.
- **Impact**: New Gradle project in `C:\dev\downbeat`; builds. CI workflows and a real icon still to do.

### 2026-11-26, Connection layer design
- **Context**: First real code: the Music Assistant websocket client.
- **Decision**: One single-use `MaClient` per session (reconnect = new client, decided by the caller). Models keep enums as strings and give every field a default so a newer server cannot break parsing. Event data stays raw JSON until payload shapes are verified. The client accepts several error-field names because the real error reply has not been captured. Auth rejection is a separate state (`AuthFailed`) from a dropped connection so the UI can ask for a new sign-in instead of retrying forever. Server URL is turned into `<typed address>/ws` and OkHttp upgrades it, so https stays https.
- **Rationale**: Unverified parts of the protocol are isolated and tolerant; tests use a scripted fake server shaped like the verified protocol.
- **Impact**: `app/.../ma/` and its tests; sample script extended to capture the missing facts.

### 2026-11-26, Real-sample tests and null results
- **Context**: Second sample run showed `error_code` is numeric, unknown player ids give `result: null` instead of an error, token creation returns a plain string, and only `player_updated` events were captured.
- **Decision**: Keep error codes as strings in `MaException` (numeric codes are converted). Treat a null result as a normal answer ("not found"), not an exception. Added `RealSamplesTest`, which decodes the owner's local sample files when `DOWNBEAT_SAMPLES` is set and skips otherwise, so real data never enters the repo. Until queue events are seen, the app re-reads the affected queue after player events.
- **Rationale**: Tests against real shapes catch model drift without committing device data.
- **Impact**: `MaClientTest` (two new tests), `RealSamplesTest`, docs.

### 2026-11-26, Sign-in, token storage and connection ownership
- **Context**: Needed a way to sign in without ever storing the password, and one place that owns the live connection.
- **Options considered**: Login over the websocket versus a plain HTTPS POST; keep the 30-day login token versus swap it for a 1-year long-lived token; androidx security-crypto versus the Android Keystore directly; a ViewModel per screen versus one app-wide repository.
- **Decision**: Login is HTTPS `POST /auth/login` (the server documents it in openapi.json). After login the app opens one websocket, calls `auth/token/create` for a long-lived token, and stores only that (falling back to the login token if creation fails). The token is encrypted with an AES-GCM key in the Android Keystore (no deprecated security-crypto library). http addresses are refused (`requireHttps`, and cleartext is also off in the manifest). One `MusicRepository` owns one `MaClient` at a time, retries with 1 to 30 second backoff, and signs the user out when the server refuses the token. Sign-out forgets the token locally but does not revoke it on the server yet.
- **Rationale**: The password exists only in one request body. A long-lived token survives a month of not opening the app. One owner of the connection keeps screens simple and reconnect logic in one tested place.
- **Impact**: `data/`, `ma/MaAuth.kt`, `ui/`, manifest (`DownbeatApp`). Revoking the token on sign-out is a to-do.

### 2026-11-26, Client must answer a server-initiated close
- **Context**: A repository test showed no reconnect after the server dropped the socket.
- **Decision**: `MaClient` answers `onClosing` and reports Disconnected immediately.
- **Rationale**: OkHttp only calls `onClosed` after the client answers, so a server restart would have left the app showing stale rooms forever.
- **Impact**: `ma/MaClient.kt` plus a test.

### 2026-11-26, Playing screen: leader resolution, tap rules, controls
- **Context**: Turning the approved rooms design into behaviour.
- **Decision**: Pure functions in `RoomLogic` decide everything about rooms. The leader is `active_group`, else `synced_to`, else the chosen room. A tap on an unticked room adds it to the set only if both are single players with `set_members` and the room is in the leader's `can_group_with`; otherwise it switches to that room (and moves the queue with `transfer` if something is playing). Static group members are locked. The leader of a sync set cannot be unticked (untick the others); a lone room cannot be unticked. Default room: one that is playing, else the first single room, never a static group by surprise. Buttons are shown only if the leader's `supported_features` allow them. Position is elapsed time plus time since it was recorded, while playing. Group volume is used when several rooms play together. Transport uses the leader's queue id. Artwork uses Coil, with the token added only for the server's own host.
- **Rationale**: Keeps the "one list, one gesture" promise while respecting what the server verifiably allows; everything testable without a phone.
- **Impact**: `data/RoomLogic.kt`, `NowPlaying.kt`, `PlaybackController.kt`, `AppPrefs.kt`, `ui/*`, new dependencies `coil-compose` and `coil-network-okhttp` 3.3.0. Open: if the server needs a different image auth, adjust `DownbeatApp.newImageLoader`.

### 2026-11-27, Repository security baseline
- **Context**: Public repo; owner asked for all required protections before further work.
- **Options considered**: Require pull requests and reviews on main (rejected: solo project, pushes go straight to main); only block force-push and deletion.
- **Decision**: Enabled on GitHub: secret scanning and push protection (already on), vulnerability alerts, Dependabot security updates, private vulnerability reporting, delete branch on merge, and branch protection on main (no force push, no deletion, admins not enforced). Added `SECURITY.md`, `.github/dependabot.yml` (Gradle and Actions, weekly), and more secret patterns in `.gitignore`. Audit found no secrets, hosts or IPs in files or history; the commit email is the approved public identity.
- **Rationale**: Cheap protections against leaked secrets, vulnerable dependencies and accidental history loss, without slowing a solo workflow.
- **Impact**: Add a required CI status check to main protection once the pull request workflow exists. Manifest already has `allowBackup=false` and cleartext off.
