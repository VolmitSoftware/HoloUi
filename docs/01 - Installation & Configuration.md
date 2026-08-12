# Installation & Configuration

HoloUi is a single-jar Bukkit plugin that draws holographic menus and container previews from JSON files in its data folder. This document covers the plugin descriptor, server requirements, installation, the `plugins/holoui/` layout, every `settings.json` key, the hot-reload cadence, and telemetry. Boot order, service registration, and internal task wiring are in `11 - Runtime Architecture.md`.

## Plugin identity

| Descriptor field | Value |
| --- | --- |
| `name` | `holoui` |
| `version` | `1.0.0-26.2` |
| `main` | `art.arcane.holoui.HoloUI` |
| `api-version` | `26.1` |
| `folia-supported` | `true` |
| `authors` | `[ CrazyDev22 ]` |
| `website` | `VolmitSoftware.com` |

The data folder is `plugins/holoui/`, derived from `name`.

The descriptor declares one command, `holoui`, with aliases `holo`, `hui`, `holou`, `hu`, and twenty permissions. The command itself declares no permission; access is gated per subcommand. See `02 - Commands & Permissions.md`.

### depend / softdepend / load

There is no `depend:` block and no `load:` block. With `load:` absent, Bukkit uses the default `POSTWORLD`, so `onEnable` runs after worlds are loaded.

`softdepend:` lists 17 plugins, in file order:

`PlaceholderAPI`, `ProtocolLib`, `ProtocolSupport`, `ViaVersion`, `ViaBackwards`, `ViaRewind`, `Geyser-Spigot`, `CraftEngine`, `ItemsAdder`, `Oraxen`, `Nexo`, `MMOItems`, `ExecutableItems`, `EcoItems`, `Slimefun`, `MythicMobs`, `HeadDatabase`

None of these are required, and none are bundled in the jar. Each custom item adapter is class-loaded only once its host plugin is confirmed enabled; see `08 - Custom Items & Item Providers.md`. PlaceholderAPI support is installed only when PlaceholderAPI is enabled; see `07 - Expressions & Placeholders.md`.

## Requirements

| Requirement | Value |
| --- | --- |
| Server | Bukkit server built against the Paper API (`paper-api` 26.1.2.build.74-stable at compile time) |
| Folia | Supported; `folia-supported: true` |
| Minecraft | `api-version 26.1` |
| Java | 25 or newer; the jar is compiled with `--release 25` |
| Network | Outbound HTTPS on first start to the Maven repositories embedded in SlimJar metadata; optional outbound HTTPS to the configured editor-sync relay |

HoloUi carries the SlimJar loader and dependency metadata in its jar. PacketEvents and the other runtime libraries are downloaded into HoloUi's dependency cache and loaded through its classloader; installing PacketEvents as a separate plugin is not required.

## Installation

1. Place the jar in `plugins/`.
2. Start the server. During construction, before `onLoad`, the plugin logs `Loading Dependencies...`, resolves the libraries declared with `slim(...)` in `build.gradle`, then logs `Dependencies loaded!`. Resolution uses the repository metadata embedded by SlimJar, including CodeMC for PacketEvents and Maven Central for the other current libraries. The first start of a dependency set requires network access; subsequent starts reuse the downloaded cache.
3. `onEnable` creates `plugins/holoui/` and its contents.

The jar ships no default menus, so `menus/` is created empty. The 13 shipped preview documents are extracted into `previews/` on every start when missing.

## Data folder layout

