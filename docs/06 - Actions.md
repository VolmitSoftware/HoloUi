# Actions

Clickable components run an ordered list of typed actions. HoloUi supports player and console commands, sounds, MiniMessage player messages, Folia-safe teleports, proxy connections, and native page-stack navigation; the web editor authors and previews the same six-type contract.

## Action model

Every action is a JSON object with a required `type` discriminator and an optional `trigger`. Actions may appear in a button's `actions` list or a toggle's `trueActions` and `falseActions` lists; a decoration has no actions.

| `type` | Runtime data | Effect |
| --- | --- | --- |
| `command` | `CommandActionData` | Runs a command as the clicking player or server console |
| `sound` | `SoundActionData` | Plays a sound to the clicking player |
| `message` | `MessageActionData` | Sends sanitized MiniMessage to the clicking player |
| `teleport` | `TeleportActionData` | Asynchronously teleports the clicking player |
| `connect` | `ConnectActionData` | Requests a BungeeCord-compatible proxy transfer |
| `navigate` | `NavigationActionData` | Changes the viewer's native menu page stack |

The shared Gson adapter removes `type` and binds the remaining keys to the matching record. A missing, non-string, or unknown type aborts parsing of the whole menu file. A non-string or unknown non-null `trigger` also rejects the menu rather than silently changing its click behavior. Unknown extra keys inside a recognized action are ignored by the runtime and preserved by the web editor.

An omitted or `null` action list becomes an empty list. The shared JSON codec also accepts one action object where a list is expected and wraps it as a one-element list.

### Click trigger

`trigger` is shared by all six action types. Omission and explicit `null` resolve to `any`; the web editor omits `any` on export.

| JSON value | Matches |
| --- | --- |
| `any` | Every accepted main-hand interaction |
| `left_click` | Left click while not sneaking |
| `right_click` | Right click while not sneaking |
| `shift_left_click` | Left click while sneaking |
| `shift_right_click` | Right click while sneaking |

Values are exact and case-sensitive. The Java type is `HoloClickTrigger`; `ANY` is valid as an action binding, while public click callbacks receive one of the four exact physical interactions.

## `command`

```json
{ "type": "command", "source": "player", "command": "/spawn", "trigger": "right_click" }
```

| Key | Required | Default | Meaning |
| --- | --- | --- | --- |
| `command` | Yes | none | Command line; one leading slash is optional |
| `source` | No | `player` | `player` or `server` |

The command is trimmed and exactly one leading slash is removed. Missing, blank, and slash-only commands are logged once per menu/component and dropped during action resolution.

- `player` calls `Player#performCommand` inline on the player's owning thread. The player's permissions apply.
- `server` defers `Bukkit#dispatchCommand` to the global scheduler with the console sender. This grants the command full console authority and does not check the player's permissions.

Command strings are not PlaceholderAPI-expanded and `%player%` is not replaced. For example, `%player_name%` reaches the target command literally. A deferred server command may run after later inline actions in the same list.

## `sound`

```json
{
  "type": "sound",
  "sound": "ui.button.click",
  "source": "master",
  "volume": 1,
  "pitch": 1
}
```

| Key | Required | Default | Meaning |
| --- | --- | --- | --- |
| `sound` | Yes | none | Bukkit sound registry key |
| `source` | No | `master` | `master`, `music`, `record`, `weather`, `block`, `hostile`, `neutral`, `player`, `ambient`, or `voice` |
| `volume` | No | `1` | Volume and audible-distance multiplier |
| `pitch` | No | `1` | Playback pitch |

The sound key is resolved and cached when component actions are built. An unknown or malformed key is logged and dropped without discarding the menu. Playback is positioned at the clicking player and sent only to that player; an explicit volume of `0` is silent, while values above `1` extend audible distance.

## `message`

```json
{
  "type": "message",
  "message": "<gold>Hello <white>%player%</white></gold>"
}
```

`message` is required and must contain non-whitespace text. On click, the literal `%player%` becomes the clicking player's name, PlaceholderAPI expands any installed `%...%` placeholders, legacy ampersand formatting is translated, and the result is parsed as MiniMessage before it is sent only to that player.

HoloUi recursively removes MiniMessage click events and insertion events before delivery. Formatting, gradients, decorations, and ordinary hover presentation remain available, but a message cannot become an `open_url`, `run_command`, `suggest_command`, clipboard, or insertion action. There is no general URL or arbitrary network menu action.

## `teleport`

```json
{
  "type": "teleport",
  "world": "minecraft:overworld",
  "x": 12.5,
  "y": 70,
  "z": -8,
  "yaw": 90,
  "pitch": 0
}
```

| Key | Required | Validation |
| --- | --- | --- |
| `world` | Yes | Explicit lowercase `namespace:key`, at most 255 characters |
| `x`, `y`, `z` | Yes | Finite JSON numbers |
| `yaw`, `pitch` | Yes | Finite JSON numbers in degrees |

All six destination fields are required. Invalid data is logged and the action is dropped when the component is built. At click time the world must already be loaded and resolvable by its Bukkit `NamespacedKey`; HoloUi does not create a world, load a chunk, or fall back to the player's current world.

