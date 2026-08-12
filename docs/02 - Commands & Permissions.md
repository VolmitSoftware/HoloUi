# Commands & Permissions

HoloUI exposes a single root command, `/holoui`, dispatched through the VolmLib Director framework. This document lists every subcommand with its exact syntax, arguments, permission nodes, console and player availability, tab completion, and help behavior.

## Root command

| Property | Value |
|----------|-------|
| Command | `/holoui` |
| Aliases | `holo`, `hui`, `holou`, `hu` |
| Declared in | `plugin.yml` (`commands.holoui.aliases`) and `@Director` on `HoloCommand` |
| Executor and tab completer | `HoloUiCommandService` |
| Gate permission | `holoui.command` |

`HoloUiCommandService.onCommand` runs the following steps:

1. Checks `holoui.command`. Without it the sender receives `holoui.message.permission_denied` and nothing else runs.
2. Normalizes arguments: bare `previews` and `boards` become their `list` actions; bare `/holoui open <id>`, `/holoui previews reset <name>`, `/holoui boards list <page>`, and `/holoui boards near <radius>` are rewritten to keyed optional arguments. Multi-token row text, icon/style values, and image paths are joined into their final keyed parameter.
3. Resolves help via `DirectorMiniMenu.resolveHelp(engine, args, 9)`. If a help page resolves, it is delivered — the MiniMessage page for `Player` senders, the plain console form for everyone else — and the command ends.
4. Otherwise executes the Director invocation.
5. On failure, sends `holoui.message.unknown_command` with the joined arguments if Director produced no message of its own.

`onTabComplete` applies the same `holoui.command` gate and returns an empty list when it fails.

Player senders also hear command sounds from `DirectorThemes.forProduct(DirectorProduct.HOLOUI)`: success at pitch 1.3, failure at pitch 0.85, both `SoundCategory.MASTER` at volume 0.8. Console senders hear nothing.

The engine is built without a dispatcher, so `DirectorExecutionDispatcher.IMMEDIATE` applies and every handler body runs on the thread that dispatched the command. Handlers that need another thread schedule it themselves: `previews reset` and persistent-board disk mutations run async, completion feedback returns to the sender's entity or global scheduler, and player or world state is captured only from the owning scheduler.

## Argument syntax

Director's argument mapper determines what HoloUI accepts:

- A parameter is required when its `@Param` declares no `defaultValue`. Any `defaultValue` makes it optional.
- A token containing `=` is a keyed argument (`name=value`). An unknown key produces `Unknown parameter` and the whole invocation fails.
- Bare positional tokens bind, in declaration order, only to parameters that are required and non-contextual.
- A leftover positional token produces `Unexpected argument` and a usage line of the form `/holoui previews dump <name> [optional=...]`.
- `contextual = true` parameters — every `sender` parameter in HoloUI — are never read from user input. They come from the context registry, which registers `CommandSender` and `Player`.
- Arguments are re-tokenized before mapping and double quotes are honored, so `name="my doc"` is one token.
- Subcommand and parameter-key matching is fuzzy: exact or alias match, then prefix, substring, and Levenshtein distance within `max(1, len/3)`. `men=shop` therefore resolves to `menu=shop`.

`HoloUiCommandService.normalizeArgs` rewrites optional bare values before Director runs: `/holoui open shop` becomes `open menu=shop`, `/holoui previews reset chest` becomes `previews reset name=chest`, `/holoui boards list 2` becomes `boards list page=2`, and `/holoui boards near 24` becomes `boards near radius=24`. Bare `previews` and `boards` become their `list` actions. Help tokens (`help`, `?`, `help=N`) and already-keyed forms are not rewritten.

## Command index

