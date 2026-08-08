# API - Menus

`art.arcane.holoui.api` lets another plugin describe a holographic menu in code, put it in front of one
player, change what it says while it is on screen, receive clicks, and close it. This document is the
reference for the menu builder, the component and icon types, the session handle, click dispatch and
the two menu events. Service acquisition, dependency coordinates and the type index are in
"13 - API - Getting Started.md"; the preview API types are in "16 - API - Previews.md".

There are two ways in, and they yield different handles:

| Goal                                                                 | Call                                   |
|----------------------------------------------------------------------|----------------------------------------|
| Build the menu in code, receive clicks, change text while it is open | `open(Plugin, Player, HoloMenu)`       |
| Show a menu an admin already wrote in `plugins/holoui/menus/`         | `open(Plugin, Player, String menuId)`  |

A handle from the `String` overload cannot mutate anything and never receives a click. It carries
lifecycle only.

---

## A menu is a value

```java
public record HoloMenu(String id, double offsetX, double offsetY, double offsetZ, boolean lockPosition,
                       boolean followPlayer, double maxDistance, boolean closeOnDeath,
                       boolean closeOnTeleport, List<HoloComponent> components)

public static HoloMenuBuilder builder()
```

The compact constructor sanitises `id`, requires non-null `components`, replaces the list with
`List.copyOf(components)`, and then rejects duplicate component ids. A menu is immutable and reusable:
hand the same instance to `open` as many times as you like.

### `HoloMenuBuilder`

```java
public HoloMenuBuilder id(String id)
public HoloMenuBuilder offset(double x, double y, double z)
public HoloMenuBuilder lockPosition(boolean lockPosition)
public HoloMenuBuilder followPlayer(boolean followPlayer)
public HoloMenuBuilder maxDistance(double maxDistance)
public HoloMenuBuilder closeOnDeath(boolean closeOnDeath)
public HoloMenuBuilder closeOnTeleport(boolean closeOnTeleport)
public HoloMenuBuilder component(HoloComponent component)
public HoloMenu build()
```

| Method            | Default    | Meaning                                                                              |
|-------------------|------------|--------------------------------------------------------------------------------------|
| `id`              | `""`       | Required in practice: `build()` throws `IllegalArgumentException` on the empty default |
| `offset`          | `0, 0, 2`  | Menu centre relative to the player, in blocks, as `x, y, z`. Not scaled by `uiScale`   |
| `lockPosition`    | `false`    | Freezes the player in place while the menu is open. Takes priority over ordinary movement range validation and `followPlayer` |
| `followPlayer`    | `false`    | Re-centres the menu on the player on every move                                       |
| `maxDistance`     | `8.0`      | Range gate; see below                                                                 |
| `closeOnDeath`    | `true`     | Close on `PlayerDeathEvent`                                                           |
| `closeOnTeleport` | `true`     | Close on any teleport                                                                 |
| `component`       | empty list | Appends one component; call repeatedly                                                |

Every setter returns the builder. No setter validates or sanitises: `id` stores the raw string,
`component` accepts anything including a duplicate id or `null`, and all validation happens in
`build()` — that is, in the `HoloMenu` compact constructor `build()` calls.

The builder is mutable and not thread-safe. `build()` may be called more than once; each call produces
an independent `HoloMenu` from the builder's current state.

Throw sites reachable from `build()`:

| Condition                                | Thrown                                                                     |
|------------------------------------------|-----------------------------------------------------------------------------|
| `id` is `null`                           | `IllegalArgumentException("id must not be null")`                           |
| No character of `id` survives filtering  | `IllegalArgumentException("id must contain at least one of A-Z a-z 0-9 _ - .")` |
| `components` is `null`                   | `NullPointerException("components")`                                        |
| A component in the list is `null`         | `NullPointerException` from `List.copyOf`                                    |
| Two components share a sanitised id       | `IllegalArgumentException("duplicate component id: …")`                      |

`List.copyOf` runs before the distinct-id check, so a null element always produces the
`NullPointerException` and never reaches the id validation.

### Range gate

The session closes with `HoloCloseReason.MOVED_OUT_OF_RANGE` when the player's location fails
`centerPoint.distanceSquared(loc) <= maxDistance² + offsetLengthSquared`, where `offsetLengthSquared`
is the squared length of the menu-level offset. The menu's own standoff is therefore slack on top of
`maxDistance`. Leaving the menu's world fails the same test and closes the session. For an ordinary
move event, `lockPosition` freezes the destination and returns before this range check; teleport and
respawn keep their separate range and close checks.

### Coordinate frame

Offsets are player-relative, captured from the player's yaw at the instant the menu opens: `+X` right,
`+Y` up, `+Z` in front. The frame does not re-orient afterwards, so walking around an open menu walks
around a fixed object. A button's collision plane is re-aimed at the player's eye every tick, so a
button stays clickable from any angle it is visible from.

