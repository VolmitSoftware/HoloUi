# Actions

An action is the effect a clickable component fires when a player left-clicks it. HoloUi defines exactly two action types, `command` and `sound`, attached to a button's `actions` list or to a toggle's `trueActions` and `falseActions` lists. This page documents both key sets, the dispatch and threading model, and the failure modes of each key.

## Action model

An action is a JSON object with a `type` discriminator. `MenuActionData` is the base interface, not an action type of its own; it is bound through an `EnumType` factory keyed on `MenuActionType`.

| `type` value | Config record | Runtime class | Effect |
| --- | --- | --- | --- |
| `command` | `CommandActionData` | `CommandMenuAction` | Runs a command as the clicking player or as the console |
| `sound` | `SoundActionData` | `SoundMenuAction` | Plays a sound to the clicking player only |

There is no menu action type. HoloUi has no `open`, `close`, `switch`, `back`, or `menu` action; menu navigation is done with a `command` action that invokes the plugin's own command. See [Menu navigation](#menu-navigation).

### The `type` key

| Key | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `type` | string | Yes | none | Must be `command` or `sound` |

`type` is read and removed from the object before the remaining keys are bound to the record, so a record component named `type` is never populated. Failures:

| Condition | Result |
| --- | --- |
| `type` missing | `JsonParseException("Missing type")` |
| `type` not a string | `JsonParseException("Type must be a string")` |
| `type` not `command` or `sound` | `JsonParseException("Unknown type: <value>")` |

Each of these aborts parsing of the whole menu file. See [Error handling](#error-handling).

Unknown extra keys inside an action object are ignored at runtime. `$defs.commandAction` and `$defs.soundAction` do not set `additionalProperties: false`, so the schema permits them as well.

### Where action lists appear

| JSON location | Key | Required by schema | Runtime field |
| --- | --- | --- | --- |
| `button` component data | `actions` | Yes | `ButtonComponentData.actions()` |
| `toggle` component data | `trueActions` | Yes | `ToggleComponentData.trueActions()` |
| `toggle` component data | `falseActions` | Yes | `ToggleComponentData.falseActions()` |
| `decoration` component data | — | — | Decorations are not clickable and hold no actions |

Each is a JSON array of action objects. A single action object may be written in place of the array; the shared Gson instance wraps a non-array value into a one-element array. `null` and an omitted list are both tolerated at runtime and produce an empty action list.

Toggle lists are named for the state being entered, not the state currently held. Clicking a toggle whose state is `false` runs `trueActions` and then moves to `true`; clicking one whose state is `true` runs `falseActions` and then moves to `false`. Initial state is `Placeholders.setPlaceholders(player, condition).equalsIgnoreCase(expectedValue)`, evaluated once when the component is constructed. See [04 - Components & Hitboxes.md](04%20-%20Components%20%26%20Hitboxes.md).

## `command` action

```json
{ "type": "command", "source": "player", "command": "/spawn" }
```

| Key | Type | Required by schema | Value when absent | Meaning |
| --- | --- | --- | --- | --- |
| `type` | string | Yes | — | Constant `command` |
| `command` | string | Yes | `null` | Command line to run. Missing, blank, or slash-only values are dropped during action resolution |
| `source` | string enum | No | `player` | `player` or `server` |

`source` values:

| JSON value | Java constant | Dispatch |
| --- | --- | --- |
| `player` | `PLAYER` | `session.getPlayer().performCommand(command)` |
| `server` | `GLOBAL` | `Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command)` |

### Source semantics

`CommandMenuAction.execute` branches on `data.sourceOrDefault() == MenuActionCommandSource.PLAYER`. `sourceOrDefault()` maps a missing value to `PLAYER`, so `source` omitted entirely, `source: null`, and any unrecognized string all run as the clicking player. Gson accepts both serialized names and Java enum names: `server` and `GLOBAL` select the console branch, while `player` and `PLAYER` select the player branch. The editor canonicalizes the uppercase forms on import.

- `player`: dispatched through `Player#performCommand` on the clicking player. The player's own permissions apply. The boolean result is discarded, so an unknown command or a permission denial produces only whatever message the command itself sends to the player.
- `server`: dispatched from `Bukkit.getServer().getConsoleSender()`, which is operator-equivalent. Command output goes to the console, not to the player. The boolean result is discarded. Writing `server` grants the action full console privileges with no permission check against the clicking player.

### Whitespace and leading slash

The command string is trimmed first. A single leading slash is then removed before dispatch.

| Written value | Dispatched as |
| --- | --- |
| `/spawn` | `spawn` |
| `spawn` | `spawn` |
| `//wand` | `/wand`, dispatched as the command literally named `/wand` |
| `" /spawn "` | `spawn` |
| missing, blank, or `/` after trimming | Not dispatched; the action is dropped during resolution |

Invalid empty command actions are detected when a menu file is compiled and omitted when the component's resolved action list is built. HoloUi logs one warning per menu id and component id, rather than failing later when the component is clicked.

### Substitution

None. Apart from trimming surrounding whitespace and stripping one leading slash, the command string is unchanged. `CommandMenuAction` performs no PlaceholderAPI expansion, no `%player%`-style token replacement, and no expression evaluation. `Placeholders.setPlaceholders` is called in exactly two places in the plugin — the toggle `condition` in `ToggleComponent.isValid` and icon text in `TextMenuIcon` — and neither touches action data. A command containing `%player_name%` reaches the command handler with the literal `%player_name%` text in it. See [07 - Expressions & Placeholders.md](07%20-%20Expressions%20%26%20Placeholders.md).

Consequence: with `source: "server"` there is no built-in way to name the clicking player in the command string. Player-targeted commands must use `source: "player"`, or the target must be hard-coded.

### Threading

| `source` | Thread | Timing |
| --- | --- | --- |
| `player` | The click's own thread (main thread on Paper, the player's region thread on Folia) | Inline, inside the `PlayerInteractEvent` handler |
| `server` | Global/main thread, via `SchedulerUtils.runGlobal(HoloUI.INSTANCE, …)` | Deferred — Folia global region scheduler `execute`, otherwise `Bukkit.getScheduler().runTask`, i.e. next tick |