| Path | Created by | Contents |
| --- | --- | --- |
| `plugins/holoui/menus/` | `ConfigManager` (`mkdirs`) | Menu definition JSON, one menu per file. JSON files are discovered recursively; relative paths become slash-separated ids, so `shops/main.json` is `shops/main`. Other extensions and hidden path segments are ignored. Format in `03 - Menu File Format.md` |
| `plugins/holoui/boards/` | `BoardService` / `BoardRepository` | Revisioned persistent world-board JSON. Nested lowercase ids map to nested paths; writes are serialized, revision-checked, and atomically replaced. See `02 - Commands & Permissions.md` and `11 - Runtime Architecture.md` |
| `plugins/holoui/images/` | `ConfigManager` (`mkdirs`) | Image assets referenced by imaging-backed icons. See `05 - Icons.md` |
| `plugins/holoui/previews/` | `PreviewDocumentRegistry` (`mkdirs`) | Container preview documents. Missing shipped documents are re-extracted on start. See `09 - Container Previews.md` |
| `plugins/holoui/settings.json` | `HuiSettings` | Plugin settings; see below |
| `plugins/holoui/language.yml` | `HoloLocalization` | Locale selection and message overrides. Generated with a single key, `locale`, set to the English locale. See `10 - Localization.md` |
| `plugins/holoui/preview-scales.json` | `PreviewScaleService` | Per-player container preview scale factors, written when a player finishes adjusting, on quit, and at shutdown |
| `plugins/holoui/custom-items.json` | `CustomItemCatalogWriter` | Custom item catalog. Not created at boot; written only by `/holoui items export`, capped at 10000 items per provider. Consumed by the web editor (`12 - Web Editor & Schemas.md`) |
| `plugins/holoui/editor-sync-sessions.json` | `EditorSyncSessionStore` | Private server capabilities and base snapshots for active round-trip editor sessions. The file is atomically replaced, owner-restricted on POSIX stores, capped at 80 MiB, and must not be shared or published |
| `plugins/holoui/editor-sync-transactions/` | `HoloUiProjectTransaction` | Incomplete durable multi-file publications. Startup recovery commits or rolls them back before menu and board services load |
| `plugins/holoui/editor-sync-backups/` | `HoloUiProjectTransaction` | The newest 20 completed publication backups, retained for operator recovery |

A menu's registry key is its case-preserving path relative to `menus/`, with `/` separators and the final `.json` removed; the same value is stamped as the menu id. In-game row editing and menu copy commands use those ids and persist through an asynchronous, revision-checked atomic writer; see `02 - Commands & Permissions.md`.

## settings.json

The file lives at `plugins/holoui/settings.json`. Each key is registered by `art.arcane.holoui.config.HuiSettings` with a type, a default, and an optional change listener. Values are read through accessors that clamp and sanitize; a null, `NaN`, or infinite number falls back to the default rather than propagating.

### Keys

| Key | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `debugHitbox` | boolean | `false` | — | Runs a 2-tick task that calls `highlightHitbox` on every `ClickableComponent` of every open session. See `04 - Components & Hitboxes.md` |
| `debugPosition` | boolean | `false` | — | Runs a 2-tick task drawing a `Color.YELLOW` particle at the session center and a `Color.ORANGE` particle at each component location |
| `builderUrl` | string | `https://holoui.volmitsoftware.com` | Must start with `http://` or `https://`; must not contain any character `<= ' '` or any of `'`, `"`, `<`, `>`, `\` | Editor origin used by `/holoui builder`, live `#/sync/…` links, and one-way `#/import/menu/…` fallback links. A value failing validation is replaced with the default because it becomes a MiniMessage `click:open_url` target |
| `editorSyncEnabled` | boolean | `true` | — | Allows creation and automatic or manual pulling of round-trip editor sessions. Setting it false stops future pulls; list, status, and revoke remain available, and an already-committed in-flight publication may finish safely |
| `editorSyncEndpoint` | string | `https://sync.holoui.volmitsoftware.com/v1` | Absolute HTTPS endpoint, or loopback-only HTTP, ending in `/v1`; no user info, query, fragment, traversal, whitespace, or more than 1,024 characters | Relay used for new sessions. Existing sessions retain the endpoint captured when they were created. An invalid value falls back to the default |
| `editorSyncCreateToken` | string | `""` | Empty, or 22–128 URL-safe `A-Z a-z 0-9 _ -` characters | Optional bearer credential sent only when creating a relay session. It is never placed in an editor URL or persisted in the session store. The official relay requires an operator-issued token; the empty default therefore falls back to the one-way editor handoff unless the configured relay permits anonymous creation |
| `editorSyncSessionMinutes` | integer | `60` | `5`–`1440` | Expiry requested for newly created sync capabilities |
| `editorSyncPollSeconds` | integer | `3` | `1`–`60` | Base automatic polling period. Changes require a plugin/server restart because the scheduled cadence is created when the service starts; relay failures back off per session to at most five minutes |
| `editorSyncMaxProjectMiB` | integer | `8` | `1`–`32` | Maximum canonical JSON size for one sync project. Independent protocol caps on menus, images, sessions, and runtime image work still apply |
| `previewEnabled` | boolean | `true` | — | Master switch for container previews |
| `previewLookDistance` | double | `10.00` | `1.00` – `24.00` | Raycast distance in blocks used to decide what a player is looking at |
| `previewScale` | double | `0.65` | `0.25` – `4.00` | Base scale multiplier for a container preview, applied before the per-player factor and the distance factor |
| `uiScale` | double | `1.00` | `0.25` – `4.00` | Global scale for menu components, icons, and hitboxes |
| `customItems` | boolean | `true` | — | Enables custom item provider integrations. When false, `resolve` returns null and no provider is activated |
| `customItemProviders` | string | `""` | Comma-separated provider ids or plugin names; empty allows every provider | Provider allowlist. Entries are trimmed and lowercased; a provider matches on either its id or its plugin name. Ids are listed in `08 - Custom Items & Item Providers.md` |