The menu-level `offset(...)` is applied verbatim. Component offsets are additionally multiplied by the
server's `uiScale` setting (`plugins/holoui/settings.json`, default `1.00`, clamped to `0.25 .. 4.00`),
so a menu laid out at `uiScale = 1.00` spreads or tightens when an admin changes it while the overall
standoff distance stays put. `debugHitbox` and `debugPosition` draw button hitboxes and anchors as
particles. No other setting affects API menus.

### Id sanitisation

`HoloText.sanitizeId` is applied to `HoloMenu#id` and to every component id.

- Allowed characters are `A-Z a-z 0-9 _ - .`; every other character is dropped, not replaced.
- The result is truncated to the first 64 allowed characters (`HoloText.MAX_ID_LENGTH`).

Because filtering is silent, `"stock count"` becomes `"stockcount"`, and `handle.setText("stock count",
…)` then returns `false` because the component is registered under the name that survived. Keep every
id inside the allowed set and the two can never diverge.

Component ids must be unique within a menu. The only throw site is the `HoloMenu` compact constructor;
`HoloMenuBuilder#component` performs no validation.

A menu id that collides with a file in `plugins/holoui/menus/` is a live hazard: HoloUi closes every
session whose id matches a changed or deleted file, compared case-insensitively, with
`HoloCloseReason.DEFINITION_RELOADED`, including API-built sessions. Namespace your ids
(`exampleshop.store`, not `shop`).

---

## Components

```java
public sealed interface HoloComponent permits HoloComponent.Decoration, HoloComponent.Button {
  float DEFAULT_HIGHLIGHT_MODIFIER = 0.05F;

  String id();
  double offsetX();
  double offsetY();
  double offsetZ();
  HoloIcon icon();

  record Decoration(String id, double offsetX, double offsetY, double offsetZ, HoloIcon icon)
      implements HoloComponent {}

  record Button(String id, double offsetX, double offsetY, double offsetZ, HoloIcon icon,
                float highlightModifier, HoloClickHandler handler) implements HoloComponent {}

  static HoloComponent decoration(String id, double x, double y, double z, HoloIcon icon);
  static HoloComponent button(String id, double x, double y, double z, HoloIcon icon,
                              HoloClickHandler handler);
}
```

A decoration draws and nothing else: no hitbox, no clicks. A button has a hitbox derived from its icon,
leans toward the player when selected, and calls your handler on a left-click. Text and image hitboxes
are centred on the visible glyph stack rather than the logical anchor, so aiming at what is drawn is
what activates the button. Component offsets are relative to the menu centre, in the same
right/up/forward frame.

Constraints:

- Both records sanitise `id`. A `null` id throws `IllegalArgumentException`, not
  `NullPointerException`.
- Both records reject a null `icon` with `NullPointerException("icon")`, and `Button` rejects a null
  `handler` with `NullPointerException("handler")`.
- `highlightModifier` — how far, in blocks, the icon leans toward the player when selected — is clamped
  into `0.0F .. 1.0F`; a non-finite value becomes `0.0F`. The `button(...)` factory supplies
  `DEFAULT_HIGHLIGHT_MODIFIER` (`0.05F`); use the canonical constructor to choose your own.

```java
new HoloComponent.Button("close", 0.0D, -0.6D, 0.0D, HoloIcon.text("<red>[X]"), 0.2F,
    click -> click.handle().close());
```

API-authored buttons always use automatic, icon-derived hitbox dimensions and button-anchored
alignment. The `hitbox` overrides available to file-backed JSON buttons, described in
"04 - Components & Hitboxes.md", have no API equivalent.

---

## Icons

```java
public sealed interface HoloIcon permits HoloIcon.Text, HoloIcon.Item, HoloIcon.Image,
                                         HoloIcon.AnimatedImage {
  record Text(String miniMessage) implements HoloIcon {}
  record Item(ItemStack stack) implements HoloIcon {}
  record Image(String relativePath) implements HoloIcon {}
  record AnimatedImage(List<String> relativePaths, int tickSpeed) implements HoloIcon {}

  static HoloIcon text(String miniMessage);
  static HoloIcon item(ItemStack stack);
  static HoloIcon image(String relativePath);
  static HoloIcon animatedImage(List<String> relativePaths, int tickSpeed);
}
```

