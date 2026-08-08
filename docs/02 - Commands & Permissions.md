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
2. Normalizes arguments: bare `previews` → `previews list`; bare `/holoui open <id>` → `open menu=<id>`; bare `/holoui previews reset <name>` → `previews reset name=<name>`.
3. Resolves help via `DirectorMiniMenu.resolveHelp(engine, args, 9)`. If a help page resolves, it is delivered — the MiniMessage page for `Player` senders, the plain console form for everyone else — and the command ends.
4. Otherwise executes the Director invocation.
5. On failure, sends `holoui.message.unknown_command` with the joined arguments if Director produced no message of its own.

`onTabComplete` applies the same `holoui.command` gate and returns an empty list when it fails.

Player senders also hear command sounds from `DirectorThemes.forProduct(DirectorProduct.HOLOUI)`: success at pitch 1.3, failure at pitch 0.85, both `SoundCategory.MASTER` at volume 0.8. Console senders hear nothing.

The engine is built without a dispatcher, so `DirectorExecutionDispatcher.IMMEDIATE` applies and every handler body runs on the thread that dispatched the command. Handlers that need another thread schedule it themselves: `previews reset` runs async, `items export` runs its callback on the sender's scheduler, and `previews dump` hops to the player's region thread for player senders.

## Argument syntax

Director's argument mapper determines what HoloUI accepts:

- A parameter is required when its `@Param` declares no `defaultValue`. Any `defaultValue` makes it optional.
- A token containing `=` is a keyed argument (`name=value`). An unknown key produces `Unknown parameter` and the whole invocation fails.
- Bare positional tokens bind, in declaration order, only to parameters that are required and non-contextual.
- A leftover positional token produces `Unexpected argument` and a usage line of the form `/holoui previews dump <name> [optional=...]`.
- `contextual = true` parameters — every `sender` parameter in HoloUI — are never read from user input. They come from the context registry, which registers `CommandSender` and `Player`.
- Arguments are re-tokenized before mapping and double quotes are honored, so `name="my doc"` is one token.
- Subcommand and parameter-key matching is fuzzy: exact or alias match, then prefix, substring, and Levenshtein distance within `max(1, len/3)`. `men=shop` therefore resolves to `menu=shop`.

`HoloUiCommandService.normalizeArgs` rewrites two optional bare tokens before Director runs: `/holoui open shop` becomes `open menu=shop`, and `/holoui previews reset chest` becomes `previews reset name=chest`. Help tokens (`help`, `?`, `help=N`) are not rewritten. Keyed forms and `/holoui previews dump <name>` (required `name`) already work without rewriting.

## Command index

| Command | Arguments | Permission | Console | Player |
|---------|-----------|------------|---------|--------|
| `/holoui list` | none | `holoui.command.list` | yes | yes |
| `/holoui open [menu=<id>\|<id>]` | `menu` (String, optional, default `*`; bare id rewritten to `menu=`) | `holoui.command.open` and `holoui.open.<menuId>` | only the `*` form | yes |
| `/holoui back` | none | `holoui.command.back` | no | yes |
| `/holoui close` | none | `holoui.command.close` | no | yes |
| `/holoui builder` | none | `holoui.command.builder` | yes | yes |
| `/holoui items status` | none | `holoui.command.items` | yes | yes |
| `/holoui items export` | none | `holoui.command.items.export` | yes | yes |
| `/holoui previews list` | none | `holoui.command.previews` | yes | yes |
| `/holoui previews reset [name=<document>\|<document>]` | `name` (String, optional, default `*`; bare name rewritten to `name=`) | `holoui.command.previews.reset` | yes | yes |
| `/holoui previews dump <name>` | `name` (String, required) | `holoui.command.previews.dump` | yes | yes |

`items` and `previews` are command groups, not invocable commands. No subcommand declares aliases, so no subcommand aliases exist. Every subcommand uses the default `origin = DirectorOrigin.BOTH`; the framework never blocks a sender by type, and player-only restrictions are enforced inside handler bodies.

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

### `/holoui builder`

```
/holoui builder
```

Requires `holoui.command.builder`. Available to console and players, with different output.

The URL is `HuiSettings.builderUrl()`, default `https://holoui.volmitsoftware.com`. A configured value that is not a clean `http://` or `https://` URL falls back to the default.

- Non-player senders receive the plain text `holoui.message.builder.open` ("Web editor: {url}").
- Players receive a `DirectorMiniMenu` block: banner `holoui.message.builder.header` ("Web Editor"), one `click:open_url` line with hover `holoui.message.builder.hover`, and a closing bar.

See `12 - Web Editor & Schemas.md` for the editor itself and `01 - Installation & Configuration.md` for the `builderUrl` setting.

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

## Permissions

