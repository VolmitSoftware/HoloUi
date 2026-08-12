# Components & Hitboxes

A menu's `components` array holds the individual elements a player sees and clicks. This document covers the three component types (`button`, `decoration`, `toggle`), their JSON keys and runtime behaviour, the collision-plane hitbox system including the optional `hitbox` object on buttons, and how main-hand left and right clicks are routed to the nearest component.

## Component envelope

Every entry of `components` is an object with three keys.

| Key | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | yes | — | Component identifier. Used by the API for icon updates and click handlers, and in click/error log lines. |
| `offset` | vector3 | yes | — | Offset from the menu centre point. |
| `data` | object | yes | — | Type-discriminated component body; see below. |

`vector3` is always a three-element JSON array of numbers, `[x, y, z]`. The object form is not accepted.

Duplicate `id` values are accepted by the JSON parser, but `MenuSession` keeps only the first component with a given id. Later duplicates are ignored and logged while the session is constructed, so they do not render, tick, or receive clicks.

Each component retains its raw offset. The session's `MenuTransform` mirrors X, multiplies all three axes by the effective scale, then applies board roll around Z, pitch around X, and the negative menu-facing yaw around Y. Personal menus have zero pitch and roll; persistent boards can use all three rotations. A positive `offset[0]` therefore places the component to the viewing side's right. The resulting `Location` carries the fixed-display yaw (`facingYaw + 180`) and transform pitch; display metadata and the click plane also carry roll.

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

`onClick(trigger)` scans resolved actions in list order and executes only actions whose binding is `any` or the exact interaction trigger. There is no per-component cooldown, debounce, or re-entrancy guard. Navigation stops the matching action chain; see `06 - Actions.md`.

### Highlight

Highlighting is handled once per tick while the component is open:

1. The current icon aligns a non-fixed billboard plane to the live eye; `fixed` leaves the transform-aligned plane unchanged.
2. `plane.isLookingAt(eyePosition, eyeDirection)` decides selection.
3. On the transition not selected to selected, the icon is displaced by `plane.normal * highlightModifier`. A positive `highlightModifier` moves it toward the plane's current viewing side.
4. On the transition selected to not selected, the icon is teleported back to the component `location`.

Fixed icons and their planes share the menu transform, including follow-player yaw, pitch and roll. `vertical` planes turn around world Y, `horizontal` planes keep the menu's right axis and pitch toward the eye, and `center` planes face the eye on both axes. The runtime repeats this orientation at the event-time click snapshot, so a moving viewer cannot click against a stale billboard plane.

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

Like buttons, toggles have no cooldown. Each qualifying interact event runs only the matching branch actions and normally flips the state once. Reaching a matching navigation action returns before the icon and state change; navigation bound to another trigger is skipped and does not block the transition.

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
| `menu` | `session.getTransform().menuOrigin()` — the menu origin already resolved through the session's current anchor and facing. Independent of the component's own position. |

A `button`-anchored plane translates when the button origin moves; a `menu`-anchored plane's centre is unaffected by the button origin. With `anchor: "menu"` and no `offset`, the plane sits exactly on the menu centre regardless of where the icon is drawn, so the visible icon and its click plane are fully decoupled.

### Offset axis convention

The configured offset goes through the same local-vector transform as component and icon geometry:

```java
new Vector(-offset.x * uiScale, offset.y * uiScale, offset.z * uiScale)
    .rotateAroundZ(Math.toRadians(roll))
    .rotateAroundX(Math.toRadians(pitch))
    .rotateAroundY(Math.toRadians(-facingYaw))
```

The axes are menu-relative: `+x` moves the plane to the viewing side's right, `+y` up, and `+z` away from that side. The plane basis receives the same roll, pitch, and `-facingYaw`, so its centre, dimensions and normal remain aligned with a fixed rendered icon. A following personal menu updates yaw on turns; a followed board resolves the configured fixed, yaw, or full transform mode.

Worked example: offset `[0.5, -0.25, 0.75]` at `uiScale` 2 with basis `right=(1,0,0)`, `up=(0,1,0)`, `normal=(0,0,-1)` yields the translation `(-1, -0.5, 1.5)`. When the basis rotates, the translation rotates with it.

### Default hitbox derivation

When `hitbox` is absent or `hasCustomSize()` is false, the plane's dimensions and centre come entirely from the icon's bounding box. With `S = uiScale`, `X = style.scaleX`, `Y = style.scaleY`, `NAMETAG_SIZE = 3.5 / 16 = 0.21875`, `lineWidth = NAMETAG_SIZE * S * X`, and `lineHeight = NAMETAG_SIZE * S * Y`:

| Icon type | Plane width | Plane height | Plane centre |
| --- | --- | --- | --- |
| `text` | `max over lines of (plainTextLength * lineWidth / 2)` | `lineCount * lineHeight` | `location - (0, ((2 * NAMETAG_SIZE) - 4.5/40) * S * Y, 0)` = `location - (0, 0.325 * S * Y, 0)` |
| `textImage` | same formula | `(lineCount - 1) * lineHeight` | same as `text` |
| `animatedTextImage` | same formula, over the first frame | `(firstFrameLineCount - 1) * lineHeight` | same as `text` |
| `item` | `0.75 * S * X` | `0.75 * S * Y` | `location - (0, 0.05 * S, 0)` |

For image icons the line length is the rendered row width in characters, so the plane width tracks image width. Text-image and animated-image heights are one line shorter than the text-icon formula.

The plane is rebuilt on open, on icon change (including toggle swaps and API icon updates), and whenever the session transform changes through follow, manual movement or scale refresh. Each rebuild reads the current icon, records `planeOrigin` as the fresh bounding-box centre, applies the configured size when a custom size is present, and constructs the plane at the transform's current layout yaw. A custom `width` and `height` remain authored hitbox dimensions and do not multiply by icon style scale. Repositioning is a no-op when `hitbox` is `null`, which is why an unconfigured button and every toggle keep a plane centred exactly on the icon bounding box.

### `CollisionPlane` ray test

`CollisionPlane` stores `center`, `width`, `height`, a basis of `up`, `right`, `normal`, and the `pitch`/`yaw`/`roll` it was last rotated to. Base axes are `UP = (0,1,0)` and `RIGHT = (1,0,0)`; `normal = up x right`, which is `(0,0,-1)` in the unrotated case.

`rotate(pitch, yaw, roll)` short-circuits when all three values are unchanged. Otherwise `up` and `right` are recomputed from the base axes by roll around Z, pitch around X, then yaw around Y, and the normal is recomputed from them.

`MenuTransform.createPlane` initially constructs it with the current transform pitch, layout yaw `-facingYaw`, and roll:

```java
CollisionPlane plane = new CollisionPlane(center, width, height);
plane.rotate(pitch, -facingYaw, roll);
```

Before each hover ray test, and again before the event-time click intersection, `MenuIcon.orientHitbox` updates non-fixed billboard bases with `CollisionPlane.orient(up, right)`. Fixed planes retain the transform basis. `CollisionPlane.isLookingAt` then computes the ray intersection and projects it onto the current `right` and `up` axes.

The intersection test is a ray/plane intersection followed by a rectangle bound check:

```java
Vector offset = center - origin;
double proj = normal.dot(direction);
if (abs(proj) < 1.0E-9) return false;
double distance = normal.dot(offset) / proj;
if (distance < 0.0F) return false;
Vector intersect = origin + direction * distance - center;
return abs(right.dot(intersect)) < width / 2 && abs(up.dot(intersect)) < height / 2;
```

Behaviour:

- Callers pass the player's eye position and `Location.getDirection()`, a unit vector, so `distance` is in blocks.
- The parallel-ray epsilon is `1.0E-9`. The rectangle bound check has no tolerance term and uses a strict `<` against the exact half-extents.
- The test is not distance limited. Any positive `distance` qualifies, so a component remains selectable at arbitrary range. The menu's `maxDistance` is enforced separately for ordinary movement unless `lockPosition` takes the earlier freeze branch; teleport and respawn retain their own validity checks.
- The test is two-sided. Nothing rejects a hit on the back face; only a strictly negative `distance`, meaning the plane is behind the ray origin, is rejected.
- The plane test itself does not inspect occlusion. The event-time click dispatcher separately rejects the click when a block ray trace finds a block closer than the nearest HoloUi plane. Entities do not occlude it, and nearer HoloUi components win by intersection distance.

### Debug rendering

There is no hover outline in normal operation. Hitbox rendering is driven by a `MenuSessionManager` task that runs every 2 ticks and only while the `debugHitbox` setting (`HuiSettings.DEBUG_HITBOX`, default `false`) is enabled. It draws the four plane edges and the four corners with redstone dust particles interpolated in 0.1 steps in `Color.BLUE`, and the normal from the plane centre out to `centre + normal * 2` in `Color.RED`. It returns immediately when the plane is `null`.

A companion `debugPosition` setting (`HuiSettings.DEBUG_SPACING`, default `false`) draws the menu centre in `Color.YELLOW` and each component `location` in `Color.ORANGE`. See `01 - Installation & Configuration.md`.

## Click routing

### Which inputs reach components

The dispatcher is registered on `PlayerInteractEvent` at `EventPriority.HIGHEST`. It ignores an event already cancelled by an earlier listener.

| Main-hand input | `Action` | Exact `HoloClickTrigger` |
| --- | --- | --- |
| Left click while not sneaking | `LEFT_CLICK_AIR`, `LEFT_CLICK_BLOCK` | `LEFT_CLICK` (`left_click` in JSON) |
| Right click while not sneaking | `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK` | `RIGHT_CLICK` (`right_click` in JSON) |
| Left click while sneaking | `LEFT_CLICK_AIR`, `LEFT_CLICK_BLOCK` | `SHIFT_LEFT_CLICK` (`shift_left_click` in JSON) |
| Right click while sneaking | `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK` | `SHIFT_RIGHT_CLICK` (`shift_right_click` in JSON) |