Values are stored as JSON scalars. There is no list or enum value type, which is why `customItemProviders` is a comma-separated string rather than an array.

### Reload behavior

`settings.json` is checked every 5 ticks by the `ConfigManager` task chain. On a detected modification the file is reparsed and change listeners fire. A key absent from the JSON object is assigned its default. At shutdown, the file is rewritten with every value the running server actually held.

A value that cannot be parsed is rejected and the key keeps the value the server is already running with. Every other key in the same file still reloads. Each rejection logs one line, `settings.json: <key> = <value> rejected (<exception>), keeping <value>.`, and a file whose root is not a JSON object logs `settings.json: root is not a json object, keeping the last good values.` Reporting happens once per detected modification, not once per tick, and neither case interrupts the 5-tick task.

### Runtime notes

- **A freshly generated `settings.json` carries every key at its default.** Defaults are materialized before the first write, so the generated file is the authoritative listing of what can be set. A key added or edited by hand takes effect on the next 5-tick check.
- **The shutdown rewrite emits every registered key.** Entries never read and never reloaded fall back to their default rather than disappearing. Any key not recognized by `HuiSettings` is dropped.
- **`debugHitbox` and `debugPosition` apply on boot and on live edit.** Boot loads `settings.json` without firing change listeners (other subsystems start later and read values themselves). `MenuSessionManager` construction calls `applyDebugSettings()` so a `true` value in the file arms the 2-tick debug tasks immediately. A live edit still fires the entry listeners and arms or cancels those tasks.
- **A boolean set to an arbitrary string parses as `false` without a rejection.** Gson reads any non-`true` string as `false`, so the value is well-formed as far as the loader is concerned. A JSON `null` or a non-numeric double is rejected as described above.
- **Disabling editor sync does not revoke remote capabilities.** It prevents new sessions and all future automatic or manual pulls. Use `/holoui sync revoke <session>` before or after disabling, or let the capability expire.
- **Editor sync is locally bounded.** HoloUi retains at most 32 active sessions and 64 MiB of canonical base plus pending server snapshots. Expired sessions are pruned before admission; the session-store file itself is refused above 80 MiB.
- **An uncertain session-store flush pauses synchronization.** If the atomic replacement is visible but its parent directory cannot be flushed after retries, HoloUi refuses new live links and future pulls until restart recovery. Existing sessions can still be listed, inspected, or revoked.

## Hot reload

Every scheduled task is dispatched through VolmLib's `SchedulerUtils`, which routes to the Folia scheduler when present and falls back to the Bukkit scheduler otherwise. The repeating tasks are self-rescheduling chains, not Bukkit repeating tasks: the body runs, then the next delay is scheduled. Tasks started with a delayed first pass skip that pass, so their first real execution lands two periods after start.