| Command | Arguments | Permission | Console | Player |
|---------|-----------|------------|---------|--------|
| `/holoui list` | none | `holoui.command.list` | yes | yes |
| `/holoui open [menu=<id>\|<id>]` | `menu` (String, optional, default `*`; bare id rewritten to `menu=`) | `holoui.command.open` and `holoui.open.<menuId>` | only the `*` form | yes |
| `/holoui back` | none | `holoui.command.back` | no | yes |
| `/holoui close` | none | `holoui.command.close` | no | yes |
| `/holoui move` | none | `holoui.command.move` | no | yes |
| `/holoui builder` | none | `holoui.command.builder` | yes | yes |
| `/holoui edit <menu>` | loaded menu id | `holoui.command.edit`; live sync also requires `holoui.command.sync` | yes | yes |
| `/holoui sync list` | none | `holoui.command.sync` | yes | yes |
| `/holoui sync status <session>` | exact session id or unique prefix of at least 12 characters | `holoui.command.sync` | yes | yes |
| `/holoui sync revoke <session>` | exact session id or unique prefix of at least 12 characters | `holoui.command.sync` | yes | yes |
| `/holoui sync pull\|poll <session>` | exact session id or unique prefix of at least 12 characters | `holoui.command.sync` | yes | yes |
| `/holoui items status` | none | `holoui.command.items` | yes | yes |
| `/holoui items export` | none | `holoui.command.items.export` | yes | yes |
| `/holoui previews list` | none | `holoui.command.previews` | yes | yes |
| `/holoui previews reset [name=<document>\|<document>]` | `name` (String, optional, default `*`; bare name rewritten to `name=`) | `holoui.command.previews.reset` | yes | yes |
| `/holoui previews dump <name>` | `name` (String, required) | `holoui.command.previews.dump` | yes | yes |
| `/holoui menus addrow <menu> <text>` | loaded menu id and MiniMessage text | `holoui.command.menus` | yes | yes |
| `/holoui menus insertrow <menu> <row> <text>` | loaded menu id, one-based row, text | `holoui.command.menus` | yes | yes |
| `/holoui menus setrow <menu> <row> <text>` | loaded menu id, one-based row, text | `holoui.command.menus` | yes | yes |
| `/holoui menus removerow <menu> <row>` | loaded menu id and one-based row | `holoui.command.menus` | yes | yes |
| `/holoui menus offsetrow <menu> <row> <x> <y> <z>` | loaded menu id, row, absolute or `~`-relative offsets | `holoui.command.menus` | yes | yes |
| `/holoui menus seticon <menu> <row> <type> <value>` | loaded menu id, row, icon type and value | `holoui.command.menus` | yes | yes |
| `/holoui menus style <menu> <row> <property> <value>` | loaded menu id, row, style property and value or `*` | `holoui.command.menus` | yes | yes |
| `/holoui menus image <menu> <path>` | loaded menu id and confined image path | `holoui.command.menus` | yes | yes |
| `/holoui menus copy <menu> <newMenu>` | loaded source id and new nested id | `holoui.command.menus` | yes | yes |
| `/holoui boards [list [page=<number>\|<number>]]` | optional one-based page, default `1`, 10 entries per page | `holoui.command.boards` | yes | yes |
| `/holoui boards reload` | none | `holoui.command.boards` | yes | yes |
| `/holoui boards near [radius=<blocks>\|<blocks>]` | optional radius, default `64` | `holoui.command.boards` | no | yes |
| `/holoui boards info <board>` | board id | `holoui.command.boards` | yes | yes |
| `/holoui boards create <board> <menu>` | new board id and loaded menu id | `holoui.command.boards` | no | yes |
| `/holoui boards delete\|remove <board>` | board id | `holoui.command.boards` | yes | yes |
| `/holoui boards rename <board> <newBoard>` | board ids | `holoui.command.boards` | yes | yes |
| `/holoui boards copy <board> <newBoard>` | board ids | `holoui.command.boards` | yes | yes |
| `/holoui boards move <board> <x> <y> <z>` | absolute or `~`-relative effective coordinates | `holoui.command.boards` | yes | yes |
| `/holoui boards movehere\|tphere <board>` | board id | `holoui.command.boards` | no | yes |
| `/holoui boards tp <board>` | board id | `holoui.command.boards` | no | yes |
| `/holoui boards rotate <board> <yaw> <pitch> <roll>` | absolute or `~`-relative effective angles | `holoui.command.boards` | yes | yes |
| `/holoui boards scale <board> <scale>` | absolute or `~`-relative scale | `holoui.command.boards` | yes | yes |
| `/holoui boards align <board> <reference> <axes>` | axes `x`, `y`, `z`, `xy`, `xz`, `yz`, or `xyz` | `holoui.command.boards` | yes | yes |
| `/holoui boards menu\|root <board> <menu>` | board id and loaded menu id | `holoui.command.boards` | yes | yes |
| `/holoui boards addrow <board> <text>` | board id and MiniMessage text | `holoui.command.boards` | yes | yes |
| `/holoui boards insertrow <board> <row> <text>` | board id, one-based row, text | `holoui.command.boards` | yes | yes |
| `/holoui boards setrow <board> <row> <text>` | board id, one-based row, text | `holoui.command.boards` | yes | yes |
| `/holoui boards removerow <board> <row>` | board id and one-based row | `holoui.command.boards` | yes | yes |
| `/holoui boards offsetrow <board> <row> <x> <y> <z>` | board id, row, absolute or `~`-relative offsets | `holoui.command.boards` | yes | yes |
| `/holoui boards seticon <board> <row> <type> <value>` | board id, row, icon type and value | `holoui.command.boards` | yes | yes |
| `/holoui boards style <board> <row> <property> <value>` | board id, row, style property and value or `*` | `holoui.command.boards` | yes | yes |
| `/holoui boards image <board> <path>` | board id and confined image path | `holoui.command.boards` | yes | yes |
| `/holoui boards ranges <board> <viewRange> <interactionRange>` | positive block ranges | `holoui.command.boards` | yes | yes |
| `/holoui boards visibility <board> <mode> <viewPermission> <interactPermission>` | `public`, `permission`, or `hidden`; `-` clears a permission | `holoui.command.boards` | yes | yes |
| `/holoui boards permissions <board> <viewPermission> <interactPermission>` | permission nodes or `-` | `holoui.command.boards` | yes | yes |
| `/holoui boards follow <board> <player> <fixed\|yaw\|full>` | online player and rotation mode | `holoui.command.boards` | yes | yes |
| `/holoui boards unfollow <board>` | board id | `holoui.command.boards` | yes | yes |
| `/holoui boards edit <board>` | board id | `holoui.command.boards` | no | yes |
| `/holoui boards editweb\|webedit <board>` | board id | `holoui.command.boards.editweb`; live sync also requires `holoui.command.sync` | yes | yes |
| `/holoui boards save` | none | `holoui.command.boards` | no | yes |
| `/holoui boards cancel` | none | `holoui.command.boards` | no | yes |
| `/holoui import preview\|dry-run <source>` | installed legacy source | `holoui.command.import` | yes | yes |
| `/holoui import apply <source>` | installed legacy source | `holoui.command.import.apply` | yes | yes |

`items`, `previews`, `menus`, `boards`, and `import` are command groups. Bare `previews` and `boards` are normalized to `list`. Board aliases are `remove` for `delete`, `tphere` for `movehere`, and `root` for `menu`; import preview aliases are `dry-run` and `dryrun`. Every subcommand uses the default `origin = DirectorOrigin.BOTH`; player-only restrictions are enforced inside handler bodies.

## Command reference

### `/holoui list`

```
/holoui list
```

Requires `holoui.command.list`. Available to console and players.

With no configured menus, sends `holoui.message.menu.none`. Otherwise renders a `DirectorMiniMenu` block: a banner from `holoui.message.menu.list.header` ("Menus"), one line per menu id, and a closing bar. Each entry carries the hover text `holoui.message.menu.list.entry` ("Click to open {menu}.") and a `click:run_command:/holoui open menu=<id>` action, the keyed form the argument mapper accepts for an optional parameter.

The list is not filtered by `holoui.open.<menuId>`. Every configured menu id appears; the per-menu permission is checked only when the menu is opened.

### `/holoui open`

```
/holoui open
/holoui open menu=<id>
/holoui open <id>
/holoui open menu=*
/holoui open *
```

| Argument | Type | Required | Default | Handler |
|----------|------|----------|---------|---------|
| `menu` | `String` | no | `*` | `HoloCommand.MenuNameHandler` |
| `sender` | `CommandSender` | contextual | — | context registry |

Requires `holoui.command.open`, plus `holoui.open.<menuId>` for the resolved menu. Console can use only the `*` form.