| Shape           | Renders                                                 | Constraints                                                                                                                                           |
|-----------------|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Text`          | MiniMessage markup; `\n` splits into stacked lines       | Truncated at 4096 characters. Every control character except `\n` becomes a space. `null` and `""` both become `""`, which is legal and renders nothing  |
| `Item`          | A floating item display                                 | `NullPointerException("stack")` on null. Cloned on construction and on every `stack()` read, so your `ItemStack` stays yours to mutate                   |
| `Image`         | A picture from `plugins/holoui/images/`                 | `IllegalArgumentException` when blank, over 256 characters, containing a control character or `:`, starting with `/`, or containing a `..` segment. `\` becomes `/` |
| `AnimatedImage` | Those frames in order, advancing every `tickSpeed` ticks | Every path sanitised as above; list copied; empty or null list rejected with `IllegalArgumentException`; `tickSpeed` clamped to at least `1`             |

If your `ItemStack` subclass throws from `clone()`, that exception propagates out of `open`. An image
file that is missing or unreadable at render time does not fail the open: HoloUi logs it and draws a
visible placeholder in that slot.

---

## Opening, closing and querying

```java
public interface HoloUiService {
  HoloMenuHandle open(Plugin owner, Player player, HoloMenu menu);
  HoloMenuHandle open(Plugin owner, Player player, String menuId);
  boolean close(Player player);
  boolean isOpen(Player player);
  Set<String> menuIds();
}
```

`open` never returns null. An ordinary refusal comes back as a handle that is already terminal, with
the reason delivered to the close callback. It throws in exactly two cases:

- A null `owner`, `player`, `menu` or `menuId` raises `NullPointerException`.
- An exception thrown while translating your menu, such as an `ItemStack.clone()` failure, propagates to
  the caller. No handle is registered and nothing is left behind.

`close(Player)` and `isOpen(Player)` tolerate a null player and answer `false`.

`owner` is your plugin instance. HoloUi captures `owner.getName()` and a `BooleanSupplier` bound to
`owner::isEnabled` (`ApiOwner`), and uses them to name you in logs and in
`HoloUiMenuOpenEvent#getOwnerPluginName`, to skip your handlers once you are disabled, and to close
your menus when your plugin unloads.

### The two overloads are not equivalent

|                                   | `open(…, HoloMenu)`        | `open(…, String menuId)`                             |
|-----------------------------------|----------------------------|------------------------------------------------------|
| Menu source                       | your code                  | `plugins/holoui/menus/<menuId>.json`                 |
| Permission check                  | none                       | `holoui.open.<menuId>` on the player                 |
| Click handlers                    | yours, dispatched to you   | the JSON file's own actions; nothing reaches your code |
| `setText` / `setItem` / `setIcon` | work                       | always return `false`                                |
| `handle.menuId()`                 | the sanitised `menu.id()`  | exactly the string you passed                        |

The `String` overload constructs the handle with an empty handler map and an empty component-id set, so
every setter refuses and no click is ever routed to you. What you get is a lifecycle handle:
`state()`, `onClosed(…)`, `close()`.

The id lookup is an exact `ConcurrentHashMap` lookup against the loaded menu registry, whose keys are
JSON file base names. `"Welcome"` will not find `welcome.json`; a miss terminates the handle with
`HoloCloseReason.DENIED`. `menuIds()` returns the loaded ids.

The permission check is `player.hasPermission("holoui.open." + definition.getId())`, where the loaded
definition's id is the file base name — identical to the key you passed, since the lookup is exact.
`plugin.yml` does not declare `holoui.open` or any `holoui.open.*` child. The per-menu node is
runtime-only; Bukkit's default for undeclared permissions is op-only, so operators pass and everyone
else needs an explicit grant from a permission plugin. There is no parent wildcard. See
"02 - Commands & Permissions.md".

### One menu per player

A player has at most one menu open. Opening a second replaces the first, and the replaced handle
terminates with `HoloCloseReason.REPLACED`. That covers replacement by `/holoui open`, by another
plugin, and by you. Replacement happens before the new session is constructed; if that construction
or component opening fails, the previous handle still ends as `REPLACED`, partial display entities and
session state are cleaned up, and the incoming API handle ends as `OPEN_FAILED`. If the player is offline when the open task runs, the new handle terminates with
`QUIT` and the existing session is left alone.

`close(Player)` closes whatever that player has open, whoever opened it, with `CLOSED_BY_OWNER`, and
does not record the session in the `/holoui back` history; `/holoui close` does record it. `close`
returns `false` when there was nothing to close and `true` when the teardown was accepted onto the
player's entity scheduler — accepted, not necessarily already run.

To close only your own session, use `handle.close()`. It routes through `destroySessionFor`, which
compares session identity, so it no-ops when the player's current session is not this handle's.

### Why `open` resolves inline on the owning thread

`open` never reads the player's world state on your thread — the only thing it takes from the `Player`
before dispatch is the UUID. Everything else runs inside a task submitted through
`FoliaScheduler.runEntity(plugin, player, task, 0L, retired)`. That call runs the task inline when the
delay is zero and the entity is owned by the calling region, and otherwise submits it to the entity
scheduler. Two consequences:

1. Called from the owning thread, the whole open resolves before `open` returns and the handle is
   already `OPEN`, `CLOSED` or `FAILED`. Called from anywhere else, you get a `PENDING` handle and the
   menu appears a tick or more later. Code that reads `handle.state()` immediately after `open` is
   correct on one thread and wrong on the other, with no error either way.
2. HoloUi cannot protect the code around your call. If you built the `HoloMenu` from the player's
   inventory or a nearby block on an async thread, you already read world state illegally.

To hop explicitly (`Bukkit.isOwnedByCurrentRegion` and `Entity#getScheduler()` are Paper API — present
on Paper and Folia, absent on plain Spigot):

```java
if (Bukkit.isOwnedByCurrentRegion(player)) {
    holoUi.open(plugin, player, menu);
} else {
    player.getScheduler().run(plugin, task -> holoUi.open(plugin, player, menu), null);
}
```

On Folia, when the entity scheduler refuses the task, HoloUi logs a warning and refuses the global
fallback; the handle terminates `FAILED` with `OPEN_FAILED`. On non-Folia servers it falls back to
`SchedulerUtils.runGlobal`.

---

## Handles and state

```java
public interface HoloMenuHandle {
  UUID sessionId();
  UUID playerId();
  String menuId();
  HoloMenuState state();
  boolean setText(String componentId, String miniMessage);
  boolean setItem(String componentId, ItemStack stack);
  boolean setIcon(String componentId, HoloIcon icon);
  void close();
  HoloMenuHandle onClosed(Consumer<HoloCloseReason> callback);
}

public enum HoloMenuState {
  PENDING, OPEN, CLOSED, FAILED;
  public boolean terminal();   // true for CLOSED and FAILED
}
```

### Lifecycle

```
open(...)  ->  handle, never null
   |
   v
PENDING ---- session created ----> OPEN
   |                                |
   +---------------+----------------+
                   |  exactly one close reason
          +--------+--------+
          v                 v
       FAILED             CLOSED
  DENIED, OPEN_FAILED   every other reason
```

Guarantees, implemented in `ApiHandleState` over a single `AtomicReference<Snapshot>`:

- A handle is one session. It is never reused or reopened; `sessionId()` is a fresh `UUID` per handle.
  A second `open` for the same player yields a different handle.
- State moves forward only. `markOpen()` is a compare-and-set that succeeds only from `PENDING`, so
  `PENDING → OPEN` happens at most once. `PENDING → CLOSED/FAILED` skips `OPEN` when the open never
  lands.
- Termination is exactly once. `terminate` CAS-loops until the snapshot is terminal; the first reason
  wins, and every later attempt returns `false` and never reaches your callback.
- `FAILED` means the menu was never on screen. Exactly two reasons produce it: `DENIED` and
  `OPEN_FAILED`. Every other reason produces `CLOSED`.
- A handle does not survive the player leaving. `PlayerQuitEvent` closes the session with `QUIT`, and a
  player who logs out before a `PENDING` open lands also gets `QUIT`, delivered through the entity
  scheduler's retired callback. There is no reattach on rejoin.
- A handle does not survive your plugin disabling; see "13 - API - Getting Started.md".

| `HoloMenuState` | Meaning                                                                                                     |
|-----------------|--------------------------------------------------------------------------------------------------------------|
| `PENDING`       | Accepted, not yet on screen. Setters are accepted and staged; the updates apply on the first tick after open  |
| `OPEN`          | On screen                                                                                                     |
| `CLOSED`        | It was on screen and is not any more, or it ended before opening for a non-failure reason                     |
| `FAILED`        | It never reached the player. Only `DENIED` and `OPEN_FAILED` produce this                                     |

### `HoloCloseReason`

| Constant              | Resulting state | Cause                                                                                   |
|-----------------------|-----------------|------------------------------------------------------------------------------------------|
| `CLOSED_BY_OWNER`     | `CLOSED`        | `handle.close()` or `HoloUiService.close(Player)`                                          |
| `CLOSED_BY_COMMAND`   | `CLOSED`        | `/holoui close`                                                                            |
| `REPLACED`            | `CLOSED`        | Another menu opened for the same player                                                    |
| `MOVED_OUT_OF_RANGE`  | `CLOSED`        | The player failed the range gate, including leaving the world                              |
| `DEATH`               | `CLOSED`        | The player died and `closeOnDeath` was set                                                 |
| `RESPAWN`             | `CLOSED`        | The respawn point was out of range. Only reachable with `closeOnDeath(false)`              |
| `TELEPORT`            | `CLOSED`        | The player teleported and `closeOnTeleport` was set, or landed out of range                |
| `QUIT`                | `CLOSED`        | The player logged out, including before a `PENDING` open landed                            |
| `DEFINITION_RELOADED` | `CLOSED`        | A JSON menu whose id matches this session's, case-insensitively, changed or was deleted    |
| `OWNER_DISABLED`      | `CLOSED`        | The plugin that opened the menu was disabled                                               |
| `DENIED`              | `FAILED`        | Unknown menu id, missing `holoui.open.<id>`, or a cancelled `HoloUiMenuOpenEvent`           |
| `OPEN_FAILED`         | `FAILED`        | HoloUi was shut down, the scheduler refused the task, or the open threw                    |
| `HOLOUI_SHUTDOWN`     | `CLOSED`        | HoloUi disabled or reloaded while the menu was open                                        |

