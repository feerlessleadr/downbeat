# STATUS (2026-11-26)

**Phase:** Sign-in and live rooms are verified on a real phone (owner, 2026-11-26). The rooms list is still a temporary screen; next is the real Playing screen. Pushed up to the sign-in and connection commit.

## Done this session
- Registered project; research; two authenticated sample runs: `docs/music-assistant-api.md`, `docs/music-assistant-api-verified.md` (wins on conflicts).
- Design approved: `docs/design.md`.
- Scaffold (Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, minSdk 26, package `com.feerlessleadr.downbeat`).
- `ma/`: `MaClient` (websocket), `Models`, `MaApi` (typed commands), `ImageUrls`, `MaAuth` (HTTPS `POST /auth/login`, shape taken from the server's openapi.json, not yet tried live).
- `data/`: `SessionStore` (`KeystoreSessionStore`: token encrypted with an Android Keystore AES-GCM key), `MusicRepository` (sign-in, long-lived token, connection loop with backoff, player events, queue re-read on playback change, sign-out on refused token), `Rooms` (which players count as rooms, honest status text).
- `ui/`: `SignInScreen`, `AppRoot` with a temporary rooms list; `DownbeatApp` wires it up.
- Bug found by a test and fixed: the client never noticed a server-initiated close (OkHttp needs `onClosing` answered), so reconnect would never have happened.
- 45 unit tests pass (4 real-sample tests skipped without the samples folder).

## Next action
1. Owner: install the debug APK on a phone and try signing in (steps in the last message). Report what happens, especially: does sign-in work, do the rooms and their states look right, does it survive turning wifi off and on.
2. Commit and push when the owner says so.
3. Rooms model and the Playing screen (design: tick-to-play-together sheet, room chip, controls gated by features), then Library and Search.

## Open questions
- TVs hidden by default with opt-in at first run (proposed in the design; confirm).
- Palette and real icon (placeholders in use).
- CI workflows (pull request build, signed release) not copied from dockge-for-android yet.

## Not yet verified
- Password sign-in (`POST /auth/login`), `auth/token/create` over a real login, and the Keystore token store on a device.
- Queue events; token expiry in practice.
- Every playback-changing command (`play_media`, `set_members`, volume, transport): none has been sent to the real server.
- `MediaItem` and search decoding against real data.
- (Resolved 2026-11-26) The owner installed the debug build on a phone and confirmed: sign-in works, rooms show with sensible states, it recovers after wifi off and on, and a volume change in the web UI shows in the app. Not separately checked: the Keystore store across an app restart, and the first-launch sign-in on a second device.

## Owner manual steps
- Install and test the debug APK (see Next action 1).
- In the Music Assistant web UI, delete the token `downbeat-probe-2` if you created it. After testing, you may also want to delete the token Downbeat creates for itself (named `Downbeat`) if you stop using it; Downbeat's sign-out only forgets the token on the phone, it does not revoke it on the server (not implemented yet).