1. Checks `holoui.command.open`; denial sends `holoui.message.permission_denied`.
2. If `menu` trims to `*` — also the default when the argument is omitted — the call is forwarded to `list(sender)`, so a bare `/holoui open` prints the menu list and requires `holoui.command.list` for that inner call.
3. Any other value requires a `Player` sender. Console receives `holoui.message.menu.player_only` ("Menus can only be opened by players.").
4. Opening then resolves the menu:
   - unknown menu id sends `holoui.message.menu.unavailable` with the raw input,
   - a second `holoui.command.open` check sends `holoui.message.permission_denied` on failure,
   - a missing `holoui.open.<definition id>` sends `holoui.message.menu.permission_denied`,
   - otherwise `SessionManager.createNewSession` runs; any `Throwable` is logged and reported as `holoui.message.menu.open_failed`.

`MenuNameHandler.parse` rejects null or blank input with `holoui.error.menu_name_required`, passes `*` through, resolves a case-insensitive match against configured menu ids, and otherwise returns the trimmed input verbatim, so an unknown id reaches step 4.

Menu ids come from the menu files described in `03 - Menu File Format.md`.

### `/holoui back`

```
/holoui back
```

Requires `holoui.command.back`. Player only; console receives `holoui.message.command.player_only`.

Calls `SessionManager.openLastSession(player)`. When that returns false the player receives `holoui.message.menu.no_previous`. Success produces no message beyond the reopened menu and the command success sound.

### `/holoui close`

```
/holoui close
```

Requires `holoui.command.close`. Player only; console receives `holoui.message.command.player_only`.

Calls `SessionManager.destroySession(player, false)`. True sends `holoui.message.menu.closed`, false sends `holoui.message.menu.none_open`.

### `/holoui move`

```
/holoui move
```

Requires `holoui.command.move`. Player only; console receives `holoui.message.command.player_only`.

Calls `SessionManager.moveSession(player)`. With an open menu, the session is re-anchored to the player's current feet position, the definition's menu offset is reapplied, and its current facing yaw is preserved; component, icon and hitbox transforms are reapplied without rotating the menu. It then sends `holoui.message.menu.moved`. With no open menu, it sends `holoui.message.menu.none_open`.

The move changes only the current packet-only session. It does not edit the menu JSON, create a persistent world object, replace the session, alter history or API-handle state, or recapture facing. The layout keeps the yaw recorded when it opened.

### `/holoui builder`

```
/holoui builder
```

Requires `holoui.command.builder`. Available to console and players, with different output.

The URL is `HuiSettings.builderUrl()`, default `https://holoui.volmitsoftware.com`. A configured value that is not a clean `http://` or `https://` URL falls back to the default.

- Non-player senders receive the plain text `holoui.message.builder.open` ("Web editor: {url}").
- Players receive a `DirectorMiniMenu` block: banner `holoui.message.builder.header` ("Web Editor"), one `click:open_url` line with hover `holoui.message.builder.hover`, and a closing bar.

See `12 - Web Editor & Schemas.md` for the editor itself and `01 - Installation & Configuration.md` for the `builderUrl` setting.

### `/holoui edit`

```
/holoui edit <menu>
```

Requires `holoui.command.edit`. Available to console and players. The required menu argument uses `HoloCommand.ExistingMenuHandler`, which completes loaded menu ids without the `*` value reserved for `/holoui open`.

When editor sync is enabled, the sender also holds `holoui.command.sync`, and the relay accepts session creation, the command asynchronously snapshots the exact menu plus referenced confined images and creates a time-limited capability. Players receive separate `[Open Editor]`, `[Copy Link]`, and `[Revoke]` actions. Console receives the full editor URL with a warning that it is a bearer capability. The server token remains only in `editor-sync-sessions.json`; the link contains the separate editor token and relay endpoint in its URL fragment.

If live session creation is disabled, unavailable, unauthorized, too large, or rejected by the relay, HoloUi explicitly reports the fallback and emits the version-1 one-way `#/import/menu/` link. That fallback wraps the exact source in gzip plus unpadded base64url, and editor saves do not return to the server. A compressed payload over 48,000 URL characters sends `holoui.message.editor_menu.too_large`; missing source or another preparation failure sends `holoui.message.editor_menu.failed`, with unexpected failures logged.

### `/holoui sync`

```
/holoui sync list
/holoui sync status <session>
/holoui sync revoke <session>
/holoui sync pull <session>
```

Every leaf requires `holoui.command.sync` and works for players or console. `list` shows active session kind, subject, expiry, last publication revision, and pending acknowledgement state. `status` shows the same fields for one session. Displayed ids are abbreviated to 12 characters; the other leaves accept either the exact id or a unique prefix at least 12 characters long, and reject ambiguous prefixes.

`pull` (alias `poll`) bypasses the automatic backoff and checks the relay immediately, but refuses while `editorSyncEnabled=false`. `revoke` remains available while sync is disabled, atomically excludes a concurrent poll, removes the local capability after the relay response, and is the command run by a live link's `[Revoke]` action. List and status never display relay tokens or editor URLs.

Automatic polling is outbound-only and never blocks a command or tick thread. A transient relay failure backs off per session from the configured poll period to at most five minutes. Publications are applied only when their captured server revision still matches; a conflict returns the current snapshot, invalid content is rejected, and a valid multi-file update is durably staged before its menu and board registries are republished. See `12 - Web Editor & Schemas.md` for scope and size limits.

### `/holoui items status`

```
/holoui items status
```

Requires `holoui.command.items`. Available to console and players.

With `HuiSettings.customItemsEnabled()` false, sends `holoui.message.items.disabled` and stops. Otherwise renders a `DirectorMiniMenu` block: banner `holoui.message.items.status.header` ("Custom Items"), the summary `holoui.message.items.status.summary` ("{active}/{total} providers active"), and one line per provider status.

| Condition | Message key | Text | Theme color |
|-----------|-------------|------|-------------|
| `!pluginPresent()` | `holoui.message.items.state.missing` | "not installed" | muted |
| `!active()` | `holoui.message.items.state.inactive` | "present, no adapter" | required |
| `active() && !ready()` | `holoui.message.items.state.loading` | "present, still loading" | description |
| `active() && ready()` | `holoui.message.items.state.ready` | "ready, {count} ids" | optional |

If the sender also holds `holoui.command.items.export`, a clickable line running `/holoui items export` with hover `holoui.message.items.status.hint` is appended before the closing bar.

Provider details are in `08 - Custom Items & Item Providers.md`.

### `/holoui items export`

```
/holoui items export
```

Requires `holoui.command.items.export`. Available to console and players.