Both enums may gain constants; always write a `default` arm over either one, or use
`HoloMenuState.terminal()`.

### The close callback

```java
HoloMenuHandle onClosed(Consumer<HoloCloseReason> callback);
```

- Returns the handle, so it chains onto `open`.
- There is one callback slot (`AtomicReference`). Registering a second replaces the first; passing
  `null` clears it.
- It fires exactly once. `fire` uses `getAndSet(null)`, so HoloUi drops the reference the moment it
  fires. Registering on an already-terminal handle fires immediately and inline, with the stored
  reason. That is what makes this ordering correct even when `open` resolved synchronously:

  ```java
  HoloMenuHandle handle = holoUi.open(plugin, player, menu);
  live.put(player.getUniqueId(), handle);
  handle.onClosed(reason -> live.remove(player.getUniqueId()));
  ```

  Publish the handle first, then register. If the open already failed, the callback runs before
  `onClosed` returns and cleans up the entry you just wrote.
- The callback runs on whichever thread terminated the handle: the player's region thread in the
  ordinary cases, the `PlayerQuitEvent` thread on quit, HoloUi's disable thread on shutdown, and your
  own thread for a late registration. Treat it as bookkeeping only and schedule world work onto the
  player's entity scheduler.
- If your callback throws, HoloUi logs one `WARNING` naming your plugin and continues. The exception
  never reaches the session teardown.

---

## Changing what is on screen

```java
boolean setText(String componentId, String miniMessage);   // wraps HoloIcon.text
boolean setItem(String componentId, ItemStack stack);      // wraps HoloIcon.item
boolean setIcon(String componentId, HoloIcon icon);
```

Each stages a new icon for one component and returns whether it was accepted. `false` means one of:

- `componentId` is null, or is not in the component-id set the handle was built with — always the case
  for the `String` overload;
- the handle is terminal;
- for `setItem` and `setIcon` only, the value was null.

`setText(id, null)` is accepted and blanks the component, because `HoloIcon.text(null)` sanitises to
`""`.

Staging is not drawing. Updates land in `ApiPendingIcons`, a `ConcurrentHashMap` plus a volatile dirty
flag. `MenuSession#drainApiUpdates()` drains it on the player's region thread on the menu tick, which
runs every tick, so an accepted update is on screen within about 50 ms. Consequences:

- Updates coalesce per component. Repeated `setText` calls on the same component between two ticks
  produce one visual change carrying the last value, so driving a setter from a tight loop does not
  generate packet spam.
- `true` means accepted, not rendered. If the session ends in the same tick, `terminate` calls
  `pending.discard()` and the update is dropped.
- A staged update whose component id exists on the handle but not in the live session is silently
  skipped by the drain. That is reachable when a handle outlives a definition swap.
- If applying an icon throws, HoloUi logs it and that one component keeps its previous icon; the drain
  and the session continue.

Changing text in place keeps the same display entities as long as the icon is already text and the new
markup has the same number of `\n`-separated lines; only differing lines are re-sent. Changing the line
count, or changing the kind of icon, tears the old icon down and spawns a new one. Either way a
button's hitbox is re-derived from whatever it now draws.

Placeholders resolve once, at open, then freeze. Any PlaceholderAPI placeholder inside a text icon is
expanded when that icon is built and baked into the display entity; it does not re-expand on a timer or
when the value changes. What updates live is `setText` / `setItem` / `setIcon`, whose replacement
string is expanded again at the moment you set it. For live content, put a placeholder-free string in
the definition and drive the component from your own task or click handler. With PlaceholderAPI absent,
no expansion happens at all and the literal `%…%` text is what the player sees. See
"15 - API - Placeholders.md".

### After termination

After termination, `setText`, `setItem` and `setIcon` return `false`, `close()` is a no-op, and staged
updates are discarded. Checking `state().terminal()` first is an optimisation, not a requirement.

---

## Clicks

```java
@FunctionalInterface
public interface HoloClickHandler {
  void onClick(HoloClick click);
}

public record HoloClick(Player player, String menuId, String componentId, HoloMenuHandle handle)
```

`HoloClick` rejects a null `player` or `handle` with `NullPointerException`; `menuId` and `componentId`
are unchecked. `handle` is the same instance `open` returned, so a handler can mutate or close the menu
it is in with no bookkeeping.