Off-hand events and `PHYSICAL` are ignored. `ANY` is an action binding, not a physical interaction value delivered by `HoloClick` or `HoloUiMenuClickEvent`.

The handler ray-tests both the player's personal menu, when present, and visible interactive world-board views. If neither surface has a hit, or a block is closer than the nearest hit, the event passes through untouched. An unobstructed hit is cancelled before the API event and actions run, so the same input does not also perform its vanilla block interaction.

### Nearest-hit arbitration

`SessionHolder.snapshotClick` recomputes every open personal-menu plane against the event-time eye position and direction:

```java
for (MenuComponent<?> component : current.getComponents())
  if (component instanceof ClickableComponent<?> clickable)
    keep the smallest clickable.intersectionDistance(eye, direction);
```

Consequences:

- Click targeting is independent of the previous hover tick. Non-fixed billboard planes are re-oriented for the event-time eye before intersection.
- Only the smallest positive intersection distance fires. Within a personal menu, an exact-distance tie keeps the first declared component because replacement uses strict `<`.
- Board candidates are limited by each board's interaction range and visibility. The nearest board candidate is compared with the personal candidate; an exact tie goes to the personal menu.
- Decorations and closed clickables have no intersection. Selection and highlight remain tick-driven presentation state, not a click prerequisite.
- A block ray trace from the eye to the winning distance rejects a closer obstruction. Passable blocks and fluids do not obstruct this check.

### Winning-component dispatch

The one winning component receives the exact `HoloClickTrigger`:

1. `ApiEvents.fireClick(player, menuId, componentId, ownerName, trigger)` fires the cancellable event. Cancellation skips the component's JSON actions and API handler.
2. `component.onClick(trigger)` runs the matching JSON actions inside a try/catch. A thrown exception is logged with component, menu, and player context.
3. For a personal API menu, if its `ApiMenuHandle` remains live and has a `HoloClickHandler` for the component id, the handler receives a `HoloClick` carrying the same trigger through `ApiClickGuard`. World boards have JSON actions and the public event but no API-menu handler.

`ApiClickGuard` applies only to third-party API handlers, not to JSON `actions`. It skips owners that are quarantined or inactive, counts faults, quarantines an owner after `DEFAULT_FAULT_LIMIT = 5` thrown handler calls (its menus stay open but stop receiving clicks), and logs at most one slow-handler warning per owner per 60 s when a handler exceeds `DEFAULT_SLOW_MILLIS = 5`. See `14 - API - Menus.md`.

No stage of this path imposes a click cooldown or rate limit on JSON-defined components.

## Lifecycle

| Phase | `MenuComponent` | `ClickableComponent` addition |
| --- | --- | --- |
| Construct | Resolve id, retain the raw offset, and resolve the initial `location` through `MenuTransform` | Store `highlightModifier` and `hitbox`; toggles additionally evaluate the initial state and build both icons |
| `open()` | Apply the current transform, create icon, spawn, `onOpen()`, set open | `onOpen()` rebuilds the plane |
| `tick()` | No-op unless open; tick the icon, refresh dynamic geometry when its revision changed, then call `onTick()` | Align non-fixed billboard plane, ray test, selected-state transitions |
| `applyIcon` / `swapIcon` | Replace display entities, apply the current transform, signal icon change | Icon change rebuilds the plane |
| `applyTransform()` | Recompute `location`, display orientation and local geometry from the session transform | Teleport icon, rebuild the plane at the same yaw and scale |
| `close()` | Clear open, remove icon, `onClose()` | Clear selected |

See `11 - Runtime Architecture.md` for the session and tick loop that drives these phases.

## Runtime notes

- The schema descriptions for `trueActions` and `falseActions` state "during a true state", which describes the opposite of the implemented behaviour. The lists belong to the state being entered.
- Duplicate component `id` values do not reject the menu file. The first component with an id is used and later duplicates are ignored when a session opens.
- A toggle's state is not written back to the placeholder in `condition`, and the condition is never re-evaluated while the menu is open. An externally changed placeholder does not update a live toggle.
- Menu-level `offset` is not scaled by `uiScale`; component `offset` and hitbox geometry are.
- Automatic hitboxes multiply their width and height by icon `style.scaleX` and `style.scaleY`; an explicitly sized hitbox does not.
- Hitbox planes are two-sided and the hover ray is unoccluded and unbounded in range. Event-time clicks reject a closer block; the menu's separate movement validity rules determine how far the player can travel from a personal menu.
- `anchor: "menu"` fully decouples the click plane from the drawn icon, which produces a clickable region with no visible element under it.