1. With `HuiSettings.customItemsEnabled()` false, sends `holoui.message.items.disabled`.
2. A lazily created `CustomItemCatalogWriter` is obtained. If an export is already running, sends `holoui.message.items.export.busy` and starts nothing.
3. Sends `holoui.message.items.export.started`, then runs `exportAsync`. A false return also yields `holoui.message.items.export.busy`.
4. The completion callback runs on the sender's scheduler — entity scheduler for players, global scheduler otherwise — and reports failure as `holoui.message.items.export.failed`, a zero-item result as `holoui.message.items.export.empty` with `{path}`, and anything else as `holoui.message.items.export.done` with `{count}`, `{providers}`, and `{path}`.

The command returns success to Director immediately, so the framework success sound fires before the export finishes; the completion callback plays its own sound for players. The catalog is written to `custom-items.json` in the plugin data folder.

### `/holoui previews list`

```
/holoui previews list
/holoui previews
```

Requires `holoui.command.previews`. Available to console and players. The bare `/holoui previews` form is rewritten to `previews list` by `normalizeArgs` and executed rather than treated as a group help request.

Renders a `DirectorMiniMenu` block with banner `holoui.message.previews.list.header` ("Preview Documents"). Document names come from `PreviewDocumentRegistry.names()` sorted lexicographically. An empty registry produces the single line `holoui.message.previews.list.empty`. Each entry renders the document name plus `holoui.message.previews.list.entry` ("blocks={blocks} entities={entities} special={special} priority={priority}"); a null `special` renders as `-`.

### `/holoui previews reset`

```
/holoui previews reset
/holoui previews reset name=<document>
/holoui previews reset <document>
```

| Argument | Type | Required | Default |
|----------|------|----------|---------|
| `name` | `String` | no | `*` |
| `sender` | `CommandSender` | contextual | — |

Requires `holoui.command.previews.reset`. Available to console and players.

`name` is trimmed; null or blank becomes `*`. The work is dispatched with `SchedulerUtils.runAsync` because the reset performs many file writes plus a full reparse. The async body sends `holoui.message.previews.reset.started` with `{name}`, rewrites the named shipped document — or every shipped document for `*` — from the jar, reloads the registry, and closes open previews. An empty result sends `holoui.message.previews.reset.none`; a non-empty result sends `holoui.message.previews.reset.done` with `{count}`.

Feedback is delivered through the player's entity scheduler or the global scheduler. Names are normalized, so a trailing `.json` is tolerated. Only shipped documents are rewritten; user-added documents that shadow them are not removed.

### `/holoui previews dump`

```
/holoui previews dump <name>
/holoui previews dump name=<name>
```

| Argument | Type | Required | Default |
|----------|------|----------|---------|
| `name` | `String` | yes | — |
| `sender` | `CommandSender` | contextual | — |

Requires `holoui.command.previews.dump`. Available to console and players, on different threads.

Player senders have the dump scheduled onto the region thread owning the player, because building touches live block and inventory state; this is a no-op hop on non-Folia servers. Console senders execute inline so that RCON reads a populated response buffer.

1. The document is looked up by normalized name, so a trailing `.json` is accepted. A miss sends `holoui.message.previews.dump.unknown`.
2. Context: if the sender is a player looking at a block the document matches, `PreviewStateContext.forBlock` is used; otherwise `PreviewStateContext.statics`.
3. The document is built once and elements are counted by type, reported through `holoui.message.previews.dump.result` with `{name}`, `{total}`, `{panels}`, `{cells}`, `{slots}`, and `{labels}`.
4. With no errors, sends `holoui.message.previews.dump.no_errors`. Otherwise up to 3 lines of `holoui.message.previews.dump.error_line` are sent, followed by `holoui.message.previews.dump.error_more` ("+{count} more (see console log)") when more remain.

Omitting `name` fails at argument mapping with Director's missing-argument message plus the usage line. Preview documents are covered in `09 - Container Previews.md`.

### `/holoui menus`

All menu-content actions require `holoui.command.menus` and operate on the exact loaded file under `plugins/holoui/menus/`. A slash in an id addresses a nested file: `shops/main` is `menus/shops/main.json`. For `addrow`, `insertrow`, and `setrow`, HoloUI joins every command token from the text position onward and binds it as `text=…`. It likewise joins `seticon` and `style` from the value position as `value=…`, and `image` from the path position as `path=…`; ordinary multi-word text and image paths therefore need no quotes, while explicit keyed prefixes are also accepted.

Rows are the entries of the menu's `components` array and commands number them from 1. A document using the supported single-component object form is normalized to an array on its first row mutation.

| Command | Behavior |
|---|---|
| `menus addrow <menu> <text>` | Appends a `decoration` with a text icon and a collision-free `row-N` component id. Its offset follows the last valid row by `-0.25` Y, or starts at `[0,0,0]`. |
| `menus insertrow <menu> <row> <text>` | Inserts before the given row, accepting `size + 1` as append. Its offset is the midpoint of valid neighbours, or 0.25 blocks beyond the one valid neighbour. Existing rows are not moved. |
| `menus setrow <menu> <row> <text>` | Changes the single `icon` of a `button` or `decoration`. An existing text icon retains style, refresh interval, and unknown fields; another icon type is replaced by a text icon. Toggle rows are rejected because they have two state icons. |
| `menus removerow <menu> <row>` | Removes that component object. |
| `menus offsetrow <menu> <row> <x> <y> <z>` | Replaces the component offset. A number is absolute, `~` retains the axis, and `~number` adds to it; results must be finite and within plus or minus 60,000,000. |
| `menus seticon <menu> <row> <type> <value>` | Replaces the single icon of a `button` or `decoration`. Accepted types are `text`, `image`, `animated`, `item`, `block`, `customItem`, and `entity`; image aliases are `textImage`, `animatedImage`, and `animatedTextImage`. Non-entity replacements retain the previous icon's `style`; entity replacements remove it. Toggle rows are rejected. |
| `menus style <menu> <row> <property> <value>` | Sets one display-style property on a button or decoration icon. The literal `*` clears that property. Entity icons and toggle rows are rejected. |
| `menus image <menu> <path>` | Replaces the complete component list with one centered `decoration` named `image` using a `textImage` icon. Other top-level menu fields remain unchanged. |
| `menus copy <menu> <newMenu>` | Copies the complete JSON object, including unknown extension keys, to a new case-preserving nested id. Existing targets and symbolic-link paths are rejected. |

