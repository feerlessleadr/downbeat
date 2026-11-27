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