The request is marshalled through `SchedulerUtils.runEntity` for the clicking player and uses `Player#teleportAsync(Location, PLUGIN)`. This is safe for cross-region Folia movement. The completion is not awaited, so later non-terminal actions can run after the teleport request has been accepted; a false or exceptional completion is logged with menu/component/player context, and the menu's normal `closeOnTeleport`, world, and range rules still apply.

## `connect`

```json
{ "type": "connect", "server": "lobby-1" }
```

`server` is the exact logical server name configured on a BungeeCord-compatible proxy. It must be 1–64 characters, start with a letter or number, and contain only letters, numbers, `.`, `_`, and `-`; invalid values are logged and dropped during action resolution.

HoloUi registers the outgoing `BungeeCord` plugin channel while enabled and sends exactly two UTF fields through the clicking player: the fixed subchannel `Connect`, then the validated server name. Authors cannot select another plugin-message subchannel, host, port, URL, or payload. Delivery is a request rather than a confirmation; without a compatible proxy configuration the player remains on the current server.

## `navigate`

```json
{ "type": "navigate", "mode": "push", "target": "shops/confirm" }
```

| Mode | Target | Effect |
| --- | --- | --- |
| `push` | Required | Opens the target and pushes the current page onto history |
| `replace` | Required | Opens the target without adding the current page to history |
| `back` | Ignored | Pops and opens the newest history entry |
| `home` | Ignored | Opens the flow root and clears history |
| `close` | Ignored | Closes the current flow |

An omitted mode defaults to `push`. Targets are exact, case-sensitive menu IDs, including forward-slash folder paths such as `shops/confirm`; push and replace also require the viewer to have `holoui.open.<target>`. Missing targets, denied permissions, cancelled open events, and empty history leave the current flow unchanged.

Navigation is terminal for the actions matching the current interaction whether or not the requested navigation succeeds. An unmatched navigation is skipped and does not stop later matching actions. A toggle whose matching action chain reaches navigation returns before swapping its icon or changing its state.

## Execution model

HoloUi accepts left or right clicks from the main hand and samples sneak state at the event. It ray-tests open personal-menu and world-board clickables, chooses the nearest unobstructed hit across both surfaces, cancels the interact event, and dispatches only that component. Personal-menu clicks fire the cancellable API click event before JSON actions and invoke an API handler after JSON actions when the handle remains live; board clicks fire the same event with no owner name or API-menu handler.

Within one component, HoloUi scans actions in declaration order. It skips bindings that are neither `any` nor the exact interaction, then executes matching actions in their original order. `command`, `sound`, `message`, `teleport`, and `connect` return `CONTINUE`; a matching `navigate` returns `STOP`. An exact-trigger navigation therefore does not affect another click type, while an `any` navigation is terminal on every click.

| Action path | Thread and timing |
| --- | --- |
| Player command, sound, message, connect | Inline on the clicking player's owning thread |
| Teleport | Player entity scheduler, then Paper's asynchronous teleport path |
| Server command | Deferred to the global/main scheduler |
| Navigation | Inline through the current personal or board viewer context |

The component dispatch catches `Exception`, logs the full stack trace with menu, component, and player context, and then continues normal click dispatch outside that component. A thrown inline action aborts the remaining actions in its list. A deferred console-command failure occurs later in its scheduler task and is not caught by the component's click-time guard.

## Resolution failures

Bad action data does not normally discard a valid menu. `MenuAction.resolve` removes only the invalid entry and keeps the surviving declaration order.

| Action | Dropped when |
| --- | --- |
| `command` | Command is missing, blank, or slash-only |
| `sound` | Sound key is missing, malformed, or absent from the registry |
| `message` | Message is missing or blank |
| `teleport` | World key is malformed, or any pose number is missing/non-finite |
| `connect` | Server name fails the fixed whitelist |
| `navigate` | Push/replace has no nonblank target |

Each configured failure is warned once per owning menu/component context. An unloaded but well-formed teleport world is checked at click time and warned once per menu/component/world key, allowing a later world load to make the same action usable without reloading the menu.

An unsupported in-memory `MenuActionData` implementation is also warned and skipped. By contrast, malformed JSON type discrimination happens earlier and prevents the entire menu file from registering.

## Absent-key reference

| Action | Missing key | Runtime result |
| --- | --- | --- |
| Any | `trigger` or `trigger: null` | Defaults to `any` |
| `command` | `source` | Defaults to player |
| `sound` | `source`, `volume`, `pitch` | Defaults to master, `1`, `1` |
| `message` | `message` | Action dropped |
| `teleport` | Any destination field | Action dropped |
| `connect` | `server` | Action dropped |
| `navigate` | `mode` | Defaults to push |
| `navigate` | `target` for back/home/close | Accepted |
| Any | `type` | Whole menu file fails to parse |

## Related

- [03 - Menu File Format.md](03%20-%20Menu%20File%20Format.md) — action placement and menu IDs
- [04 - Components & Hitboxes.md](04%20-%20Components%20%26%20Hitboxes.md) — clickable selection and toggles
- [07 - Expressions & Placeholders.md](07%20-%20Expressions%20%26%20Placeholders.md) — PlaceholderAPI behavior
- [11 - Runtime Architecture.md](11%20-%20Runtime%20Architecture.md) — scheduler and session ownership
- [12 - Web Editor & Schemas.md](12%20-%20Web%20Editor%20%26%20Schemas.md) — editor/runtime contract
