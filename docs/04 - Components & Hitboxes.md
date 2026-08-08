# Components & Hitboxes

A menu's `components` array holds the individual elements a player sees and clicks. This document covers the three component types (`button`, `decoration`, `toggle`), their JSON keys and runtime behaviour, the collision-plane hitbox system including the optional `hitbox` object on buttons, and how a left click is routed to one or more components.

## Component envelope

Every entry of `components` is an object with three keys.

| Key | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | yes | — | Component identifier. Used by the API for icon updates and click handlers, and in click/error log lines. |
| `offset` | vector3 | yes | — | Offset from the menu centre point. |
| `data` | object | yes | — | Type-discriminated component body; see below. |

`vector3` is always a three-element JSON array of numbers, `[x, y, z]`. The object form is not accepted.

Duplicate `id` values are accepted by the JSON parser, but `MenuSession` keeps only the first component with a given id. Later duplicates are ignored and logged while the session is constructed, so they do not render, tick, or receive clicks.

The runtime offset applied in the `MenuComponent` constructor is `offset * (-uiScale, uiScale, uiScale)`. The X component is negated, so a positive `offset[0]` places the component to the viewer's right. All three axes are multiplied by the `settings.json` `uiScale` (clamped to `[0.25, 4.0]`, default `1.0`). The resulting `Location` has its yaw and pitch forced to `0`.

The menu-level `offset` in the menu definition is negated on X but is **not** scaled by `uiScale`. Only component offsets and hitbox geometry scale. See `03 - Menu File Format.md`.

### `data` type discrimination

`data` is decoded on a `type` string, which is removed from the object before the concrete body is read. A missing `type`, a non-string `type`, or an unrecognized value throws `JsonParseException`. Matching is exact and case-sensitive.

| `type` | Record | Clickable |
| --- | --- | --- |
| `button` | `ButtonComponentData` | yes |
| `decoration` | `DecoComponentData` | no |
| `toggle` | `ToggleComponentData` | yes |

On serialization, `type` is re-inserted as a property of the emitted object.

## Button

```json
{
  "id": "confirm",
  "offset": [0.5, 0.0, 0.0],
  "data": {
    "type": "button",
    "highlightModifier": 0.05,
    "icon": { "type": "text", "text": "<green>Confirm" },
    "actions": [{ "type": "command", "command": "say confirmed", "source": "player" }]
  }
}
```

| JSON key | Type | Required | Default when absent |
| --- | --- | --- | --- |
| `highlightModifier` | number | no | `0.0` |
| `icon` | icon object | yes (schema) | `null` |
| `actions` | array of action objects | yes (schema) | `null` |
| `hitbox` | hitbox object | no | `null` — fully automatic hitbox |

The `Required` column reflects `schema/holoui.schema.json`. The Gson records do not enforce presence; a button decoded with no `hitbox` key yields `hitbox() == null`.

`actions` are resolved through `MenuAction.resolve` at construction (see `06 - Actions.md`). `icon` is resolved through `MenuIcon.createIcon` (see `05 - Icons.md`); a failure logs the exception and falls back to the "missing" text-image icon rather than dropping the component.

### Click behaviour

`onClick()` executes every resolved action in list order against the session. There is no per-component cooldown, no debounce, and no re-entrancy guard: one qualifying interact event runs the whole action list once.

### Highlight

Highlighting is handled once per tick while the component is open:

1. The collision plane is re-oriented to face the player's eye.
2. `plane.isLookingAt(eyePosition, eyeDirection)` decides selection.
3. On the transition not selected to selected, the icon is displaced by `plane.normal * highlightModifier`. The plane normal points back at the player, so a positive `highlightModifier` moves the icon toward the viewer.
4. On the transition selected to not selected, the icon is teleported back to the component `location`.
5. While selected, the icon is re-teleported to `location + normal * highlightModifier` each tick, so the displacement tracks the re-oriented plane.

`highlightModifier` is the entire hover feedback; there is no colour, scale, or sound change on hover. The selected flag is reset to `false` on close.

## Decoration