`seticon` values are MiniMessage text for `text`; one image path for `image`; comma-separated image paths for `animated` (default speed 5 ticks); a namespaced registry id for `item`, `block`, or `entity`; and `provider@item` for `customItem`. Item and custom-item icons start at count 1. Text is limited to 8,192 characters. Every image path used by `seticon` or `image` must name a readable, decodable regular file beneath `plugins/holoui/images/`; canonical path confinement rejects blank, absolute, missing, directory, traversal, and symlink-escape paths. Commands do not fetch URLs or resolve player avatars.

The suggested style properties and accepted values are:

| Property | Value |
|---|---|
| `billboard` | `fixed`, `vertical`, `horizontal`, or `center` |
| `shadow`, `seeThrough` | `true` or `false` |
| `textAlignment` | `left`, `center`, or `right` |
| `backgroundArgb`, `glowColor` | `#AARRGGBB` |
| `textOpacity` | integer `0` through `255` |
| `lineWidth` | integer `1` through `16384` |
| `brightness` | integer `0` through `15`; writes both block and sky light |
| `viewRange` | finite decimal `0.01` through `64` |
| `shadowRadius` | finite decimal `0` through `64` |
| `shadowStrength` | finite decimal `0` through `1` |
| `cullingWidth`, `cullingHeight` | finite decimal `0` through `4096` |
| `scale`, `scaleX`, `scaleY`, `scaleZ` | finite decimal `0.01` through `64`; `scale` writes all three axes |

Property matching ignores case, hyphens, and underscores. `textShadow`, `alignment`, `background`, `backgroundColor`, `opacity`, `width`, `height`, and `glow` are accepted aliases. Clearing `brightness` removes both light fields; clearing `scale` removes all three scale fields.

Each mutation captures the loaded source's SHA-256 revision, runs through one serialized asynchronous write queue, rereads the file before replacement, validates the candidate with the runtime parser, and writes and flushes a same-directory temporary file. Existing menus are replaced with an atomic move; copies are published with atomic no-clobber file creation so a concurrently created target is never replaced. An external edit or a second command based on a stale snapshot returns `holoui.message.menu.content.revision_conflict` instead of overwriting. The published revision shown in feedback is the first 12 hexadecimal characters of the source hash.

Publication occurs only after the persisted bytes are reread and parsed successfully. Open personal sessions using the menu are closed with `DEFINITION_RELOADED`; every board view currently on that menu is rebuilt on its viewer's entity scheduler. Unknown JSON keys outside the field intentionally changed by the command survive the rewrite. `menus copy` is the supported in-game file copy; there is no chat-based raw JSON import/export command. `/holoui edit <menu>` can create a constrained round-trip session, with the confirmation-first one-way import retained as its explicit fallback.

### `/holoui boards`

All board actions require `holoui.command.boards`. `/holoui boards` is normalized to `boards list`. Board ids are canonical lowercase paths; loaded menu arguments preserve their configured ids. Create, movehere, teleport, edit, save, cancel, and near require a player where the command needs that player's location or preview. Other mutations can run from console.

Inspection and lifecycle commands:

| Command | Behavior |
|---|---|
| `boards list [page=1\|1]` | Lists published ids, root menus, and revisions in sorted-id order, 10 per page. The bare page is normalized to `page=`; invalid pages report the available range. An empty registry has one empty page. |
| `boards reload` | Asynchronously reloads `boards/**/*.json`, reporting loaded, retained, removed, and failed counts. Invalid files retain the last good published definition where one exists. |
| `boards near [radius=64]` | Lists effective board positions within the horizontal radius of the player. A bare radius is normalized to `radius=`. |
| `boards info <id>` | Shows identity, effective world position/rotation/scale, visibility, permissions, ranges, and follow state. An active editor sees the staged state. |
| `boards create <id> <menu>` | Creates revision 1 at the player's feet, with the player's yaw/pitch, roll 0, scale 1, public visibility, and default ranges. |
| `boards delete|remove <id>` | Deletes the expected published revision. |
| `boards rename <id> <newId>` | Renames the expected published revision without changing its stable UUID. |
| `boards copy <id> <newId>` | Creates a new UUID at revision 1 from the visible published or staged definition. |

Transform commands operate on the board's effective world pose. A bare number is absolute; `~` keeps the current value and `~<number>` adds to it. For a following board, `~` is relative to its current effective pose and the result is re-encoded as a target-relative transform before persistence.

| Command | Behavior |
|---|---|
| `boards move <id> <x> <y> <z>` | Changes effective coordinates without changing world, rotation, or scale. |
| `boards movehere|tphere <id>` | Moves to the player's current world and feet position while preserving effective rotation and scale. |
| `boards tp <id>` | Teleports the player asynchronously to the effective position, yaw, and pitch. |
| `boards rotate <id> <yaw> <pitch> <roll>` | Changes effective rotation without changing position or scale. |
| `boards scale <id> <scale>` | Sets or adjusts scale; the validated range is `[0.05, 16.0]`. |
| `boards align <id> <reference> <axes>` | Copies the selected effective position axes from a same-world reference board. |

Content, visibility, and follow commands:

| Command | Behavior |
|---|---|
| `boards menu|root <id> <menu>` | Selects a currently loaded root menu. |
| `boards addrow <id> <text>` | Runs the corresponding `menus addrow` mutation against the board's visible root menu. |
| `boards insertrow <id> <row> <text>` | Runs the corresponding `menus insertrow` mutation against the board's visible root menu. |
| `boards setrow <id> <row> <text>` | Runs the corresponding `menus setrow` mutation against the board's visible root menu. |
| `boards removerow <id> <row>` | Runs the corresponding `menus removerow` mutation against the board's visible root menu. |
| `boards offsetrow <id> <row> <x> <y> <z>` | Runs the corresponding `menus offsetrow` mutation against the board's visible root menu. |
| `boards seticon <id> <row> <type> <value>` | Runs the corresponding `menus seticon` mutation against the board's visible root menu. |
| `boards style <id> <row> <property> <value>` | Runs the corresponding `menus style` mutation against the board's visible root menu. |
| `boards image <id> <path>` | Runs the corresponding whole-menu `menus image` replacement against the board's visible root menu. |
| `boards editweb\|webedit <id>` | Opens a round-trip project containing the board, its root and reachable submenu menus, and their referenced confined images. Requires `holoui.command.boards.editweb`; live sync also requires `holoui.command.sync`, otherwise the command emits a one-way copy of the root menu. |
| `boards ranges <id> <view> <interaction>` | Sets finite positive ranges; interaction cannot exceed view. |
| `boards visibility <id> <public|permission|hidden> <viewPermission|-> <interactPermission|->` | Sets the mode and both permission fields; only `permission` accepts a view permission, and `hidden` accepts neither. |
| `boards permissions <id> <viewPermission|-> <interactPermission|->` | Changes permissions and derives `permission` mode when a view node is present, otherwise public unless the existing hidden board remains permissionless. |
| `boards follow <id> <onlinePlayer> <fixed|yaw|full>` | Preserves the current effective pose while converting storage to a player-relative transform. `fixed` translates only; `yaw` also rotates horizontal offset and yaw; `full` additionally follows pitch. |
| `boards unfollow <id>` | Materializes the current effective pose as an absolute transform before clearing follow state. |

