# Server admin: research (2026-11-27)

Source: the server's public API description (`/api-docs/commands.json` and `schemas.json`, server 2.10.13). **Nothing was
tried against the real server**; marked "unverified" items must be checked on a phone first. Roadmap: M7. Same rules as M6
(`provider-setup-research.md`): administrators only, no change on the owner's server during testing unless the owner says
so, never log values or secrets.

## What the server offers (scope in brackets)
- **About**: `info` returns `ServerInfoMessage`: `name`, `server_version`, `schema_version`, `status`, `onboard_done`,
  `homeassistant_addon`, `has_remote_access`, plus `base_url`, `internal_url` and `external_url` (addresses: never shown in
  the app by default, they are private). `time` returns the server clock.
- **Background tasks** (`system.read` to read, `system.manage` to change): `tasks/list`, `tasks/get`, `tasks/log(task_id)` (the
  task's own log text), `tasks/run` (run next), `tasks/cancel`, `tasks/retry`, `tasks/remove`, `tasks/clear_finished`,
  `tasks/set_enabled(task_id, enabled)` and `tasks/update_schedule` (recurring tasks). A `BackgroundTask` has `id`, `name`,
  `status`, `progress`, `progress_text`, `last_run`, `next_run`, `last_error`, `failure_count`, `schedule`, `allow_retry`,
  `allow_cancel`. Library syncs (`music/sync`, `library.manage`) return tasks.
- **Users** (`users.read`, `users.manage`): `auth/users`, `auth/user`, `auth/user/create(username, password, role?,
  display_name?, player_filter?, provider_filter?)`, `auth/user/update(user_id, ... role, password, filters)`,
  `auth/user/enable|disable|delete(user_id)`. A `User` has `user_id`, `username`, `role` (a string; the valid values are not
  listed: unverified), `enabled`, `display_name`, `created_at`, `player_filter`, `provider_filter` (limit what a user sees).
- **Tokens**: `auth/tokens(user_id?)` (another user's tokens need an administrator), `auth/token/revoke(token_id)`,
  `auth/token/create`. An `AuthToken` has `token_id`, `name`, `created_at`, `expires_at`, `last_used_at`, `is_long_lived`
  (the server's token hash is also in the schema and must never be shown).
- **Players** (`config.players.read` and `.write`): `config/players(provider?, include_values?, include_unavailable?,
  include_disabled?)` lists `PlayerConfig` (`player_id`, `provider`, `enabled`, `name`, `default_name`, `player_type`);
  `config/players/get_entries(player_id)` is a settings form like a provider's (the same `ConfigEntry` model);
  `config/players/save(player_id, values)`, `config/players/invoke_action`, `config/players/remove(player_id)` (forget a
  player), `config/players/setup`, DSP presets. Whether rename and hide are fields in that form is unverified.
- **Log and diagnostics** (`system.manage`): `logging/get` returns the application log as text; `diagnostics/get(include_log_tail)`
  returns a sanitized report. Logs can be large.
- **Other**: `config/core/*` (server-wide settings by domain, `config.core.read` and `.write`), `remote_access/info` and
  `remote_access/configure` (`system.manage`), dashboards, party mode, translations.

## Plan (slices, each tested on the phone before the next)
1. **Read-only Server screen**: this server (name, version, status, add-on or not, remote access on or off), background tasks
   (status, progress, last and next run, last error), users (name, role, enabled), access tokens (name, kind, created, last
   used, expires, "this phone" marked), and the application log on demand. Safe on the real server.
2. **Tasks, library sync and tokens**: run, cancel, retry and clear tasks, switch a recurring task on or off, "Sync all
   libraries", revoke a token (never this phone's own without a clear warning), share the log text.
3. **Users**: add a user (username, password, role, name), change role, enable or disable, delete (asks first, never one's own
   account), reset password.
4. **Player settings**: a player's settings form (the same form code as providers), enable or disable, forget a player.
Out of M7 unless asked: server-wide core settings and remote access (high risk, rarely needed), DSP, dashboards, party mode.

## Rules
- Hidden from non-administrators (the same check as providers: the server refusing a read means no).
- Never show the server addresses, token hashes or passwords. Password fields are hidden as typed and never stored.
- Deleting a user, revoking a token, forgetting a player and clearing tasks always ask first and say what is lost.
- Each section loads on its own, so one refused read (a role missing one permission) does not blank the screen.

## Unknowns to settle on the phone
- The role strings, and whether the owner's account may use every call above.
- The date formats in tasks and tokens (numbers or text); the app accepts both.
- The status words of tasks; the app shows the server's word.
- How large the log is on the owner's server.