`SchedulerUtils.runGlobal` returns `false` and drops the runnable if the plugin is not active or scheduling is refused. `CommandMenuAction` ignores the return value, so a dropped console command is silent.

Because the console branch is deferred, a `server` command does not run inside the click's `try`/`catch`. An exception it throws surfaces as an uncaught scheduler task error rather than the per-component log line described in [Error handling](#error-handling). Ordering also changes: every inline action of the click completes before any deferred console command runs.

## `sound` action

```json
{ "type": "sound", "sound": "ui.button.click", "source": "master", "volume": 1, "pitch": 1 }
```

| Key | Type | Required by schema | Value when absent | Meaning |
| --- | --- | --- | --- | --- |
| `type` | string | Yes | — | Constant `sound` |
| `sound` | string | Yes | `null` | Namespaced sound key |
| `source` | string enum | No | `master` | Client volume category |
| `volume` | number | No | `1.0` | Volume multiplier |
| `pitch` | number | No | `1.0` | Playback pitch |

`SoundActionData` holds `sound` as the raw string and `source`, `volume`, and `pitch` as nullable boxed values, so an omitted key is distinguishable from an explicit one. `sourceOrDefault()`, `volumeOrDefault()`, and `pitchOrDefault()` supply `master`, `1.0`, and `1.0`. An explicit `"volume": 0` stays `0.0` and is silent; only an omitted key defaults.

### `sound` key format

`sound` is read as a plain string at parse time and resolved once at resolve time by `SoundActionData.resolveSound()`: `NamespacedKey.fromString(value)` — no namespace means `minecraft` — then `RegistryUtil.find(Sound.class, key)` against the Bukkit `Sound` registry, with field-name and enum-name fallbacks. Parsing a `sound` key never throws.