### Dispatch

HoloUi listens to `PlayerInteractEvent` at `EventPriority.MONITOR` (`MenuSessionManager#dispatchClick`).
Per click:

1. Only `Action.LEFT_CLICK_AIR` and `Action.LEFT_CLICK_BLOCK` are considered; everything else returns.
2. The player's session is snapshotted. The snapshot lists every clickable component that is currently
   open and selected — its collision plane is under the player's crosshair — in menu declaration order.
   If nothing is selected the snapshot is null and the interact event is left alone.
3. If at least one component was hit, the interact event is cancelled. A left-click inside an open menu
   that hits nothing is not cancelled. HoloUi cancels at `MONITOR`, the last priority, so earlier
   listeners have already seen the event uncancelled; cancelling here stops the vanilla action, not
   other listeners.
4. For each hit component, in order:
   - `HoloUiMenuClickEvent` is fired. If a listener cancels it, that component is skipped entirely —
     both its file-authored actions and your handler.
   - The component's own JSON actions run (`component.onClick()`). For an API-built menu the action
     list is empty, so this is a no-op. An exception here is logged and does not stop the next step.
   - If the handle is non-null and still live, its `HoloClickHandler` for that component id, if any, is
     dispatched through the click guard.

More than one component can be hit by one click: overlapping hitboxes all fire. Space your buttons if
that is not what you want.

Handlers run on the clicking player's region thread. Reading and mutating that player's inventory,
experience and location is legal there. Do not block that thread: no I/O, no `CompletableFuture.join`,
no `callSyncMethod`, no lock held across the call. That thread ticks every entity and every open menu
in the region.

### The click guard

`ApiClickGuard` wraps every dispatch. HoloUi constructs one per `MenuSessionManager` with
`ApiClickGuard.DEFAULT_FAULT_LIMIT` (`5`) and `ApiClickGuard.DEFAULT_SLOW_MILLIS` (`5`), keyed by
plugin name. `dispatch` returns an `ApiClickOutcome`:

| Outcome                  | When                                                          |
|--------------------------|---------------------------------------------------------------|
| `DISPATCHED`             | The handler ran and returned normally                         |
| `FAULTED`                | The handler, or the `isEnabled()` probe, threw a `Throwable`  |
| `NO_HANDLER`             | No handler is registered for that component id                |
| `SKIPPED_OWNER_DISABLED` | `owner.active()` returned `false`                             |
| `SKIPPED_QUARANTINED`    | The owning plugin is quarantined                              |

Behaviour:

- Faults. A throwing handler is logged at `WARNING` naming your plugin, the component and the menu, and
  the click is swallowed. The owner's fault counter increments. `Plugin#isEnabled()` throwing counts as
  a fault too, and the handler is not called.
- Quarantine. At `DEFAULT_FAULT_LIMIT` (5) accumulated faults, the plugin is quarantined: one `SEVERE`
  log line, and its handlers are no longer called. Its menus stay on screen and still close normally.
  Fault counts are never decayed, so quarantine is reached by lifetime total, not by a rate.
- Slow-handler warning. A dispatch taking `DEFAULT_SLOW_MILLIS` (5 ms) or more logs one `WARNING` per
  plugin per 60 seconds. It never changes the outcome. The timing window also covers the
  `owner.active()` probe.
- Reset. `forget(ownerName)` clears quarantine, fault count and slow-warning timestamp. HoloUi calls it
  from `PluginDisableEvent` for the disabling plugin, so a quarantined plugin that is disabled starts
  clean when it comes back. The whole guard is also replaced whenever HoloUi reloads.

`ApiClickOutcome` and the guard's counters (`dispatches()`, `faults()`, `skips()`, `quarantines()`) are
internal telemetry and are not reachable from the API.

---

## Menu events

Both events extend `org.bukkit.event.Event` directly with their own `HandlerList`; there is no shared
base class. Both are constructed with the no-arg `Event()` superconstructor and are therefore
synchronous events — where "synchronous" on Folia means "on whichever region thread fired it", not "on
the main thread". The third API event, `HoloUiContainerPreviewAccessEvent`, is documented in
"16 - API - Previews.md".

### `HoloUiMenuOpenEvent`

```java
public HoloUiMenuOpenEvent(Player player, String menuId, String ownerPluginName)
public Player getPlayer()
public String getMenuId()
public String getOwnerPluginName()
public boolean isCancelled()
public void setCancelled(boolean cancel)
public HandlerList getHandlers()
public static HandlerList getHandlerList()
```

Fired from `MenuSessionManager#createNewSession`, immediately before the session is created, on the
player's region thread. It fires for every menu on the server: API opens, `/holoui open`, `/holoui back`
and any other internal open path.

