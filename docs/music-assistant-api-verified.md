# Music Assistant API: verified with a real server (2026-11-26)

Companion to `music-assistant-api.md` (research and unauthenticated probe). This file records what the owner's authenticated sample run (`tools/Sample-MusicAssistant.ps1`, server 2.10.13, schema 65) showed. Where it conflicts with the research file, this file wins. Names are generic on purpose (public repo).

## Verified
- **Websocket auth works as documented.** Server sends server-info first. Client sends `{"command":"auth","message_id":N,"args":{"token":"..."}}`. Reply: `{"message_id":"1","result":{"authenticated":true,"user":{...}},"partial":false}`. `message_id` comes back as a **string**. It worked through the owner's reverse proxy over `wss://`. The `user` object has `user_id, username, role, enabled, display_name, avatar_url, preferences, provider_filter, player_filter`.
- **Images.** `PlayerMedia.image_url` is a ready absolute URL of the form `<server>/imageproxy/<proxy_id>?size=512&fmt=jpg`. `MediaItemImage` has `path, provider, remotely_accessible, proxy_id`; build `/imageproxy/<proxy_id>?size=N&fmt=jpg` from it (inferred from the `image_url` form). User avatars use `/api/image/serve/<id>/<W>x<H>`. The server writes its own internal address into these URLs, so the app must rewrite the host to the address the user typed.
- **Real topology** (18 players): about 10 `universal_player` entries (Android TVs, AV receivers, a soundbar, a phone app), 4 `chromecast` (3 of type `group`), 2 `wiim`, and 1 `sendspin` web player that is `hide_in_ui` and `private`.
  - Chromecast groups are **static** (`static_group_members` set, `can_group_with` empty, `powered=false` while idle). A 2-member group and a 4-member group share members, and members are also normal players.
  - Features differ per player. TVs via `universal_player`: only `volume_set, volume_mute, set_members` (no pause, seek or play_media flag; `power_control: none`). Chromecast players add `pause, next_previous, seek, play_media, enqueue`. WiiM adds `select_source, gapless_playback`. Groups: `pause, power, volume_set, play_media, volume_mute, enqueue`. Gate every control on `supported_features`.
  - Unavailable players are present (`available=false`, `volume_level=null`).
  - `playback_state` can be `idle` while `current_media` still holds the last item (stale queue).
  - Each player has a queue with `queue_id == player_id`; all are `active=true` even when empty (`items=0`), so `active` does not mean "playing".
- **Library** comes from an Emby provider inside Music Assistant (`provider: library`, `uri: library://album/<id>`, mapping domain `emby`), with fanart.tv images. Downbeat talks only to Music Assistant.
- **Queue fields**: `current_index`, `current_item{queue_item_id, name ("Artist - Title"), duration, media_item{uri...}}`, `elapsed_time` (seconds), `elapsed_time_last_updated` (epoch seconds), `shuffle_enabled`, `repeat_mode`, `crossfade_enabled`, `smart_fades_active`, `state`.
- **Search** returns `artists, albums, genres, tracks, playlists, radio, audiobooks, podcasts, sound_effects` (empty types are empty arrays). `music/albums/library_items` returns a plain array of Album (`year`, `artists`, `metadata.images`, `provider_mappings`).
- **Server-side user preferences** (`user.preferences`) hold `activePlayerId` and `recentlySelectedPlayerIds`, written by the official apps. Downbeat may read them as a hint for the default room but must not depend on or overwrite them.
- **Sizes**: `players/all` 151 KB and `player_queues/all` 80 KB here. Fetch once, then use events; do not poll.

## Second run (2026-11-26): errors, token creation, events
- **Error reply**: `{"message_id":"8","error_code":12,"details":"Invalid or unsupported command."}`. `error_code` is a **number**, the text is in `details`. No `result` key.
- **Not every bad request is an error**: `players/get` with an unknown player id returned `{"message_id":"9","result":null,"partial":false}`. Callers must treat a null result as "not found".
- **`auth/token/create(name)`**: result is the token as a **plain string** (`{"message_id":"10","result":"<token>","partial":false}`).
- **Events**: `{"event":"player_updated","object_id":"<player id>","data":{<a full Player object>}}`. `object_id` equals `data.player_id`. Both events captured in the 25-second window were `player_updated` (from volume and play/pause changes); no `queue_updated` or `queue_time_updated` was captured (the script saves only the first two events per name, and the owner's actions may not have produced any). The app should therefore re-read the queue after player events instead of relying on queue events until those are seen.
- The `Player`, `PlayerQueue` models decode all 18 real players, all 18 real queues and the real events (unit test `RealSamplesTest`, which reads a local samples folder and is skipped without it).

## Still NOT verified
- `auth/login` (username/password) result shape.
- Queue events (`queue_updated`, `queue_items_updated`, `queue_time_updated`) and their payloads.
- Token expiry behavior in practice (docs say 1 year, no renewal).
- Dynamic grouping (`set_members`) and `play_media` in practice: the runs were read-only.
- Third-party client licenses (listing snippets only).