```json
{
  "id": "title",
  "offset": [0.0, 0.6, 0.0],
  "data": {
    "type": "decoration",
    "icon": { "type": "textImage", "path": "logo.png" }
  }
}
```

| JSON key | Type | Required | Default when absent |
| --- | --- | --- | --- |
| `icon` | icon object | yes (schema) | `null` |

`DecoComponent` overrides `onOpen`, `onTick`, and `onClose` as no-ops and is not a `ClickableComponent`. Consequently it has no `CollisionPlane` and is never ray-tested, never enters the selected state and has no highlight, is skipped when the click snapshot is built and can never receive a click, and accepts neither `highlightModifier` nor `hitbox`.

It still ticks its icon, so animated text images advance, and it still accepts API icon replacement through `applyIcon`.

## Toggle

```json
{
  "id": "flight",
  "offset": [-0.5, 0.0, 0.0],
  "data": {
    "type": "toggle",
    "highlightModifier": 0.05,
    "condition": "%player_gamemode%",
    "expectedValue": "CREATIVE",
    "trueIcon": { "type": "text", "text": "<green>On" },
    "falseIcon": { "type": "text", "text": "<red>Off" },
    "trueActions": [{ "type": "command", "command": "gamemode creative", "source": "server" }],
    "falseActions": [{ "type": "command", "command": "gamemode survival", "source": "server" }]
  }
}
```

| JSON key | Type | Required | Default when absent |
| --- | --- | --- | --- |
| `highlightModifier` | number | no | `0.0` |
| `condition` | string | yes (schema) | `null` |
| `expectedValue` | string | yes (schema) | `null` |
| `trueActions` | array of action objects | yes (schema) | `null` |
| `falseActions` | array of action objects | yes (schema) | `null` |
| `trueIcon` | icon object | yes (schema) | `null` |
| `falseIcon` | icon object | yes (schema) | `null` |

`ToggleComponentData` has no `hitbox` field and the schema's `toggleComponent` definition does not declare one. The constructor passes `null` as the hitbox, so a toggle's click plane is always icon-derived and always centred on the icon. Toggles cannot carry a custom hitbox.

### State

State is a single transient `boolean` field on the `ToggleComponent` instance. It is per player and per session: the component is constructed when the `MenuSession` is constructed and discarded when the session closes. It is not persisted anywhere — no PDC, no file, no database — and it is never written back to the placeholder named in `condition`. Reopening the menu re-evaluates the initial state from scratch; a manual toggle survives only for the lifetime of the open session.

The initial state is evaluated once, in the constructor:

```java
state = Placeholders.setPlaceholders(session.getPlayer(), condition).equalsIgnoreCase(expected);
```

`condition` is expanded as a placeholder string against the viewing player and compared case-insensitively to `expectedValue` (see `07 - Expressions & Placeholders.md`). It is never re-evaluated on tick, so external changes to the underlying placeholder do not update an open toggle.

The icon spawned at open is `trueIcon` when the state is true and `falseIcon` otherwise; only the returned icon is spawned.

### Click behaviour

```java
if (state) { falseActions...; swapIcon(falseIcon); state = false; }
else       { trueActions...;  swapIcon(trueIcon);  state = true; }
```

`trueActions` and `trueIcon` belong to the state being **entered**, not the state being left. Clicking a toggle that is currently `false` runs `trueActions` and shows `trueIcon`.

Both icons are constructed eagerly in the constructor and both are teleported on icon creation and on `move`, so the inactive icon stays positionally in sync without being spawned. `swapIcon` removes the current icon's display entities, teleports the replacement to `location`, rebuilds the collision plane from the new icon's bounding box, and spawns the replacement.

Like buttons, toggles have no cooldown: each qualifying interact event flips the state once.

## Hitbox system

### `hitbox` keys

`hitbox` is accepted on `button` only. Schema constraints are `minProperties: 1`, `additionalProperties: false`, and a `dependentRequired` binding of `width` and `height` to each other.