`getOwnerPluginName()` is the opening plugin's name, or `null` for a menu HoloUi opened itself.
`getMenuId()` is the resolved definition id. A null `player` throws `NullPointerException`.

Cancelling it means the session is never created, `createNewSession` returns `false`, and for an API
open the handle terminates `FAILED` with `DENIED`.

### `HoloUiMenuClickEvent`

```java
public HoloUiMenuClickEvent(Player player, String menuId, String componentId, String ownerPluginName)
public Player getPlayer()
public String getMenuId()
public String getComponentId()
public String getOwnerPluginName()
public boolean isCancelled()
public void setCancelled(boolean cancel)
public HandlerList getHandlers()
public static HandlerList getHandlerList()
```

Fired once per hit component, before that component's actions and handler run, on the clicking player's
region thread. Cancelling it makes that component do nothing for this click — neither its JSON actions
nor the API handler run — and leaves other hit components unaffected.

### Dispatch cost and failure

`ApiEvents` checks `getHandlerList().getRegisteredListeners().length == 0` first and skips dispatch
entirely when no listener is registered, so an unused event costs nothing. If the dispatch itself
throws, HoloUi logs it and treats the event as not cancelled.

```java
package com.example.shop;

import art.arcane.holoui.api.HoloUiMenuClickEvent;
import art.arcane.holoui.api.HoloUiMenuOpenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class MenuWatcher implements Listener {
  private final Plugin plugin;

  public MenuWatcher(Plugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(ignoreCancelled = true)
  public void onMenuOpen(HoloUiMenuOpenEvent event) {
    if ("welcome".equals(event.getMenuId()) && event.getPlayer().hasPermission("example.veteran")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onMenuClick(HoloUiMenuClickEvent event) {
    if (event.getOwnerPluginName() == null) {
      return;
    }

    plugin.getLogger().info(event.getPlayer().getName() + " clicked " + event.getComponentId()
        + " on " + event.getMenuId() + " owned by " + event.getOwnerPluginName());
  }
}
```

---

## Complete example

A shop that hands out emeralds, rewrites its own stock line, and closes itself when the stock runs out.

### The menu

```java
package com.example.shop;

import art.arcane.holoui.api.HoloClick;
import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloComponent;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenu;
import art.arcane.holoui.api.HoloMenuHandle;
import art.arcane.holoui.api.HoloMenuState;
import art.arcane.holoui.api.HoloUiService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopMenu {
  private static final String MENU_ID = "exampleshop.store";
  private static final String STOCK_ID = "stock";

  private final Plugin plugin;
  private final HoloUiService holoUi;
  private final Map<UUID, HoloMenuHandle> live = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> stock = new ConcurrentHashMap<>();

  public ShopMenu(Plugin plugin, HoloUiService holoUi) {
    this.plugin = plugin;
    this.holoUi = holoUi;
  }

  public void open(Player player) {
    int remaining = stock.computeIfAbsent(player.getUniqueId(), id -> 3);

    HoloMenu menu = HoloMenu.builder()
        .id(MENU_ID)
        .offset(0.0D, 0.6D, 2.5D)
        .maxDistance(6.0D)
        .closeOnDeath(true)
        .closeOnTeleport(true)
        .component(HoloComponent.decoration("title", 0.0D, 0.85D, 0.0D,
            HoloIcon.text("<gold><bold>Village Store")))
        .component(HoloComponent.decoration(STOCK_ID, 0.0D, 0.55D, 0.0D,
            HoloIcon.text("<gray>Emeralds left: " + remaining)))
        .component(HoloComponent.button("buy", 0.0D, 0.0D, 0.0D,
            HoloIcon.item(new ItemStack(Material.EMERALD)), this::onBuy))
        .component(new HoloComponent.Button("close", 0.0D, -0.6D, 0.0D,
            HoloIcon.text("<red>[X]"), 0.2F, click -> click.handle().close()))
        .build();

    HoloMenuHandle handle = holoUi.open(plugin, player, menu);
    live.put(player.getUniqueId(), handle);
    handle.onClosed(reason -> onClosed(player.getUniqueId(), reason));
  }

  public boolean isShowing(Player player) {
    HoloMenuHandle handle = live.get(player.getUniqueId());
    return handle != null && handle.state() == HoloMenuState.OPEN;
  }

  private void onBuy(HoloClick click) {
    UUID playerId = click.player().getUniqueId();
    int remaining = stock.merge(playerId, -1, (current, delta) -> Math.max(0, current + delta));

    click.player().getInventory().addItem(new ItemStack(Material.EMERALD));
    click.handle().setText(STOCK_ID, "<gray>Emeralds left: " + remaining);

    if (remaining <= 0) {
      click.handle().close();
    }
  }

  private void onClosed(UUID playerId, HoloCloseReason reason) {
    live.remove(playerId);

    if (reason == HoloCloseReason.DENIED || reason == HoloCloseReason.OPEN_FAILED) {
      plugin.getLogger().warning("Shop menu for " + playerId + " never opened: " + reason);
    }
  }
}
```