| Watched path | Period | Detects | Behavior |
| --- | --- | --- | --- |
| `menus/` | 5 ticks | Modification | Reparse the file. On success, destroy every open session of that menu, notify each affected player, then replace the registry entry and log `Menu config "<name>" has been changed and re-registered.` |
| `menus/` | 20 ticks | Creation, deletion | Created files are parsed and registered (`New menu config "<name>" detected and registered.`); deleted files destroy all sessions of that menu and drop the key (`Menu config "<name>" has been deleted and unregistered.`) |
| `images/` | 5 ticks | Modification | Log `Image asset "<file>" changed and was hot reloaded.` and refresh visuals on open sessions |
| `images/` | 20 ticks | Creation, deletion | Log the change and refresh visuals on open sessions |
| `settings.json` | 5 ticks | Modification | Reparse and fire change listeners |
| `language.yml` | 5 ticks | Modification | Reload the locale overlay; a rejected reload is reported and the previous catalog stays live |
| `previews/` | 5 ticks | Modification, creation, deletion | Recompile the affected `*.json` and republish the snapshot |

Both `menus/` passes apply the same recursive filter as the boot scan. Regular non-symbolic `*.json` files under real, non-hidden directories are accepted case-insensitively and receive a slash-separated relative id; other extensions, hidden paths, directories themselves, symbolic links, and files outside the root are skipped. Creating or deleting a nested directory registers or unregisters its accepted descendants.

Players with a session open on a reloaded menu receive a notice: a localized `CONFIG_RELOADED` message on the action bar, or on a boss bar lane when the action bar is claimed by something with higher priority, plus `ENTITY_EXPERIENCE_ORB_PICKUP` at volume `0.5`, pitch `1`.

### Runtime notes

- **A broken edit keeps the last good definition.** A parse failure leaves the previous registry entry untouched rather than removing the menu. An empty menu file logs `Menu config "<name>.json" is empty, ignoring.` and is skipped.
- **Both reload passes survive an operational failure.** The 5-tick and 20-tick bodies catch exceptions and log the full stack trace as `Config <period> reload pass failed.`. Without that guard, an escaping exception would end menu, image, settings, and localization hot reload until restart because the passes are self-rescheduling chains. JVM errors are not suppressed.
- **`previews/` follows the same `.json` rule as `menus/`.** A document that fails to compile logs `previews/<name>.json: <message>` and, on reload, leaves the previously compiled version live. The preview watcher body is wrapped in a catch-all as well.

## Metrics and telemetry

Two independent systems. bStats sends data off-server. The in-process telemetry counters never leave the JVM.

### bStats

The bStats plugin id is `24222`. Five custom charts are registered in addition to the standard bStats server data (server software, Java version, player count, core count, OS, plugin version, and similar).

| Chart id | Chart type | Sampled value |
| --- | --- | --- |
| `menu_definitions` | `SingleLineChart` | Number of registered menu definitions |
| `open_menus` | `SingleLineChart` | Number of open menus |
| `session_holders` | `SingleLineChart` | Number of session holders |
| `display_entities` | `SingleLineChart` | Total display entity count |
| `item_providers` | `AdvancedPie` | Each active provider id mapped to `1` |

Chart callables run on the bStats daemon thread, never the server thread. A sample whose source is not yet available returns null and is skipped for that cycle.

**Disabling.** There is no per-plugin toggle; the enable check is a compile-time constant that is always true. Metrics are turned off through the shared bStats configuration at `plugins/bStats/config.yml` by setting `enabled: false`, which applies to every bStats-reporting plugin on the server.

### In-process telemetry

`HoloUiTelemetry` is a static counter store: packets sent, display entity spawn churn, preview refreshes, session tick nanoseconds, open menus, and open previews. Per-second rates are recomputed on a 1000 ms window. Nothing is transmitted to a third party; the counters feed the bStats `open_menus` chart and the VolmLib integration service that other Volmit plugins poll in-process. Only aggregate counters exist — there is no per-player, per-world, or per-menu breakdown, and nothing identifying is recorded. The exposed metric keys and the service contract are documented in `11 - Runtime Architecture.md`.