| JSON key | Type | Required | Default when absent |
| --- | --- | --- | --- |
| `width` | number | no; required if `height` is present | `null` — automatic sizing from the icon |
| `height` | number | no; required if `width` is present | `null` — automatic sizing from the icon |
| `offset` | vector3 | no | `null` — treated as `[0, 0, 0]` |
| `anchor` | `"button"` or `"menu"` | no | `null` — `button` |

`width` and `height` are expressed in blocks at `uiScale` 1 and are multiplied by the live `uiScale` at plane-build time. `offset` is likewise a pre-scale value; each axis is multiplied by `uiScale`, and an absent `offset` produces a zero vector.

Validation runs in the record's compact constructor and throws `IllegalArgumentException` during deserialization:

| Condition | Message |
| --- | --- |
| Exactly one of `width`/`height` supplied | `Button hitbox width and height must be supplied together.` |
| `width` non-finite or `<= 0` | `Button hitbox width must be finite and greater than zero.` |
| `height` non-finite or `<= 0` | `Button hitbox height must be finite and greater than zero.` |
| Any component of `offset` non-finite | `Button hitbox offset must contain only finite values.` |

`hasCustomSize()` returns `width != null`. A hitbox object carrying only `offset` and/or `anchor` therefore keeps automatic icon-derived dimensions while relocating the plane.

Round-trip is symmetric: `offset` serializes back to a three-element array and `anchor` back to its lowercase form.

```json
{
  "hitbox": {
    "width": 0.6,
    "height": 0.25,
    "offset": [0.0, -0.1, 0.0],
    "anchor": "button"
  }
}
```

### Anchor

| Value | Origin the `offset` is measured from |
| --- | --- |
| `button` | `planeOrigin` — the centre of the collision plane the icon produced at this component's `location`. Moves with the component. |
| `menu` | `session.getCenterInitialYAdjusted()` — the menu's centre point rotated about the player's eye by the session's `initialY`. Independent of the component's own position. |

A `button`-anchored plane translates when the button origin moves; a `menu`-anchored plane's centre is unaffected by the button origin. With `anchor: "menu"` and no `offset`, the plane sits exactly on the menu centre regardless of where the icon is drawn, so the visible icon and its click plane are fully decoupled.

### Offset axis convention

The scaled offset is composed against the plane's own basis:

```java
right * (-offset.x) + up * (offset.y) + normal * (-offset.z)
```

Because the plane is re-oriented to face the player each tick, the axes are viewer-relative: `+x` moves the plane to the viewer's right, `+y` up, and `+z` away from the viewer. The normal points back toward the player and `z` enters with a negative sign. This matches the sign convention used for component `offset`, whose X is also negated.

Worked example: offset `[0.5, -0.25, 0.75]` at `uiScale` 2 with basis `right=(1,0,0)`, `up=(0,1,0)`, `normal=(0,0,-1)` yields the translation `(-1, -0.5, 1.5)`. When the basis rotates, the translation rotates with it.

### Default hitbox derivation

When `hitbox` is absent or `hasCustomSize()` is false, the plane's dimensions and centre come entirely from the icon's bounding box. With `S = uiScale`, `NAMETAG_SIZE = 3.5 / 16 = 0.21875`, and `lineHeight = NAMETAG_SIZE * S`:

| Icon type | Plane width | Plane height | Plane centre |
| --- | --- | --- | --- |
| `text` | `max over lines of (plainTextLength * lineHeight / 2)` | `lineCount * lineHeight` | `location - (0, ((2 * NAMETAG_SIZE) - 4.5/40) * S, 0)` = `location - (0, 0.325 * S, 0)` |
| `textImage` | same formula | `(lineCount - 1) * lineHeight` | same as `text` |
| `animatedTextImage` | same formula, over the first frame | `(firstFrameLineCount - 1) * lineHeight` | same as `text` |
| `item` | `0.75 * S` | `0.75 * S` | `location - (0, 0.05 * S, 0)` |

For image icons the line length is the rendered row width in characters, so the plane width tracks image width. Text-image and animated-image heights are one line shorter than the text-icon formula.