Every disk mutation uses the current published revision and runs through `BoardService`'s serialized async queue. A stale expected revision produces a conflict rather than overwriting newer data; Bukkit feedback returns through the sender's owning scheduler.

`boards edit <id>` starts one per-player staged session at the current published revision and forces a private, clickable preview for that editor even outside the published visibility and range. Transform, menu, visibility, range, permission, follow, and unfollow changes update that preview without writing disk. `boards save` performs one optimistic update and clears the preview on success; a conflict leaves the staged session available for cancellation. `boards cancel` discards it and restores the published view. Quit also discards the session. Rename and delete are blocked for the board being edited, while copy creates a separate board immediately.

Board content commands persist the referenced menu immediately through the menu write queue; they are not part of the staged board definition and are not reverted by `boards cancel`. If the sender has staged a different root menu for that board, the command targets that staged root id. They require only `holoui.command.boards`, not `holoui.command.menus`.

### `/holoui import`

```
/holoui import preview <source>
/holoui import dry-run <source>
/holoui import apply <source>
```

`preview` and its `dry-run`/`dryrun` aliases require `holoui.command.import`; `apply` requires `holoui.command.import.apply`. Both are available to players and console. Only one legacy scan or apply operation may run at a time. Preview reads and validates the source, classifies every candidate, reports complete counts plus up to 12 candidate and 12 issue lines, and writes nothing. Apply performs the same fresh preview and then publishes only candidates in `ready` or `resume_board` state; source files are never changed or deleted.

| Source argument | Accepted aliases | Read location |
|---|---|---|
| `gholo` | `files` | `plugins/GHolo/holos/*.yml` and `*.yaml` |
| `decent-holograms` | `decent`, `decent_holograms` | `plugins/DecentHolograms/holograms/*.yml` and `*.yaml` |
| `holographic-displays` | `hd`, `holographic_displays` | `plugins/HolographicDisplays/database.yml` |
| `fancy-holograms` | `fancy`, `fancy_holograms` | `plugins/FancyHolograms/holograms.yml` version 2 |

Every accepted hologram creates a menu and persistent board with the same deterministic id: `imports/<source>/<canonical-legacy-id>`. Canonical leaves are lowercase, at most 64 characters, retain ASCII letters, digits, `.`, `_`, and `-`, and collapse other runs to `-`. The loaded world name, key, and UUID must resolve before a candidate can be published. Source view range becomes the board view range, capped by the board contract at 256 blocks; interaction range is the smaller of 8 blocks and view range. A source range of `-1` uses 120 blocks. Generated display styles use the effective range divided by 64 for Minecraft display-entity `viewRange`.

| Source | Imported data |
|---|---|
| GHolo files exporter | `Holo.location`, global permission/range/rotation, row offsets and text, background, text opacity/shadow/alignment, billboard, see-through, scale, brightness, and text/block/item culling size. Static `block:`, `item:`, `itemstack:`, and living `entity:` rows become native HoloUI icons. |
| DecentHolograms | world position, `display-range`, and each page's text `content`, flattened in source order at 0.26-block row spacing. |
| HolographicDisplays | legacy `location` or structured `position`, and text `lines`; literal `null` rows are removed. |
| FancyHolograms | version-2 `TEXT` world position/rotation, visibility distance, text, background, shadow, alignment, billboard, see-through, scale, and brightness. |

GHolo global permission becomes both the board view and interaction permission. Six-digit GHolo backgrounds receive its default `0x40` alpha; eight-digit `RRGGBBAA` values are converted to HoloUI `AARRGGBB`. Bare `#RRGGBB`, `<#RRGGBB>`, ampersand hex, legacy color, MiniMessage, and PlaceholderAPI text remain runtime-compatible text. Board yaw is offset by 180 degrees and row offsets are inverse-transformed so fixed icon yaw/pitch and row entity anchors match GHolo despite HoloUI's mirrored, rotated menu coordinate system. Text and item anchors compensate HoloUI's internal layout offsets; block scale compensates HoloUI's 0.75 block model scale. HoloUI centers block geometry around the preserved entity anchor while GHolo's un-translated block display grows from its transform origin, so block geometry still reports that visual delta. A static `itemstack:` row becomes a default-style item display and reports that dropped-item spin, bob, and raw-entity presentation cannot be preserved. A static raw `entity:` row uses HoloUI's default 1-by-1 interaction geometry because GHolo does not apply display size to that raw entity. A typed row whose payload contains a dynamic placeholder remains text and reports a warning because HoloUI icon type is fixed by the menu document.

The importer does not transfer GHolo database records, `animations.yml`, custom symbols, or interactions; run GHolo's files export first. GHolo row-specific range, permission, and rotation cannot be represented independently by the current component contract and are reported per candidate. DecentHolograms page timing, actions, flags, and non-text line fields are not transferred. HolographicDisplays item, image, and touch-action lines are not transferred. FancyHolograms non-`TEXT` entries, update intervals, and linked-NPC behavior are not transferred. Invalid or non-living entity ids fail that candidate rather than silently becoming another icon.

An existing target board is always a conflict. An existing target menu with different bytes is also a conflict. A byte-identical imported menu without its board is `resume_board`, allowing recovery after a previous board-write failure. Apply never replaces either target: menus use atomic no-clobber creation and boards use the persistent service's create contract. A menu created before its board fails is reported as `menu_only`; later candidates continue, and rerunning the same import resumes that exact menu. At most 12 issue lines are displayed per result, while summary counts include the complete scan.