| Node | Declared in plugin.yml | Default | Grants |
|------|------------------------|---------|--------|
| `holoui.command` | yes | `op` | Use of `/holoui` at all, and any tab completion for it. Checked in `onCommand` and `onTabComplete`. |
| `holoui.command.list` | yes | `op` | `/holoui list`, and the list output produced by `/holoui open` with `menu=*`. |
| `holoui.command.open` | yes | `op` | `/holoui open`. Checked at the top of the handler and again during menu resolution. |
| `holoui.command.close` | yes | `op` | `/holoui close`. |
| `holoui.command.back` | yes | `op` | `/holoui back`. |
| `holoui.command.builder` | yes | `op` | `/holoui builder`. |
| `holoui.command.items` | yes | `op` | `/holoui items status`. |
| `holoui.command.items.export` | yes | `op` | `/holoui items export`, and the clickable export hint appended to `/holoui items status`. |
| `holoui.command.previews` | yes | `op` | `/holoui previews list`, and therefore bare `/holoui previews`. |
| `holoui.command.previews.reset` | yes | `op` | `/holoui previews reset`. |
| `holoui.command.previews.dump` | yes | `op` | `/holoui previews dump`. |
| `holoui.open.<menuId>` | no | op-only fallback | Opening one specific menu. Checked when a menu is opened by command and by the API menu resolution path. |
| `holoui.preview` | yes | `op` | Viewing holographic container previews. Not a command permission. |

Every command-level denial sends `holoui.message.permission_denied` ("&7[&bHoloUI&7]: &cYou lack permission &f{permission}&c.") with `{permission}` set to the exact node that was tested. The root gate uses the same message.

`plugin.yml` declares no `children:` blocks, so no node implies any other node. `holoui.open.<menuId>` is the only node HoloUI checks without declaring it, because the menu ids are only known at runtime; Bukkit falls back to `PermissionDefault.OP` for unregistered nodes, so it behaves as op-only unless a permission plugin grants it explicitly. There is no bare `holoui.open` node — nothing checks one, and it would not act as a wildcard parent if it existed.

The API path that resolves menus applies `holoui.open.<menuId>` as well — see `14 - API - Menus.md`.

## Tab completion

Completion delegates to the Director engine after the `holoui.command` gate. Any `Throwable` is caught, logged as a warning, and returned as an empty list. Per-subcommand permissions are not consulted, so any sender holding `holoui.command` sees every subcommand name.

- With no arguments typed, all root child names are suggested, sorted case-insensitively.
- On a group name, completions are the child names of the current node, filtered by prefix, substring, or reverse substring.
- On an invocable node, completion switches to parameter suggestions. Unconsumed non-contextual parameters are offered as `name=` chips. Bare unkeyed value suggestions are normally offered only for required parameters. HoloUI additionally rewrites the optional positional value for `open` and `previews reset` to keyed form during completion, then removes the synthetic key from returned suggestions. After typing `key=`, the value side is completed from the parameter's custom handler and emitted as a full `name=value` token; if nothing matches, the bare `name=` is returned.
- Value candidates come from the parameter's custom handler, then registered legacy handlers, then enum constants or `true`/`false` for booleans. HoloUI registers no legacy handlers, so plain `String` parameters without a custom handler produce no value suggestions.

| Input | Suggestions |
|-------|-------------|
| `/holoui <TAB>` | `back`, `builder`, `close`, `items`, `list`, `open`, `previews` |
| `/holoui items <TAB>` | `export`, `status` |
| `/holoui previews <TAB>` | `dump`, `list`, `reset` |
| `/holoui open <TAB>` | bare `*` plus every configured menu id |
| `/holoui open sh<TAB>` | matching menu ids as bare values, such as `shop` |
| `/holoui open menu=<TAB>` | `menu=*` plus `menu=<id>` for every configured menu id |
| `/holoui previews reset <TAB>` | positional completion is normalized through `name=`; because `name` has no custom value handler, it has no document-name candidates |
| `/holoui previews reset ch<TAB>` | the positional prefix is accepted and normalized, but no candidate is produced because `name` has no custom value handler |
| `/holoui previews reset name=<TAB>` | `name=` only |
| `/holoui previews dump <TAB>` | `name=` only |
| `/holoui list <TAB>`, `/holoui back <TAB>`, `/holoui close <TAB>`, `/holoui builder <TAB>`, `/holoui items status <TAB>`, `/holoui items export <TAB>`, `/holoui previews list <TAB>` | empty |

`MenuNameHandler.getPossibilities` returns `*` followed by the configured menu ids with duplicates removed, and returns just `*` if the plugin instance or config manager is null.

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
| `/holoui list` | Executed |

`help=N` selects a page. The integer after `help=` is converted to a zero-based index via `max(0, N - 1)`; a non-numeric value falls back to page 0.

### Page structure

Children are sorted case-insensitively by name, `totalPages` is `ceil(entries / 9)`, and the requested page is clamped into range. A rendered page contains:

1. A banner headline showing the node path, with ` {page/totalPages}` appended when there is more than one page.
2. A back link on the parent path when the node has a parent.
3. One line per child. Groups render as `name - Category`; invocable commands render the name followed by parameter chips, `[name]` for required parameters and `⊰name⊱` for optional ones. Contextual parameters are hidden. Clicking a command suggests the command path; clicking a group runs `<path> help=1`.
4. Hover text per entry listing names and aliases, the description, a usage note, and a generated example such as `/holoui previews dump name=<String>`, with defaults substituted where present.
5. A footer bar with `〈 Page N` and `Page N ❭` buttons wired to `<path> help=<index>` and `<path> help=<index+2>`.

No HoloUI help page actually pages: the root has 7 entries, `items` has 2, and `previews` has 3, all under the page size of 9.

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
- `/holoui items export` returns success to Director immediately, so the success sound plays before the export completes and players hear a second sound when it finishes.
- No subcommand declares `sync = true`, and the engine uses the immediate dispatcher, so the execution-mode value is never acted on.