The plane is rebuilt on open, on icon change (including toggle swaps and API icon updates), on `move`, and on `adjustRotation`. Each rebuild reads the current icon, records `planeOrigin` as the fresh bounding-box centre, applies the configured size when a custom size is present, re-applies the previous plane's pitch and yaw so orientation is not lost, and then repositions the plane. Repositioning is a no-op when `hitbox` is `null`, which is why an unconfigured button and every toggle keep a plane centred exactly on the icon bounding box.

### `CollisionPlane` ray test

`CollisionPlane` stores `center`, `width`, `height`, a basis of `up`, `right`, `normal`, and the `pitch`/`yaw` it was last rotated to. Base axes are `UP = (0,1,0)` and `RIGHT = (1,0,0)`; `normal = up x right`, which is `(0,0,-1)` in the unrotated case.

`rotate(pitch, yaw)` short-circuits when both values are unchanged. Otherwise `up` and `right` are recomputed from the base axes as `rotateAroundX(radians(pitch)).rotateAroundY(radians(yaw))` and the normal is recomputed from them.

Facing the player runs every tick, before the ray test:

```java
Vector rotation = MathHelper.getRotationFromDirection(MathHelper.unit(eye, plane.getCenter()));
plane.rotate((float) rotation.getX(), (float) -rotation.getY());
positionPlane();
```

`MathHelper.unit(a, b)` returns `b - a` and is not normalized despite the name, so the direction used is eye to plane centre. `getRotationFromDirection` returns pitch in `x` and yaw in `y`, and the yaw is negated when applied. The result is a plane whose `normal` points from the plane back at the player's eye and whose `right`/`up` span the viewer-facing rectangle.

The intersection test is a ray/plane intersection followed by a rectangle bound check:

```java
Vector offset = center - origin;
double proj = normal.dot(direction);
if (proj == 0) return false;
double distance = normal.dot(offset) / proj;
if (distance < 0.0F) return false;
Vector intersect = origin + direction * distance - center;
return abs(right.dot(intersect)) < width / 2 && abs(up.dot(intersect)) < height / 2;
```

Behaviour:

- Callers pass the player's eye position and `Location.getDirection()`, a unit vector, so `distance` is in blocks.
- There is no epsilon and no tolerance term. The bound check is a strict `<` against the exact half-extents, and the parallel case is rejected only on exact `proj == 0`.
- The test is not distance limited. Any positive `distance` qualifies, so a component remains selectable at arbitrary range. The menu's `maxDistance` is enforced separately for ordinary movement unless `lockPosition` takes the earlier freeze branch; teleport and respawn retain their own validity checks.
- The test is two-sided. Nothing rejects a hit on the back face; only a strictly negative `distance`, meaning the plane is behind the ray origin, is rejected.
- Nothing occludes the ray. Blocks, entities, and other components between the player and the plane do not block selection.

### Debug rendering

There is no hover outline in normal operation. Hitbox rendering is driven by a `MenuSessionManager` task that runs every 2 ticks and only while the `debugHitbox` setting (`HuiSettings.DEBUG_HITBOX`, default `false`) is enabled. It draws the four plane edges and the four corners with redstone dust particles interpolated in 0.1 steps in `Color.BLUE`, and the normal from the plane centre out to `centre + normal * 2` in `Color.RED`. It returns immediately when the plane is `null`.

A companion `debugPosition` setting (`HuiSettings.DEBUG_SPACING`, default `false`) draws the menu centre in `Color.YELLOW` and each component `location` in `Color.ORANGE`. See `01 - Installation & Configuration.md`.

## Click routing

### Which inputs reach components

The dispatcher is registered on `PlayerInteractEvent` at `EventPriority.MONITOR`.

| Input | `Action` | Reaches components |
| --- | --- | --- |
| Left click, no block in reach | `LEFT_CLICK_AIR` | yes |
| Left click on a block | `LEFT_CLICK_BLOCK` | yes |
| Right click, air or block | `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK` | no — returned early |
| Physical (pressure plate, tripwire) | `PHYSICAL` | no — returned early |