The stock decoration carries no placeholder; its value moves because `onBuy` pushes a new string
through the handle. `onBuy` touches the player's inventory directly, which is legal because a click
handler already runs on that player's region thread.

### Triggering it

A player-issued command runs on the player's own region thread on Folia and on the main thread on
Paper, so `open` resolves inline and the returned handle is already settled.

```java
package com.example.shop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopCommand implements CommandExecutor {
  private final ShopMenu shopMenu;

  public ShopCommand(ShopMenu shopMenu) {
    this.shopMenu = shopMenu;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only a player can open the shop.");
      return true;
    }

    shopMenu.open(player);
    return true;
  }
}
```

### Wiring it up

```java
package com.example.shop;

import art.arcane.holoui.api.HoloUiService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExampleShopPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    RegisteredServiceProvider<HoloUiService> registration =
        Bukkit.getServicesManager().getRegistration(HoloUiService.class);

    if (registration == null) {
      getLogger().warning("HoloUi is not installed; the shop menu is unavailable.");
      return;
    }

    ShopMenu shopMenu = new ShopMenu(this, registration.getProvider());
    PluginCommand command = getCommand("shop");

    if (command != null) {
      command.setExecutor(new ShopCommand(shopMenu));
    }
  }
}
```

There is nothing to unregister for menus: HoloUi closes every menu you own when your plugin disables.

### Showing an admin-authored menu

```java
public void show(Player player) {
  if (!holoUi.menuIds().contains("welcome")) {
    return;
  }

  HoloMenuHandle handle = holoUi.open(plugin, player, "welcome");
  handle.onClosed(reason -> plugin.getLogger().info("welcome closed: " + reason));
}
```

The player still needs `holoui.open.welcome`. The handle reports lifecycle and can end the session; it
cannot change the menu or hear its clicks.

---

## Failure isolation and consumer lifecycle

The API isolates callback failures and owner shutdown where it can; blocking callbacks still block the player's owning thread and are reported only after they return.

| Misbehaviour                                    | What HoloUi does                                                                                        |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| A click handler throws (`Exception` or `Error`) | Counted as a fault, logged at `WARNING` naming your plugin, the component and the menu; click swallowed   |
| A click handler is slow                         | One `WARNING` per plugin per 60 s once a dispatch takes 5 ms or more. Never changes the outcome           |
| 5 accumulated faults from one plugin            | That plugin is quarantined: one `SEVERE` line, and its handlers stop being called. Its menus stay open    |
| A quarantined plugin disables                   | Quarantine, fault count and slow-warning timestamp are cleared on `PluginDisableEvent`                    |
| `Plugin#isEnabled()` throws on the owner        | Counted as a fault; the handler is not called                                                             |
| Your plugin disables with menus open            | Every session you own closes with `OWNER_DISABLED`                                                        |
| A close callback throws                         | One `WARNING` naming your plugin. Teardown continues                                                      |
| `ItemStack.clone()` throws during translation   | Propagates out of `open`; no handle is registered and nothing is left behind                              |
| Session construction or component opening throws | Partial entities, session state, placeholder state, and telemetry are cleaned up; logged at `SEVERE`; the incoming handle terminates `FAILED` with `OPEN_FAILED` |
| The open task throws for another reason         | Logged at `SEVERE` naming your plugin and the player; the handle terminates `FAILED` with `OPEN_FAILED`   |
| `setText`/`setItem`/`setIcon` with a bad id     | Returns `false`. Nothing logged, nothing staged                                                           |
| A staged update for a component the session lacks | Silently skipped by the drain; the session continues                                                    |
| An icon update throws while being applied       | Logged; that component keeps its previous icon and the session continues                                  |
| The entity scheduler refuses the task on Folia  | Logged at `WARNING`. HoloUi refuses the global fallback and fails the handle with `OPEN_FAILED`           |
| The entity scheduler retires the task           | The handle terminates with `QUIT`                                                                         |

No value moves through this API. HoloUi never takes or gives items, currency or experience on anyone's
behalf, and a menu you build in code carries no actions but your own handlers. The one thing that does
execute on your say-so is the `String` overload: an admin-authored menu runs the actions written in its
JSON file, which may include commands dispatched as the player or as console, gated only by
`holoui.open.<menuId>`. See "06 - Actions.md".

Text supplied through the API is intentionally parsed as MiniMessage markup. Before parsing, it is truncated to 4096 characters and control characters other than newlines are replaced with spaces. Ids are filtered to a fixed character set, image paths are constrained to the HoloUi images directory, and each menu's packets are sent only to its owning player.
