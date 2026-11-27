# downbeat

Simple, sleek Android client (Kotlin + Jetpack Compose, Material 3) for a Music Assistant server (music-assistant.io). Public repo (planned): feerlessleadr/downbeat. Standalone: no connections to other workspace projects.

- Read `STATUS.md` first. Search `DECISIONS.md` only when a past decision matters.
- Design goal: fewer screens and fewer options than other Music Assistant apps. When in doubt, leave the feature out or hide it.
- v1 scope: Now Playing + queue, browse + search library, multi-room / player groups. Not in v1: phone as a player, provider setup, server admin.
- External dependency (not a workspace connection): the Music Assistant server, over its websocket/REST API. Other open-source Music Assistant apps may be read for ideas; copy no code unless the license allows it and the owner agrees.
- Build (PowerShell): `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. `local.properties` (gitignored) holds `sdk.dir`. Package `com.feerlessleadr.downbeat` (debug builds add `.debug`). Stack versions are in `gradle/libs.versions.toml`, copied from dockge-for-android.
- Docs: `docs/design.md` (approved 2026-11-26), `docs/music-assistant-api.md` plus `docs/music-assistant-api-verified.md` (the verified one wins), `tools/Sample-MusicAssistant.ps1` (owner runs it; Claude never handles tokens).
- Public repo: no LAN IPs, real hostnames, usernames, keys, tokens or passwords in any file. Use `music.example.com`. Server address lives only in `C:\dev\_docs\WORKSPACE.md`, if at all.
- Never write the GitHub token anywhere. `gh` lives at `C:\Program Files\GitHub CLI\gh.exe` (not on PATH).
- Keystore lives outside the repo; secrets go in GitHub repo secrets only. The signing key must never change once released.
- Ask before destructive or externally visible actions (making the repo public, secrets, releases).
- Commits: small, `feerlessleadr <github@thecgkg.com>`, `git commit -F <file>`. Tags only when the owner says "publish it".