## Permissions

| Node | Declared in plugin.yml | Default | Grants |
|------|------------------------|---------|--------|
| `holoui.command` | yes | `op` | Use of `/holoui` at all, and any tab completion for it. Checked in `onCommand` and `onTabComplete`. |
| `holoui.command.list` | yes | `op` | `/holoui list`, and the list output produced by `/holoui open` with `menu=*`. |
| `holoui.command.open` | yes | `op` | `/holoui open`. Checked at the top of the handler and again during menu resolution. |
| `holoui.command.close` | yes | `op` | `/holoui close`. |
| `holoui.command.move` | yes | `op` | `/holoui move`. |
| `holoui.command.back` | yes | `op` | `/holoui back`. |
| `holoui.command.builder` | yes | `op` | `/holoui builder`. |
| `holoui.command.edit` | yes | `op` | `/holoui edit <menu>`. A live capability additionally requires `holoui.command.sync`; without it the command uses the one-way fallback. |
| `holoui.command.sync` | yes | `op` | List, inspect, manually pull, and revoke editor sync sessions. It is also required before edit commands create a live capability, ensuring the creator can use its Revoke action. |
| `holoui.command.items` | yes | `op` | `/holoui items status`. |
| `holoui.command.items.export` | yes | `op` | `/holoui items export`, and the clickable export hint appended to `/holoui items status`. |
| `holoui.command.previews` | yes | `op` | `/holoui previews list`, and therefore bare `/holoui previews`. |
| `holoui.command.previews.reset` | yes | `op` | `/holoui previews reset`. |
| `holoui.command.previews.dump` | yes | `op` | `/holoui previews dump`. |
| `holoui.command.menus` | yes | `op` | Every `/holoui menus` content mutation and menu copy action. |
| `holoui.command.boards` | yes | `op` | Every `/holoui boards` inspection, mutation, reload, follow, teleport, and staged-edit action. |
| `holoui.command.boards.editweb` | yes | `op` | `/holoui boards editweb <board>` and its `webedit` alias. A live capability additionally requires `holoui.command.sync`. |
| `holoui.command.import` | yes | `op` | Non-destructive `/holoui import preview`, `dry-run`, and `dryrun`. |
| `holoui.command.import.apply` | yes | `op` | No-overwrite `/holoui import apply`. It is checked directly and does not depend on a permission-child declaration. |
| `holoui.open.<menuId>` | no | op-only fallback | Opening one specific menu. Checked when a menu is opened by command and by the API menu resolution path. |
| `holoui.preview` | yes | `op` | Viewing holographic container previews. Not a command permission. |

Every command-level denial sends `holoui.message.permission_denied` ("&7[&bHoloUI&7]: &cYou lack permission &f{permission}&c.") with `{permission}` set to the exact node that was tested. The root gate uses the same message.

`plugin.yml` declares no `children:` blocks, so no node implies any other node. `holoui.open.<menuId>` is the only node HoloUI checks without declaring it, because the menu ids are only known at runtime; Bukkit falls back to `PermissionDefault.OP` for unregistered nodes, so it behaves as op-only unless a permission plugin grants it explicitly. There is no bare `holoui.open` node — nothing checks one, and it would not act as a wildcard parent if it existed.

The API path that resolves menus applies `holoui.open.<menuId>` as well — see `14 - API - Menus.md`.

## Tab completion

Completion delegates to the Director engine after the `holoui.command` gate. Any `Throwable` is caught, logged as a warning, and returned as an empty list. Per-subcommand permissions are not consulted, so any sender holding `holoui.command` sees every subcommand name.

- With no arguments typed, all root child names are suggested, sorted case-insensitively.
- On a group name, completions are the child names of the current node, filtered by prefix, substring, or reverse substring.
- On an invocable node, completion switches to parameter suggestions. Unconsumed non-contextual parameters are offered as `name=` chips. Bare unkeyed value suggestions are normally offered only for required parameters. HoloUI additionally rewrites the optional positional value for `open`, `previews reset`, `boards list`, and `boards near` to keyed form during completion, then removes the synthetic key from returned suggestions. After typing `key=`, the value side is completed from the parameter's custom handler and emitted as a full `name=value` token; if nothing matches, the bare `name=` is returned.
- Value candidates come from the parameter's custom handler, then registered legacy handlers, then enum constants or `true`/`false` for booleans. HoloUI registers no legacy handlers, so plain `String` parameters without a custom handler produce no value suggestions.

| Input | Suggestions |
|-------|-------------|
| `/holoui <TAB>` | `back`, `boards`, `builder`, `close`, `edit`, `import`, `items`, `list`, `menus`, `move`, `open`, `previews`, `sync` |
| `/holoui sync <TAB>` | `list`, `pull`, `revoke`, `status` |
| `/holoui sync status\|pull\|revoke <TAB>` | full ids for active editor sync sessions |
| `/holoui items <TAB>` | `export`, `status` |
| `/holoui previews <TAB>` | `dump`, `list`, `reset` |
| `/holoui menus <TAB>` | `addrow`, `copy`, `image`, `insertrow`, `offsetrow`, `removerow`, `seticon`, `setrow`, `style` |
| `/holoui boards <TAB>` | `addrow`, `align`, `cancel`, `copy`, `create`, `delete`, `edit`, `editweb`, `follow`, `image`, `info`, `insertrow`, `list`, `menu`, `move`, `movehere`, `near`, `offsetrow`, `permissions`, `ranges`, `reload`, `removerow`, `rename`, `rotate`, `save`, `scale`, `seticon`, `setrow`, `style`, `tp`, `unfollow`, `visibility` |
| `/holoui open <TAB>` | bare `*` plus every configured menu id |
| `/holoui open sh<TAB>` | matching menu ids as bare values, such as `shop` |
| `/holoui open menu=<TAB>` | `menu=*` plus `menu=<id>` for every configured menu id |
| `/holoui edit <TAB>` | every configured menu id, without `*` |
| `/holoui menus <action> <TAB>` where `action` accepts a menu | every configured menu id, without `*` |
| `/holoui menus seticon <menu> <row> <TAB>` | `text`, `image`, `animated`, `item`, `block`, `customItem`, `entity` |
| `/holoui menus style <menu> <row> <TAB>` | the canonical style properties listed in the menu-content table |
| `/holoui boards create <id> <TAB>`, `/holoui boards menu <id> <TAB>` | every configured menu id, without `*` |
| `/holoui boards <action> <TAB>` where `action` accepts a board | published board ids from `BoardService` |
| `/holoui boards follow <id> <TAB>` | online player names, then enum rotation values |
| `/holoui boards align <id> <reference> <TAB>` | `x`, `y`, `z`, `xy`, `xz`, `yz`, `xyz` |
| `/holoui import <TAB>` | `apply`, `preview` (plus preview aliases accepted when typed) |
| `/holoui import preview <TAB>`, `/holoui import apply <TAB>` | `decent-holograms`, `fancy-holograms`, `gholo`, `holographic-displays` |
| `/holoui previews reset <TAB>` | positional completion is normalized through `name=`; because `name` has no custom value handler, it has no document-name candidates |
| `/holoui previews reset ch<TAB>` | the positional prefix is accepted and normalized, but no candidate is produced because `name` has no custom value handler |
| `/holoui previews reset name=<TAB>` | `name=` only |
| `/holoui previews dump <TAB>` | `name=` only |
| `/holoui list <TAB>`, `/holoui back <TAB>`, `/holoui close <TAB>`, `/holoui move <TAB>`, `/holoui builder <TAB>`, `/holoui items status <TAB>`, `/holoui items export <TAB>`, `/holoui previews list <TAB>` | empty |

