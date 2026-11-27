# Provider setup: research (2026-11-27)

Source: the server's own public API description (`/api-docs/commands.json`, `/api-docs/schemas.json`, server 2.10.13, no
sign-in needed to read them). **Nothing here was tried against the real server**; it is the documented surface. Everything
marked "unverified" must be checked on a phone before it is relied on. Roadmap: M6.

## What the server offers (all need an administrator; each has a scope such as `config.providers.write`)
- **List and read**: `config/providers(provider_type?, provider_domain?, include_values?)` gives every configured instance
  (`ProviderConfig`: `instance_id`, `domain`, `type`, `enabled`, `name`, `default_name`, `status`, `last_error`, `values`).
  `config/providers/get(instance_id)`, `config/providers/get_entries(instance_id)` (the form fields of an instance),
  `providers/manifests` (every available provider type: `domain`, `name`, `description`, `type`, `builtin`,
  `multi_instance`, `allow_disable`, `has_setup_flow`, icon, documentation), `providers/manifests/get`, `providers/icon`.
- **Change**: `config/providers/save(provider_domain, values, instance_id)` updates an existing instance only;
  `config/providers/reload(instance_id)`; `config/providers/remove(instance_id)`;
  `config/providers/invoke_action(instance_id, action)` runs a one-shot button from the form and returns a message, a link
  to open, or fields to redraw (`ConfigActionResult` or a list of `ConfigEntry`).
- **Add**: only through a setup flow. `config/providers/setup(provider_domain)` returns a `SetupFlowStep`; for a provider
  needing no input the instance is created at once and a `finish` step comes back. A step has `flow_id`, `step_id`, `type`
  (`form`, `external`, `progress`, `finish`, `abort`), `title`, `description`, `entries` (the fields), `errors` (a message
  per field), `url` (for `external`, for example a web sign-in), `progress` and `progress_text`, `last_step`.
  `config/flows/submit(flow_id, values)` sends a form and returns the next step; `config/flows/get(flow_id)` re-reads a step
  (poll an `external` or `progress` step); `config/flows/abort(flow_id)`. `config/providers/reconfigure(instance_id)` starts a
  flow to change an existing instance (for example to sign in again).
- **Library sync**: `music/sync(media_types?, providers?)` returns background tasks (scope `library.manage`).
- **Who is an admin**: `auth/me` returns a `User` with a `role` string. The role strings are not listed anywhere (unverified);
  the scope names (`config.providers.write`, `system.manage`, `users.manage`, ...) show permissions are per role.

## The form model (`ConfigEntry`)
Fields: `key`, `type` (boolean, string, secure_string, integer, float, label, splitted_string, divider, action, icon, alert,
image, url, pairing_code), `label`, `description`, `default_value`, `required`, `options` (a list of `value`, `title`,
`disabled`), `range` (for numbers), `multi_value`, `depends_on` with `depends_on_value` or `depends_on_value_not` (show a field
only when another has, or lacks, a value), `hidden`, `read_only`, `advanced`, `category`, `action` and `action_label` (for
buttons), `help_link`, `immediate_apply`, `requires_reload`. A value is a boolean, number, string, or a list of those.
Provider status: `loaded`, `loading`, `disabled`, `auth_required`, `incompatible`, `error` (with `last_error.message`).

## Plan (slices, each tested on the phone before the next)
1. **Read-only list** (built first): admin detection, "Music sources and players" in Settings, instances grouped by kind
   (music, players, metadata, plugins) with a status and any error. No changes possible. Safe on the real server.
2. **Instance actions**: reload, enable or disable, remove (asks first, says what is lost), sync a music provider's library.
3. **Edit an instance**: a form drawn from `get_entries` with every entry type above, `depends_on` rules, an Advanced fold,
   required checks, secure fields never shown or logged, then `save`.
4. **Add a provider**: a list of available kinds from the manifests (builtin and single-instance ones already added are
   hidden), then the setup flow: form steps, progress steps (poll), external steps (open the link in the browser, then poll),
   finish and abort.

## Rules for this milestone
- Hidden from anyone who is not an administrator. Admin is detected by whether `config/providers` succeeds (not by guessing
  role names) and re-checked at each connection.
- Never log or store a field value; password-type values are not echoed.
- Removing a provider can remove its library items: always confirm, name the provider, say it cannot be undone.
- No change is made on the owner's server during testing without the owner saying so: slice 1 is read-only; slices 2 to 4 are
  tested on one provider the owner chooses.

## Unknowns to settle on the phone
- Whether `config/providers` fails for a non-admin (the error shape), and the role strings.
- How enabling and disabling is saved (a value in `save`, or a separate entry in `get_entries`).
- Whether secure values come back masked from `get_entries`.
- Which providers on the owner's server need the external (browser) step.