| Written value | Resolves | Path |
| --- | --- | --- |
| `ui.button.click` | Yes | Registry, namespace defaulted to `minecraft` |
| `minecraft:ui.button.click` | Yes | Registry |
| `ui_button_click` | Yes | `findByEnum` fallback: `Sound` field names lowercased (`UI_BUTTON_CLICK` → `ui_button_click`) |
| `UI_BUTTON_CLICK` | No | `NamespacedKey` keys are validated against `[a-z0-9_-./]`; uppercase fails and `fromString` returns `null` |
| `ui.button.nonexistent` | No | Well-formed but absent from every lookup |

Both failure modes behave identically. The key resolves to nothing, `MenuAction.resolve` logs one `WARNING` naming the menu, the component, and the written key, the action is dropped so clicking it does nothing, and the rest of the menu file loads and runs normally. See [Error handling](#error-handling).

### `source` (sound category)

`SoundSource` maps a JSON value to an `org.bukkit.SoundCategory`.

| JSON value | Java constant | `SoundCategory` |
| --- | --- | --- |
| `master` | `MASTER` | `MASTER` |
| `music` | `MUSIC` | `MUSIC` |
| `record` | `RECORD` | `RECORDS` |
| `weather` | `WEATHER` | `WEATHER` |
| `block` | `BLOCK` | `BLOCKS` |
| `hostile` | `HOSTILE` | `HOSTILE` |
| `neutral` | `NEUTRAL` | `NEUTRAL` |
| `player` | `PLAYER` | `PLAYERS` |
| `ambient` | `AMBIENT` | `AMBIENT` |
| `voice` | `VOICE` | `VOICE` |

The JSON spellings `record` and `block` are singular while the Bukkit constants `RECORDS` and `BLOCKS` are plural. Gson also accepts the Java `SoundSource` names in uppercase, such as `MUSIC`, `RECORD`, and `PLAYER`; the editor canonicalizes them to lowercase on import.

`SoundMenuAction.execute` calls `data.sourceOrDefault().getCategory()`. An omitted `source`, an explicit `null`, or an unrecognized string (Gson maps unknown enum names to `null`) all fall back to `master`. `$defs.soundAction` lists only `sound` in its `required` array, and the runtime agrees.

### `volume` and `pitch`

Both are passed to `Player#playSound(Location, Sound, SoundCategory, float, float)` after the omitted-key default is applied. HoloUi performs no validation or clamping of a written value.

| Key | Default when omitted | Usual range | Behavior |
| --- | --- | --- | --- |
| `volume` | `1.0` | `0.0`–`1.0` for loudness | Vanilla treats values above `1.0` as extended audible distance rather than extra loudness |
| `pitch` | `1.0` | `0.5`–`2.0` | The client clamps into `0.5`–`2.0`, so a written `0.0` plays as `0.5`, the deepest and slowest rendering of the sound |

A `sound` action that writes neither key plays the sound as recorded.

### Playback target

`session.getPlayer().playSound(session.getPlayer().getLocation(), …)`. The sound is sent to the clicking player only, positioned at that player's own location. Nearby players hear nothing.

## Menu navigation

To open, close, or switch menus from a click, use a `command` action against the plugin's own command. The command root is `holoui`, with aliases `holo`, `hui`, `holou`, and `hu`.

| Command | Effect | Permissions required | Sender constraint |
| --- | --- | --- | --- |
| `/holoui open menu=<id>` or `/holoui open <id>` | Opens `<id>`, replacing any session the player currently has | `holoui.command.open` and `holoui.open.<id>` | Player only |
| `/holoui back` | Reopens the player's previous session | `holoui.command.back` | Player only |
| `/holoui close` | Closes the player's current session | `holoui.command.close` | Player only |

All three reject a non-player sender with a "player only" message and do nothing else. Navigation actions therefore need the player source, which is what an omitted `source` gives. With an explicit `source: "server"` the command runs as the console and is refused, and because the boolean result is discarded the failure is silent to the player.

Bare `/holoui open <id>` is rewritten to `menu=<id>` before Director runs. Keyed form remains valid.

```json
{ "type": "command", "source": "player", "command": "/holoui open shop" }
```

See [02 - Commands & Permissions.md](02%20-%20Commands%20%26%20Permissions.md) for the full command set.

### Target menu id resolution

- The menu id is the base file name of the JSON file in the plugin's `menus/` directory, minus the extension.
- Lookup is `ConfigManager.get(String)` against a `ConcurrentHashMap`: an exact, case-sensitive key match. There is no fuzzy or case-insensitive resolution.
- `/holoui open menu=*` (or bare `/holoui open`) prints the menu list instead of opening anything; that path also requires `holoui.command.list`.

### Behavior when the target is missing

| Situation | Result |
| --- | --- |
| Id not in the registry (never loaded, failed to parse, file deleted) | `MENU_UNAVAILABLE` message to the sender, no session change, nothing logged as an error |
| Id exists but the player lacks `holoui.open.<id>` | `MENU_PERMISSION_DENIED` message, no session change |
| Session construction throws | `MENU_OPEN_FAILED` message to the sender, `SEVERE` log with the cause chain |
| `/holoui back` with no prior session | `NO_PREVIOUS_MENU` message |

### Session replacement

`SessionHolder.openSessionLocked` detaches the current session, records it as history for `back`, and terminates it with `HoloCloseReason.REPLACED` before constructing and opening the new one.

The click loop iterates over a snapshot of clickable components captured before any action ran, and a component's action list is iterated to completion regardless. Actions declared after an open or close command still execute, against a session that has already been replaced or destroyed. Put navigation commands last in the list.

## Execution model

### Trigger

`MenuSessionManager.dispatchClick` is registered on `PlayerInteractEvent` at `EventPriority.MONITOR`. It returns immediately unless the action is `LEFT_CLICK_AIR` or `LEFT_CLICK_BLOCK`; right clicks never fire component actions. When a session with at least one selected component exists, the interact event is cancelled.

### Order

1. `SessionHolder.snapshotClick` walks `MenuSession.getComponents()` in menu declaration order and collects every `ClickableComponent` that is both open and currently selected. The raytrace can select more than one. If none are selected, nothing runs.
2. For each collected component, in that order:
   1. `ApiEvents.fireClick` fires `HoloUiMenuClickEvent`. If cancelled, the component is skipped entirely — neither its actions nor its API handler run.
   2. `component.onClick()` runs the component's action list.
   3. If the menu belongs to a live API handle, the third-party `HoloClickHandler` is invoked through `ApiClickGuard`, after the JSON actions.
3. Within a component, actions run in the order they are declared in the JSON array, sequentially, each fully completing before the next starts (`actions.forEach(a -> a.execute(session))`).
4. For a toggle, only one of the two lists runs per click, chosen by the current state. The icon swap and the state assignment happen after that list completes.

### Threads

- The whole dispatch is an event handler: the server main thread on Paper, or the clicking player's region thread on Folia.
- `sound` actions and `player` command actions execute inline on that thread.
- `server` command actions are the only asynchronous hop; they are handed to `SchedulerUtils.runGlobal` and execute on a later tick on the global/main thread.

Nothing is ever run off a server thread. See [11 - Runtime Architecture.md](11%20-%20Runtime%20Architecture.md).

### Error handling

**Resolve time** (`ConfigManager` compilation and component construction through `MenuAction.resolve`). An entry that is `null`, or whose `MenuActionData` implementation is neither `CommandActionData` nor `SoundActionData`, is dropped with a `WARNING`:

```
Component "%s" declares an unsupported action "%s"; skipping it.
```

A command whose value is missing, blank after trimming, or exactly `/` after trimming is dropped. The menu compilation pass emits one warning per menu id and component id:

```
Menu "%s" component "%s" declares an empty command; that action does nothing.
```

A `sound` action whose key resolves to nothing — malformed, unknown, or missing — is dropped with one `WARNING` per bad key:

```
Menu "%s" component "%s" declares an unknown sound "%s"; that action does nothing.
```

The menu name is the id of the session the component belongs to. The surviving actions keep their declaration order, and no `null` ever enters the list.

**Click time.** `component.onClick()` is wrapped per component in `MenuSessionManager.dispatchClick`:

```java
try {
  component.onClick();
} catch (Exception ex) {
  HoloUI.logExceptionStack(false, ex, "Menu component %s of menu %s threw while handling a click from %s.", …);
}
```

| Consequence | Detail |
| --- | --- |
| Blast radius | A throwing action aborts every remaining action in that same component's list. The `forEach` has no per-action guard. |
| Other components | Unaffected; the loop continues to the next component in the snapshot. |
| Log level | `WARNING`, with the full throwable stack trace and cause chain. |
| Toggle state | A throw skips `swapIcon` and the state assignment, so the toggle neither flips nor changes icon. |
| Errors | Only `Exception` is caught, not `Throwable`. |
| Deferred console commands | Run outside this `try`, so their failures are not attributed to the component. |
| API handlers | Wrapped separately by `ApiClickGuard`, which logs at `WARNING` and quarantines an owning plugin after `DEFAULT_FAULT_LIMIT` (5) faults. JSON actions are not covered by that guard and are never quarantined. |

**Parse time.** `ConfigManager.loadConfig` catches `Throwable` around `BukkitJson.parse` and logs

```
An error occurred while parsing menu config "%s.json":
```

then returns empty, so the menu is not registered at all. An action-level parse failure — unknown `type` or missing `type` — discards the entire menu file, not just the offending action. A sound key is not a parse failure: it is resolved later, and a bad one costs only that one action.

## Runtime notes

- `/holoui open|back|close` are player-only. A navigation action with an explicit `source: "server"` is refused by the command and fails silently.
- Command strings receive no placeholder or expression substitution of any kind.
- One thrown action aborts the rest of that component's action list, but not the other components' lists.
- `//wand` becomes `/wand` after the single leading-slash strip, and is dispatched as a command literally named `/wand`.
- Surrounding command whitespace is trimmed before the leading-slash check.
- Missing, blank, and slash-only commands are warned about once per menu/component and omitted from the resolved list.

## Absent-key reference

| Action | Key omitted | Value | Effect |
| --- | --- | --- | --- |
| `command` | `command` | `null` | One resolve-time warning per menu/component; action dropped |
| `command` | `source` | `player` | Runs as the clicking player, inline, under that player's permissions |
| `sound` | `sound` | `null` | One `WARNING` at resolve time; the action is dropped and does nothing |
| `sound` | `source` | `master` | Plays in the `MASTER` category |
| `sound` | `volume` | `1.0` | Full volume |
| `sound` | `pitch` | `1.0` | Normal pitch |
| any | `type` | — | `JsonParseException`; whole menu file fails to load |
| button | `actions` | `null` | Empty action list; clicking does nothing |
| toggle | `trueActions` / `falseActions` | `null` | Empty list; the toggle still flips state and swaps icon |

## Related

- [03 - Menu File Format.md](03%20-%20Menu%20File%20Format.md) — where action lists sit in a menu file
- [04 - Components & Hitboxes.md](04%20-%20Components%20%26%20Hitboxes.md) — buttons, toggles, and selection
- [02 - Commands & Permissions.md](02%20-%20Commands%20%26%20Permissions.md) — the `holoui` command tree
- [07 - Expressions & Placeholders.md](07%20-%20Expressions%20%26%20Placeholders.md) — where substitution does apply
- [12 - Web Editor & Schemas.md](12%20-%20Web%20Editor%20%26%20Schemas.md) — `$defs.action`, `$defs.commandAction`, `$defs.soundAction`
- [14 - API - Menus.md](14%20-%20API%20-%20Menus.md) — `HoloClickHandler` and API-owned menus