Sneak state is never inspected: there is no sneak-modified click path, and sneaking neither suppresses nor alters component clicks. There is no off-hand filter in the handler; whether an off-hand `LEFT_CLICK_*` event is delivered at all is decided by the server implementation.

The handler returns without doing anything when the player has no `SessionHolder`, or when the click snapshot reports no currently selected clickable components. When at least one component is selected, the event is cancelled before any action runs, so an in-range left click on a HoloUI component does not also break a block or swing at an entity. Cancellation happens only on a hit; a left click that misses every hitbox passes through untouched.

### Ordering and overlap

The click snapshot is built as:

```java
for (MenuComponent<?> component : current.getComponents())
  if (component instanceof ClickableComponent<?> clickable && clickable.isOpen() && clickable.isSelected())
    hits.add(clickable);
```

Consequences:

- Selection state is read from the previous tick's `onTick`, not recomputed at click time. A click targets whatever the last tick marked as selected.
- Overlapping hitboxes do not resolve to a single winner. Every selected component fires, in the declaration order of the menu's `components` array. There is no depth sort, no nearest-hit test, and no early exit.
- Decorations, closed components, and unselected components are excluded.
- The snapshot is taken once; the whole list is then processed even if an earlier component's action closes or replaces the session.

### Per-component dispatch

For each component in the snapshot, in order:

1. `ApiEvents.fireClick(player, menuId, componentId, ownerName)` — a cancelled event skips that component and continues with the next.
2. `component.onClick()` — wrapped in try/catch. A thrown exception is logged with the component id, menu id, and player name, and dispatch continues to the next component.
3. If the menu has a live `ApiMenuHandle` with a `HoloClickHandler` registered for that component id, the handler is dispatched through `ApiClickGuard`.

`ApiClickGuard` applies only to third-party API handlers, not to JSON `actions`. It skips owners that are quarantined or inactive, counts faults, quarantines an owner after `DEFAULT_FAULT_LIMIT = 5` thrown handler calls (its menus stay open but stop receiving clicks), and logs at most one slow-handler warning per owner per 60 s when a handler exceeds `DEFAULT_SLOW_MILLIS = 5`. See `14 - API - Menus.md`.

No stage of this path imposes a click cooldown or rate limit on JSON-defined components.

## Lifecycle

| Phase | `MenuComponent` | `ClickableComponent` addition |
| --- | --- | --- |
| Construct | Resolve id, compute scaled and negated offset, set `location` from the menu centre with yaw/pitch zeroed | Store `highlightModifier` and `hitbox`; toggles additionally evaluate the initial state and build both icons |
| `open()` | `adjustRotation`, create icon, spawn, `onOpen()`, set open | `onOpen()` rebuilds the plane |
| `tick()` | No-op unless open; `onTick()` then the icon's own tick | Face the player, ray test, selected-state transitions |
| `applyIcon` / `swapIcon` | Replace display entities, teleport, signal icon change | Icon change rebuilds the plane |
| `move(loc)` | Recompute `location` from the new centre plus offset, zero yaw/pitch | Teleport icon, rebuild the plane |
| `adjustRotation()` | Rotate about the player's eye by the session's `initialY`, teleport icon | Rebuild the plane |
| `close()` | Clear open, remove icon, `onClose()` | Clear selected |

See `11 - Runtime Architecture.md` for the session and tick loop that drives these phases.

## Runtime notes

- The schema descriptions for `trueActions` and `falseActions` state "during a true state", which describes the opposite of the implemented behaviour. The lists belong to the state being entered.
- Duplicate component `id` values do not reject the menu file. The first component with an id is used and later duplicates are ignored when a session opens.
- A toggle's state is not written back to the placeholder in `condition`, and the condition is never re-evaluated while the menu is open. An externally changed placeholder does not update a live toggle.
- Menu-level `offset` is not scaled by `uiScale`; component `offset` and hitbox geometry are.
- Hitbox planes are two-sided, unoccluded, and unbounded in range. A component behind a wall can still be selected; the menu's separate movement validity rules determine how far the player can travel from it.
- `anchor: "menu"` fully decouples the click plane from the drawn icon, which produces a clickable region with no visible element under it.
