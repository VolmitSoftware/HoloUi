# Building HoloUi menus from another plugin

`art.arcane.holoui.api` lets another plugin describe a holographic menu in code, put it in front of one
player, change what it says while it is on screen, and close it. It is built from Bukkit types, `java.*`
types and its own types only — no VolmLib, no Adventure, no shaded types — so it links against a plain
Spigot or Paper compile classpath.

There are two ways in, and they give you very different handles:

| You want to…                                                        | Use                                    |
|---------------------------------------------------------------------|----------------------------------------|
| build the menu in code, receive clicks, change text while it is open | `open(Plugin, Player, HoloMenu)`       |
| show a menu an admin already wrote in `plugins/holoui/menus/`        | `open(Plugin, Player, String menuId)`  |

**A handle from the `String` overload cannot mutate anything and never receives a click.** It carries
lifecycle only. That is a hard limit, not an oversight — see [Opening](#opening-the-menu).

---

## Getting the service

HoloUi registers exactly one `HoloUiService` on the Bukkit `ServicesManager` in its `onEnable`, at
`ServicePriority.Normal`, and unregisters it on disable.

```java
import art.arcane.holoui.api.HoloUiService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<HoloUiService> registration =
    Bukkit.getServicesManager().getRegistration(HoloUiService.class);

if (registration == null) {
    getLogger().warning("HoloUi is not installed; the shop menu is unavailable.");
    return;
}

HoloUiService holoUi = registration.getProvider();
```

Do not hold that provider across a HoloUi reload. When HoloUi disables it builds a fresh service on the
next enable, and the reference you kept goes permanently inert: `menuIds()` returns an empty set,
`isOpen` and `close` return `false`, and every `open` hands back a handle that is already `FAILED` with
`HoloCloseReason.OPEN_FAILED`. Nothing throws and nothing corrupts — you simply stop showing menus. Look
the service up when you need it, or re-resolve it from `PluginEnableEvent`.

See [README.md](README.md) for the build-file and `plugin.yml` side of the dependency.

---

## Thread affinity

This is the part that bites. HoloUi runs on Folia, where a player's world state belongs to the region
thread that owns that player, and reading it from anywhere else is a bug that usually does not throw.

**The rule: call `open` from the thread that owns the player — the player's own region thread on Folia,
the main thread on Paper.** Everything else on the service and the handle is safe from any thread, and
each of those claims is justified below rather than assumed.

| Call                                    | Where it is legal                                | What actually runs on your thread                                     |
|-----------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------|
| `open(Plugin, Player, HoloMenu)`        | any thread; **call it from the player's owning thread** | `Plugin#getName()`, `Plugin#isEnabled()`, `Player#getUniqueId()`, and one `clone()` of every `ItemStack` in your menu |
| `open(Plugin, Player, String)`          | any thread; **call it from the player's owning thread** | the same, minus the icon translation                                  |
| `close(Player)`                         | any thread                                       | one concurrent-map lookup, then a scheduler hand-off                  |
| `isOpen(Player)`                        | any thread                                       | one concurrent-map lookup                                             |
| `menuIds()`                             | any thread                                       | a copy of a concurrent map's key set                                  |
| `handle.sessionId()` `playerId()` `menuId()` | any thread                                  | reads of final fields                                                 |
| `handle.state()`                        | any thread                                       | one atomic read                                                       |
| `handle.setText/setItem/setIcon`        | any thread                                       | validation and one concurrent-map put                                 |
| `handle.close()`                        | any thread                                       | one atomic read, then a scheduler hand-off                            |
| `handle.onClosed(Consumer)`             | any thread                                       | one atomic set — **and the callback inline** if the handle is already terminal |

Why the "any thread" answers are real, and not optimism:

- **`open` never touches the player's world state on your thread.** The only thing it reads from the
  `Player` before dispatching is the UUID. Reading the location, spawning the display entities and
  sending packets all happen inside a task on the player's entity scheduler. HoloUi does the region hop
  for you.
- **Everything else is a read of an atomic or a `ConcurrentHashMap`.** The pending-update map, the
  session map and the menu registry are all concurrent structures. `handle.state()` is one
  `AtomicReference` read.

So why does the rule still say "call `open` from the owning thread"? Two reasons, and neither is
theoretical:

1. **Off the owning thread, `open` silently defers.** On the owning thread the whole open runs inline and
   the handle you get back is already `OPEN`, `CLOSED` or `FAILED`. From anywhere else you get a
   `PENDING` handle and the menu appears a tick or more later. Code that reads `handle.state()` right
   after `open` and expects `OPEN` is correct on one thread and wrong on the other, with no error either
   way.
2. **HoloUi cannot protect the code around the call.** If you built the `HoloMenu` from the player's
   inventory, the block they are looking at, or a scoreboard on an async thread, you already read world
   state illegally before HoloUi saw anything.

If you are somewhere else and cannot move the call, hop explicitly. `Bukkit.isOwnedByCurrentRegion` and
`Entity#getScheduler()` are Paper API — present on Paper and on Folia, absent on plain Spigot:

```java
import org.bukkit.Bukkit;

if (Bukkit.isOwnedByCurrentRegion(player)) {
    holoUi.open(plugin, player, menu);
} else {
    player.getScheduler().run(plugin, task -> holoUi.open(plugin, player, menu), null);
}
```

**Click handlers run on the clicking player's region thread. Do not block them.** No I/O, no
`CompletableFuture.join`, no `callSyncMethod`, no lock held across the call. That thread ticks every
entity and every open menu in the region; a handler that hangs stalls all of them. Slow handlers are
warned about by name — see [Hostile-consumer policy](#hostile-consumer-policy).

**The close callback runs on a server thread, not one of yours.** In the ordinary cases that is the
player's region thread. On quit it is whichever thread delivered `PlayerQuitEvent`. On HoloUi shutdown it
is the thread running HoloUi's disable. And if you register `onClosed` on a handle that has already
finished, it fires inline on your thread before `onClosed` returns. Because the thread is not fixed,
treat the callback as bookkeeping only: update your own state there, and schedule anything that touches
the world or the player onto the player's entity scheduler.

---

## A menu is a value

`HoloMenu` is an immutable record. Build it with `HoloMenu.builder()`, hand it to `open`, and throw it
away or reuse it — HoloUi copies everything it needs.

```java
public record HoloMenu(String id, double offsetX, double offsetY, double offsetZ, boolean lockPosition,
                       boolean followPlayer, double maxDistance, boolean closeOnDeath,
                       boolean closeOnTeleport, List<HoloComponent> components)
```

| Builder call             | Default | Meaning                                                                                     |
|--------------------------|---------|---------------------------------------------------------------------------------------------|
| `id(String)`             | none    | Required. Identifies the session in events, logs and `%holoui_menu.id%`                       |
| `offset(double, double, double)` | `0, 0, 2` | Where the menu sits relative to the player, in blocks, as `x, y, z`                 |
| `lockPosition(boolean)`  | `false` | Freezes the player in place for as long as the menu is open                                  |
| `followPlayer(boolean)`  | `false` | Re-centres the menu on the player every time they move                                       |
| `maxDistance(double)`    | `8.0`   | How far the player may get from the menu centre before the session closes with `MOVED_OUT_OF_RANGE`. The test is `distance² ≤ maxDistance² + offsetLength²`, so the menu's own standoff offset is slack on top of it. Leaving the menu's world closes the session the same way |
| `closeOnDeath(boolean)`  | `true`  | Close on `PlayerDeathEvent`                                                                  |
| `closeOnTeleport(boolean)` | `true` | Close on any teleport                                                                       |
| `component(HoloComponent)` | —     | Adds one component; call it repeatedly                                                       |

`build()` throws `IllegalArgumentException` if you never set an id, and copies the component list, so a
list you keep mutating afterwards cannot corrupt the menu.

### The coordinate frame

Offsets are player-relative, captured from the player's yaw at the instant the menu opens: **+X is to the
player's right, +Y is up, +Z is in front of them.** The frame does not re-orient afterwards — walking in
a circle around an open menu walks around a fixed object, and the drawn icons keep the facing they were
spawned with. What does track the player is a button's hitbox: its collision plane is re-aimed at the
player's eye every tick, so a button stays clickable from any angle it is still visible from.

The menu-level `offset(...)` is used verbatim. Component offsets are additionally multiplied by the
server's `uiScale` setting, so a menu laid out at `uiScale = 1.00` spreads or tightens when an admin
changes it while the overall standoff distance stays put.

`lockPosition(true)` takes priority over `followPlayer(true)`: a frozen player never moves, so the follow
branch never runs.

### Ids are sanitised, and you must use the sanitised form

Menu ids and component ids are filtered to `A-Z a-z 0-9 _ - .`, truncated to 64 characters, and rejected
with `IllegalArgumentException` if nothing survives. `"stock count"` becomes `"stockcount"` — and
`handle.setText("stock count", …)` then returns `false`, because the component is registered under the
name that survived. Keep every id inside the allowed character set and the two can never diverge.

Two more id hazards worth knowing before you pick one:

- **Component ids must be unique within a menu.** A duplicate throws `IllegalArgumentException` from both
  the builder and the record constructor.
- **A menu id that collides with a file in `plugins/holoui/menus/` is a live hazard.** When an admin
  edits or deletes `shop.json`, HoloUi closes every session whose id matches `shop`, case-insensitively —
  including yours, with `HoloCloseReason.DEFINITION_RELOADED`. Namespace your ids: `exampleshop.store`,
  not `shop`.

---

## Components

`HoloComponent` is a sealed interface with exactly two shapes. Nothing you write can implement it.

```java
static HoloComponent decoration(String id, double x, double y, double z, HoloIcon icon)
static HoloComponent button(String id, double x, double y, double z, HoloIcon icon, HoloClickHandler handler)
```

A **decoration** draws and does nothing else — no hitbox, no clicks. A **button** has a hitbox derived
from its icon, highlights when the player looks at it, and calls your handler when they left-click.
Text and image hitboxes are centred on the visible glyph stack rather than the logical component anchor,
so aiming at what is drawn is what activates the button.

File-backed JSON buttons may displace the click plane with
`"hitbox": {"offset": [0.5, 0, 0]}` or replace its automatically derived size with
`"hitbox": {"width": 1.25, "height": 0.35}` inside the button `data`. The options may be combined.
Dimensions and right/up/forward offset values are blocks at `uiScale = 1`, scale with `uiScale`, and
rotate with the menu. The default `"anchor": "button"` keeps the offset relative to the icon, while
`"anchor": "menu"` makes the offset relative to the menu centre so the button and plane can be moved
independently. Omitted dimensions stay automatic and an omitted offset stays aligned with its anchor.
API-authored buttons always use automatic icon-derived dimensions and button alignment.

Component offsets are relative to the menu's own centre, in the same right/up/forward frame.

`HoloComponent.button(...)` gives the button the default highlight of
`HoloComponent.DEFAULT_HIGHLIGHT_MODIFIER` (`0.05F`) — how far, in blocks, the icon leans toward the
player when it is selected. Use the canonical constructor to choose your own; the value is clamped into
`0.0F .. 1.0F`, and a non-finite value becomes `0.0F`.

```java
new HoloComponent.Button("close", 0.0D, -0.6D, 0.0D, HoloIcon.text("<red>[X]"), 0.2F,
    click -> click.handle().close())
```

Both constructors reject a null icon, and `Button` rejects a null handler, with `NullPointerException`.

---

## Icons

`HoloIcon` is sealed too, with four shapes:

| Factory                                          | Renders                                                                |
|--------------------------------------------------|------------------------------------------------------------------------|
| `HoloIcon.text(String miniMessage)`              | MiniMessage markup. `\n` splits it into stacked lines                   |
| `HoloIcon.item(ItemStack stack)`                 | A floating item display                                                |
| `HoloIcon.image(String relativePath)`            | A picture from `plugins/holoui/images/`                                 |
| `HoloIcon.animatedImage(List<String> relativePaths, int tickSpeed)` | Those frames in order, advancing every `tickSpeed` ticks |

Every one of them sanitises on construction, so a hostile or careless string cannot reach a client:

- **Text** is truncated at 4096 characters, and every control character except `\n` becomes a space. A
  null string becomes `""`, which is legal and renders as nothing.
- **Item** is cloned on the way in *and* on every read, so the `ItemStack` you passed is yours to keep
  mutating. If your `ItemStack` subclass throws from `clone()`, that exception propagates out of `open`.
- **Image** paths are normalised to forward slashes and rejected with `IllegalArgumentException` if they
  are blank, longer than 256 characters, contain a control character or a `:`, start with `/`, or contain
  a `..` segment. The path is always relative to `plugins/holoui/images/` and cannot escape it.
- **AnimatedImage** sanitises every frame path the same way, copies the list, rejects an empty list, and
  clamps `tickSpeed` to at least `1`.

An image file that is missing or unreadable at render time does not fail the open. HoloUi logs it and
draws a visible "missing icon" placeholder in that slot.

---

## Opening the menu

```java
HoloMenuHandle open(Plugin owner, Player player, HoloMenu menu);
HoloMenuHandle open(Plugin owner, Player player, String menuId);
boolean close(Player player);
boolean isOpen(Player player);
Set<String> menuIds();
```

`open` never returns null and never throws for an ordinary refusal — a refusal comes back as a handle
that is already terminal, with the reason on the close callback. It throws in exactly two cases: a null
`owner`, `player`, `menu` or `menuId` raises `NullPointerException`, and an exception thrown while
translating your menu — from a hostile `ItemStack.clone()`, say — propagates to you with no handle
registered and nothing left behind.

`close(Player)` and `isOpen(Player)` tolerate a null player and answer `false`.

`owner` is your plugin instance. HoloUi uses it for three things: to name you in logs and in
`HoloUiMenuOpenEvent#getOwnerPluginName`, to skip your handlers once you are disabled, and to close your
menus when your plugin unloads.

**The two overloads are not equivalent.**

|                              | `open(…, HoloMenu)`                   | `open(…, String menuId)`                                   |
|------------------------------|----------------------------------------|-------------------------------------------------------------|
| Menu source                  | your code                              | `plugins/holoui/menus/<menuId>.json`                          |
| Permission check             | none                                   | `holoui.open.<menuId>` on the player                          |
| Click handlers               | yours, dispatched to you               | the JSON file's own actions; **nothing reaches your code**    |
| `setText` / `setItem` / `setIcon` | work                              | **always return `false`**                                     |
| `handle.menuId()`            | the sanitised `menu.id()`              | exactly the string you passed                                 |

The `String` overload registers no component ids and no handlers with the handle, so every setter refuses
and no click is ever routed to you. What you get is a lifecycle handle: `state()`, `onClosed(…)` and
`close()`. If you need to change the menu or hear about clicks, describe it in code.

The id lookup is exact. `menuIds()` gives you the loaded ids; `"Welcome"` will not find `welcome.json`,
and a miss closes the handle with `HoloCloseReason.DENIED`.

The permission check is a literal `player.hasPermission("holoui.open." + menuId)`. HoloUi's `plugin.yml`
declares the parent node `holoui.open` at `default: op` and nothing below it, so the per-menu child node
you actually need is undeclared — grant it explicitly in your permission plugin. Operators pass either
way.

**A player has at most one menu open.** Opening a second replaces the first, and the replaced handle
terminates with `HoloCloseReason.REPLACED`. That includes replacement by `/holoui open`, by another
plugin, and by you.

`close(Player)` closes whatever that player has open, whoever opened it, with
`HoloCloseReason.CLOSED_BY_OWNER`. It returns `false` if there was nothing to close, and `true` when the
teardown was accepted onto the player's entity scheduler — accepted, not already run. To close only your
own session and leave someone else's alone, use `handle.close()`.

---

## The handle lifecycle

```
open(...)  returns a handle immediately, never null
   |
   v
PENDING  --- the session is created --->  OPEN
   |                                       |
   +-------------------+-------------------+
                       |
                       |  one close reason arrives, exactly once
            +----------+----------+
            v                     v
         FAILED                CLOSED
   DENIED, OPEN_FAILED     every other reason
```

`HoloMenuState.terminal()` answers "is it over" without a switch: it is `true` for `CLOSED` and `FAILED`.

Rules HoloUi guarantees:

- A handle is **one session**. It is never reused, never reopened, and `sessionId()` is a fresh `UUID`
  per handle. A second `open` for the same player gives you a different handle.
- The state moves forward only. `PENDING → OPEN` happens once; `PENDING → CLOSED/FAILED` skips `OPEN`
  entirely when the open never lands.
- **Termination is exactly once.** The first reason to arrive wins; every later attempt is a no-op and
  never reaches your callback.
- `FAILED` means *the menu was never on screen*. It is reached by exactly two reasons: `DENIED` (the menu
  id was unknown, the player lacked the permission, or a listener cancelled `HoloUiMenuOpenEvent`) and
  `OPEN_FAILED` (the service was already shut down, the entity scheduler refused the task, or the open
  threw). Every other reason produces `CLOSED`.
- **A handle does not survive the player leaving.** `PlayerQuitEvent` closes the session and terminates
  the handle with `QUIT`, and a player who logs out before a `PENDING` open lands gets `QUIT` as well.
  There is no reattach on rejoin. Open a new menu.
- **A handle does not survive your plugin disabling.** HoloUi watches `PluginDisableEvent` and closes
  every session owned by the plugin that went down, with `OWNER_DISABLED`.

### The close callback

```java
HoloMenuHandle onClosed(Consumer<HoloCloseReason> callback);
```

- It returns the handle, so it chains onto `open`.
- There is **one callback slot**. Registering a second replaces the first; passing `null` clears it.
- It fires **exactly once**, and HoloUi drops its reference the moment it fires — a callback registered
  after the handle already closed fires immediately, inline, with the stored reason. That is what makes
  this correct even when `open` resolved synchronously:

  ```java
  HoloMenuHandle handle = holoUi.open(plugin, player, menu);
  live.put(player.getUniqueId(), handle);
  handle.onClosed(reason -> live.remove(player.getUniqueId()));
  ```

  Publish the handle first, then register. If the open already failed, the callback runs before
  `onClosed` returns and cleans up the entry you just wrote.
- If your callback throws, HoloUi logs one warning naming your plugin and continues. The exception never
  reaches the session teardown.

### Mutating a handle after it closes

Nothing throws. `setText`, `setItem` and `setIcon` return `false`, `close()` is a no-op, and any updates
you had staged but not yet applied are discarded. Checking `state().terminal()` first is an optimisation,
not a requirement.

---

## Changing what is on screen

```java
boolean setText(String componentId, String miniMessage);
boolean setItem(String componentId, ItemStack stack);
boolean setIcon(String componentId, HoloIcon icon);
```

Each stages a new icon for one component and returns whether it was accepted. `false` means one of:
the component id is not in the menu you opened, the id is null, the handle is terminal, or — for
`setItem` and `setIcon` — the value was null. `setText(id, null)` is *accepted* and blanks the component.

Staging is not drawing. HoloUi applies staged updates on the player's region thread on the next menu
tick, which runs every tick, so an update is on screen within about 50 ms. Two consequences follow:

- **Updates coalesce per component.** Fifty `setText` calls on the same component between two ticks
  produce one visual change carrying the last value. You can drive a setter from a tight loop without
  generating packet spam.
- **`true` means accepted, not rendered.** If the session ends in the same tick, the update is dropped.

Changing text in place keeps the same display entities as long as the icon is already text and the new
markup has the same number of `\n`-separated lines; only the lines that actually differ are re-sent.
Changing the line count, or changing the *kind* of icon (text to item, item to image), tears the old icon
down and spawns a new one. Either way a button's hitbox is re-derived from whatever it now draws, so a
button that grows stays clickable across its new extent.

### Placeholders resolve once, at open, and then freeze

This is a real limitation and you should know it before you ship.

Any PlaceholderAPI placeholder inside a text icon — `%vault_eco_balance%`, `%player_name%`,
`%holoui_menu.id%` — is expanded when that icon is built, which is when the menu opens. The result is
baked into the display entity. It does **not** re-expand on a timer, on a tick, or when the underlying
value changes. A menu showing `%vault_eco_balance%` shows the balance the player had when they opened it,
for the whole session.

What does update live:

- `handle.setText(id, …)` — the replacement string is expanded again at the moment you set it, so a
  handle setter is the supported way to show a value that moves. If you want a live balance, read it
  yourself and push it.
- `handle.setItem(id, …)` and `handle.setIcon(id, …)` — a whole new icon, built fresh.

So the pattern for live content is: put a static placeholder-free string in the menu definition, and
drive the component from your own scheduled task or from your click handler.

When PlaceholderAPI is not installed, no expansion happens at all and the literal `%…%` text is what the
player sees. Never put a placeholder in a menu you also expect to work without PlaceholderAPI.

---

## Clicks

A left-click while looking at a button dispatches to your handler.

```java
@FunctionalInterface
public interface HoloClickHandler {
    void onClick(HoloClick click);
}

public record HoloClick(Player player, String menuId, String componentId, HoloMenuHandle handle)
```

`handle` is the same instance `open` returned, so a handler can mutate or close the menu it is in without
any bookkeeping of your own.

Facts about dispatch:

- It runs on the **clicking player's region thread**, from a `MONITOR`-priority `PlayerInteractEvent`
  listener. Reading and mutating that player's inventory, experience and location is legal there.
- The interact event is cancelled before your handler runs, so the click never places or breaks a block.
  HoloUi cancels at `MONITOR`, which is the last priority, so plugins listening earlier have already seen
  the event uncancelled — cancelling here stops the vanilla action, not other listeners.
- **More than one component can be hit by one click.** Overlapping hitboxes all fire, in menu declaration
  order. Space your buttons if that is not what you want.
- A handler is only invoked while the handle is live. A click that arrives in the same tick the session
  closes is dropped.
- `HoloUiMenuClickEvent` fires first, per component. A listener that cancels it skips that component's
  handler entirely.

---

## Worked example

A shop that hands out three emeralds and closes itself when the stock runs out. It has a decoration for
the title, a decoration it rewrites live, and a button with a handler.

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

The stock decoration carries no placeholder. Its value moves because `onBuy` pushes a new string through
the handle, which is the only mechanism that updates live text.

`onBuy` touches the player's inventory directly, and that is legal because a click handler already runs
on that player's region thread.

### Triggering it

A player-issued command is handled on the player's own region thread on Folia and on the main thread on
Paper, so `open` resolves inline and the handle you receive is already settled.

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

There is nothing to unregister. HoloUi closes every menu you own when your plugin disables.

---

## The minimum: showing a menu somebody else wrote

If all you want is to put an admin-authored menu in front of a player at the right moment, you do not
need `HoloMenu` at all.

```java
package com.example.shop;

import art.arcane.holoui.api.HoloMenuHandle;
import art.arcane.holoui.api.HoloUiService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public final class Welcome {
  private final Plugin plugin;
  private final HoloUiService holoUi;

  public Welcome(Plugin plugin, HoloUiService holoUi) {
    this.plugin = plugin;
    this.holoUi = holoUi;
  }

  public void show(Player player) {
    Set<String> available = holoUi.menuIds();

    if (!available.contains("welcome")) {
      return;
    }

    HoloMenuHandle handle = holoUi.open(plugin, player, "welcome");
    handle.onClosed(reason -> plugin.getLogger().info("welcome closed: " + reason));
  }
}
```

The player still needs `holoui.open.welcome`. The handle tells you when the session ends and lets you end
it; it will not let you change the menu or hear its clicks.

---

## Observing with events

Two Bukkit events let a third plugin watch or veto every menu on the server, including menus HoloUi opens
from its own command. Each has its own `HandlerList` and there is no shared base class.

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

| Event                    | Fires                                            | Cancelling it                                                    |
|--------------------------|--------------------------------------------------|-------------------------------------------------------------------|
| `HoloUiMenuOpenEvent`    | immediately before the session is created, on the player's region thread | The menu never opens; the owner's handle terminates `FAILED` with `DENIED` |
| `HoloUiMenuClickEvent`   | once per hit component, before that component's actions and handler run, on the clicking player's region thread | That component does nothing this click; other hit components are unaffected |

`getOwnerPluginName()` is the opening plugin's name, or `null` for a menu HoloUi opened itself from
`/holoui open`, `/holoui back` or a JSON definition.

Neither event is dispatched at all when no listener is registered, so an unused event costs nothing.
If dispatch itself throws, HoloUi logs it and treats the event as **not cancelled**.

---

## Hostile-consumer policy

HoloUi assumes a consumer will throw, return null, block, or vanish mid-session.

| Misbehaviour                                     | What HoloUi does                                                                                   |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| A click handler throws (`Exception` or `Error`)  | Counted as a fault, logged at `WARNING` naming your plugin and the component, click swallowed         |
| A click handler is slow                          | One `WARNING` per plugin per 60 seconds once a call takes 5 ms or more. Never changes the outcome     |
| **5 faults from one plugin**                     | That plugin is **quarantined**: one `SEVERE` log line, and its handlers stop being called. Its menus stay on screen and still close normally |
| A quarantined plugin disables and re-enables     | Quarantine and fault count are cleared; handlers work again                                           |
| `Plugin#isEnabled()` throws on the owner         | Counted as a fault, the handler is not called                                                         |
| Your plugin disables with menus open             | Every session you own closes with `OWNER_DISABLED`                                                    |
| A close callback throws                          | One `WARNING` naming your plugin. Teardown continues                                                  |
| `ItemStack.clone()` throws during translation    | The exception propagates out of `open`; no handle is registered and nothing is left behind            |
| The open task throws for any reason              | Logged at `SEVERE` naming your plugin and the player; the handle terminates `FAILED` with `OPEN_FAILED` |
| `setText`/`setItem`/`setIcon` with a bad id      | Returns `false`. Nothing is logged and nothing is staged                                              |
| An icon update throws while being applied        | Logged; that one component keeps its previous icon and the session continues                          |
| The entity scheduler refuses the task on Folia   | Logged as a warning. HoloUi refuses the unsafe global fallback and fails the handle with `OPEN_FAILED` |

No value moves through this API. HoloUi never takes or gives items, currency or experience on anyone's
behalf — everything the shop example hands out, the shop example hands out itself, on a thread where
doing so is legal. A menu you built in code carries no actions but your own handlers. The one thing that
does execute on your say-so is the `String` overload: an admin-authored menu runs the actions written in
its JSON file, and those may include commands dispatched as the player or as console. Opening one on a
player's behalf runs whatever the admin wired to it, gated only by `holoui.open.<menuId>`.

Text you supply is truncated and stripped of control characters before it reaches a client, and ids are
filtered to a fixed character set, so a menu cannot inject markup or a path into somebody else's screen.

---

## Configuration

`plugins/holoui/settings.json`. These are the keys that change how an API menu looks or behaves:

| Key             | Default | Effect on your menus                                                                       |
|-----------------|---------|---------------------------------------------------------------------------------------------|
| `uiScale`       | `1.00`  | Multiplies component offsets and icon size. Clamped to `0.25 .. 4.00`. Does not scale the menu-level `offset(...)` |
| `debugHitbox`   | `false` | Draws button hitboxes as particles                                                          |
| `debugPosition` | `false` | Draws the menu centre and each component anchor as particles                                |

The remaining keys — `previewEnabled`, `previewLookDistance`, `previewScale`, `builderIp` and
`builderPort` — govern HoloUi's container-preview HUD and its web builder, and have no effect on API
menus. Container contents require the `holoui.preview` permission (operator-only by default). A player
who lacks that permission, cannot physically open the container, cannot satisfy its held-item lock, or
is denied by an access provider sees one lock marker and nothing from the inventory. HoloUi performs
all access checks before it builds any preview document or reads an inventory slot.

What each preview draws is data, not code: JSON documents in `plugins/holoui/previews/`, hot reloaded,
managed with `/holoui previews`. Another plugin can publish extra variables into them through
`PreviewStateProvider` without touching the menu API. See [previews.md](previews.md).

WorldGuard is detected and queried at runtime when present; it is not a required or optional plugin
dependency in HoloUi's metadata. Region membership, bypass and `chest-access` decisions apply to block
containers and inventory vehicles. Other protection plugins can cancel
`HoloUiContainerPreviewAccessEvent`, inspecting either `getBlock()` or `getEntity()` for the target.
That event is a preview preflight only. HoloUi does not synthesize player-interact or inventory-open
events while the player is looking at a container.

Related paths, all relative to `plugins/holoui/`:

- `menus/<id>.json` — the definitions reachable through `open(…, String)` and listed by `menuIds()`
- `images/` — the root that every `HoloIcon.image` and `HoloIcon.animatedImage` path resolves against
- `previews/<name>.json` — the container-preview documents; nothing here affects API menus

All three folders are watched. Editing a definition closes matching sessions with
`DEFINITION_RELOADED`; adding or removing an image respawns every open icon; editing a preview document
recompiles it and closes open previews.

---

## Switching over the enums

`HoloCloseReason` and `HoloMenuState` may gain constants in a future release. A `switch` **expression**
over them is exhaustive, so it stops compiling — and throws `IncompatibleClassChangeError` on an
already-compiled jar — the moment one is added.

**Always write a `default` arm** in third-party code:

```java
String message = switch (reason) {
    case DENIED, OPEN_FAILED -> "the menu never reached the player";
    case QUIT, DEATH, RESPAWN, TELEPORT, MOVED_OUT_OF_RANGE -> "the player moved on";
    default -> "closed";
};
```

`HoloMenuState.terminal()` answers the only question most consumers actually have, without a switch.

### `HoloMenuState`

| Constant  | Meaning                                                                 |
|-----------|--------------------------------------------------------------------------|
| `PENDING` | Accepted, not yet on screen. Setters work; the updates apply when it opens |
| `OPEN`    | On screen                                                                |
| `CLOSED`  | It was on screen and is not any more, or it was cancelled before opening for a non-failure reason |
| `FAILED`  | It never reached the player. Only `DENIED` and `OPEN_FAILED` produce this |

### `HoloCloseReason`

| Constant              | Resulting state | Cause                                                                             |
|-----------------------|-----------------|------------------------------------------------------------------------------------|
| `CLOSED_BY_OWNER`     | `CLOSED`        | `handle.close()` or `HoloUiService.close(Player)`                                    |
| `CLOSED_BY_COMMAND`   | `CLOSED`        | `/holoui close`                                                                      |
| `REPLACED`            | `CLOSED`        | Another menu opened for the same player                                              |
| `MOVED_OUT_OF_RANGE`  | `CLOSED`        | The player walked further than `maxDistance` from the menu centre                    |
| `DEATH`               | `CLOSED`        | The player died and `closeOnDeath` was set                                           |
| `RESPAWN`             | `CLOSED`        | The respawn point was out of range. Only reachable with `closeOnDeath(false)`        |
| `TELEPORT`            | `CLOSED`        | The player teleported and `closeOnTeleport` was set, or landed out of range          |
| `QUIT`                | `CLOSED`        | The player logged out, including before a `PENDING` open landed                      |
| `DEFINITION_RELOADED` | `CLOSED`        | A JSON menu whose id matches this session's, case-insensitively, was edited or deleted |
| `OWNER_DISABLED`      | `CLOSED`        | The plugin that opened the menu was disabled                                         |
| `DENIED`              | `FAILED`        | Unknown menu id, missing `holoui.open.<id>`, or a cancelled `HoloUiMenuOpenEvent`     |
| `OPEN_FAILED`         | `FAILED`        | HoloUi was shutting down, the scheduler refused the task, or the open threw           |
| `HOLOUI_SHUTDOWN`     | `CLOSED`        | HoloUi disabled or reloaded while the menu was open                                  |