`MenuNameHandler.getPossibilities` returns `*` followed by configured menu ids for `/open`. `ExistingMenuHandler`, used by editor and board menu arguments, returns only configured ids. Board-id completion reads the current published service index; it does not invent ids for malformed or unpublished files.

## Help

Help is produced by `DirectorMiniMenu` with a page size of 9 entries.

### When help is shown

- Explicit help: any argument equal to `help` or `?`, or beginning with `help=`, case-insensitive. Help tokens are stripped, the remaining tokens walk the node tree, and the page is built for that node, or its parent if the walk landed on an invocable command.
- Implicit help: no help token but arguments given. All tokens must resolve to nodes; if the final node is a group, its help page is shown. If the final node is invocable, or any token fails to resolve, the command executes normally.
- No arguments at all takes the explicit path at page 0 and resolves to the root help page.

| Input | Result |
|-------|--------|
| `/holoui` | Root help page |
| `/holoui help`, `/holoui ?` | Root help page |
| `/holoui items` | Help page for the `items` group |
| `/holoui items help`, `/holoui items export help` | Help page for the `items` group |
| `/holoui previews` | Rewritten to `previews list` and executed, not help |
| `/holoui boards` | Rewritten to `boards list` and executed, not help |
| `/holoui list` | Executed |

`help=N` selects a page. The integer after `help=` is converted to a zero-based index via `max(0, N - 1)`; a non-numeric value falls back to page 0.

### Page structure

Children are sorted case-insensitively by name, `totalPages` is `ceil(entries / 9)`, and the requested page is clamped into range. A rendered page contains:

1. A banner headline showing the node path, with ` {page/totalPages}` appended when there is more than one page.
2. A back link on the parent path when the node has a parent.
3. One line per child. Groups render as `name - Category`; invocable commands render the name followed by parameter chips, `[name]` for required parameters and `⊰name⊱` for optional ones. Contextual parameters are hidden. Clicking a command suggests the command path; clicking a group runs `<path> help=1`.
4. Hover text per entry listing names and aliases, the description, a usage note, and a generated example such as `/holoui previews dump name=<String>`, with defaults substituted where present.
5. A footer bar with `〈 Page N` and `Page N ❭` buttons wired to `<path> help=<index>` and `<path> help=<index+2>`.

The root has thirteen entries and therefore spans two pages. The `boards` group has 32 entries and spans four pages; `menus` has exactly one nine-entry page, while `items`, `previews`, and `sync` remain below the page size.

### Delivery

Help goes through `DirectorMiniMenu.deliver(sender, page, theme, resolver)`, which routes by sender type: `Player` senders get `render` — the MiniMessage page described above — while every other sender gets `renderConsole`, a plain listing headed `--- <path> ---` with one `name <required> [optional=default] - description` line per child and no click, hover, gradient, or font chrome.

Menu blocks built by the commands themselves use `DirectorMiniMenu.deliver(sender, lines)`. It prepends 19 blank lines for `Player` senders only, when at least one line is non-blank, then sends each line through a reflective `sendRichMessage(String)` call, falling back to `sendMessage(String)` with MiniMessage tags stripped when that method does not exist on the sender.

Descriptions are localized through the plugin localization resolver. Every `@Director` and `@Param` in HoloUI carries a `descriptionKey` under `holoui.command.*` or `holoui.parameter.*`, with English defaults in `HoloMessages`. See `10 - Localization.md`.

The color theme is `DirectorThemes.forProduct(DirectorProduct.HOLOUI)`, mapped to a fixed pastel palette: primary `#ffadad` to `#a0c4ff`, borders `#ffd6a5` and `#caffbf`, description `#fef3ff`, required `#ffd6a5`, optional `#bde0fe`, muted `#d9c7ef`.

## Runtime notes

- `holoui.open.<menuId>` is not declared in `plugin.yml`, so it relies on Bukkit's op-only fallback rather than an explicit default. The ids are runtime data, so there is nothing to declare.
- `/holoui open` with no argument does not open anything. It defaults `menu` to `*` and prints the menu list, which additionally requires `holoui.command.list`.
- Tab completion lists subcommands a sender cannot run, since only `holoui.command` is checked during completion.
- `/holoui previews` never shows group help; it is rewritten to `previews list` before help resolution.
- `/holoui boards` likewise executes `boards list`; use `/holoui boards help` for the group help page.
- `/holoui edit` and `/holoui boards editweb` attempt live sync only for a sender who also holds `holoui.command.sync`; every live-session failure is reported before the one-way fallback is offered.
- Menu-content writes return success to Director before their asynchronous persistence callback reports the final saved revision or failure.
- Board disk mutations return success to Director before their async persistence callback reports the final result.
- Legacy import commands return success to Director before the asynchronous scan or apply reports its result. Source reading remains off gameplay threads and completion feedback returns to the sender's owning scheduler.
- `/holoui items export` returns success to Director immediately, so the success sound plays before the export completes and players hear a second sound when it finishes.
- No subcommand declares `sync = true`, and the engine uses the immediate dispatcher, so the execution-mode value is never acted on.
