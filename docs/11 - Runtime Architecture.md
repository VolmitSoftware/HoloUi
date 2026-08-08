# Runtime Architecture

This document describes how HoloUi boots, how it keeps per-player state, how menus are rendered and positioned, how clicks resolve, and how everything is torn down. It is written for contributors and integrators working inside the plugin; menu authoring is covered by `03 - Menu File Format.md` and the public API by `13 - API - Getting Started.md`.

## 1. Boot lifecycle

### 1.1 Constructor

`HoloUI()` runs before `onLoad`. It logs `Loading Dependencies...`, runs `new SpigotApplicationBuilder(this).build()` — slimjar resolving and downloading every library declared with `slim(...)` in `build.gradle` — then logs `Dependencies loaded!`.

Because slimjar injects those libraries during construction, no class touched while the main class is linked may reference a slimjar-provided type. This is why all bStats usage lives in `HoloUiMetrics` and never in `HoloUI` itself. The same constraint applies to any new code added to `HoloUI`: PacketEvents, Adventure, commons, and bStats types must stay behind a separate class.

### 1.2 onLoad

1. `INSTANCE = this`.
2. `SpigotPacketEventsBuilder.clearBuildCache()`.
3. Build a `PacketEventsSettings` with `checkForUpdates(false)`.
4. `PacketEvents.setAPI(SpigotPacketEventsBuilder.buildNoCache(this, settings))`.
5. `PacketEvents.getAPI().load()`.

`plugin.yml` declares no `load:` block, so Bukkit uses the default `POSTWORLD` and `onEnable` runs after worlds are loaded.

### 1.3 onEnable

| Step | Action | Effect |
| --- | --- | --- |
| 1 | `ImageIO.scanForPlugins()` | Registers image readers for the imaging-backed icons |
| 2 | `prewarmPacketEventsUsers()` | For each online player with a non-null channel and no existing `User`, installs a `User` with `ConnectionState.PLAY` and the server version as `ClientVersion` |
| 3 | `PacketEvents.getAPI().init()` | On `NullPointerException`, `isPacketEventsUserBindFailure` walks the cause chain for a frame whose class name ends `SpigotChannelInjector` and whose method is `updatePlayer`. On a match, prewarm runs again and `init()` is retried once; otherwise the NPE is rethrown |
| 4 | `TextUtils.splash(this)` | Console banner |
| 5 | `new HudSlotService(this)`, `new HudBossBarLane()` | VolmLib HUD slot arbitration and boss bar lanes |
| 6 | `new HoloLocalization(getDataFolder(), getLogger())` | Reads `plugins/holoui/language.yml` |
| 7 | `new ConfigManager(getDataFolder())` | Creates `images/`, `menus/`, `settings.json`, and the 5-tick and 20-tick reload tasks |
| 8 | `new PreviewDocumentRegistry(getDataFolder())`, then `startWatching()` | Creates `previews/`, extracts shipped documents that are missing, compiles every `*.json`, arms a 5-tick folder watcher |
| 9 | `new ItemProviderRegistry(this)`, then `activateAll()` | Registers plugin enable/disable listeners; activates providers only when `customItems` is true |
| 10 | `new ContainerProtectionService(this)`, then `activate()` | Container access checks for previews |
| 11 | `new MenuSessionManager()` | Starts the two 1-tick tasks, registers the Bukkit listeners, seeds the debug tasks from the current settings |
| 12 | `PreviewScaleService.init(this)` | Loads `plugins/holoui/preview-scales.json`; registers `PlayerToggleSneakEvent`, `PlayerItemHeldEvent`, `PlayerQuitEvent` listeners |
| 13 | `new HoloUiCommandService(this)`, then `register()` | Binds executor and tab completer to `holoui` and builds the Director runtime engine. If `getCommand("holoui")` returns null, logs `Failed to find command 'holoui'` and returns without registering |
| 14 | `HoloUiMetrics.start(this, BSTATS_PLUGIN_ID)` | Guarded by `if (BSTATS_PLUGIN_ID > 0)`, a compile-time constant, so it always runs |
| 15 | `new HoloUiIntegrationService()`, then `register()` | Registers `IntegrationServiceContract` at `ServicePriority.Normal` |
| 16 | `new HoloUiServiceImpl(this)`, then `register()` | Public API service |
| 17 | `new PlaceholderRegistration(getLogger())`; when `PlaceholderRegistration.isPlaceholderApiEnabled()`, `HoloUiPlaceholderInstaller.install(...)` | PAPI expansion installed only when PlaceholderAPI is enabled |

One ordering constraint is recorded in source: `PreviewDocumentRegistry` is fully published before `MenuSessionManager` exists, because the session manager's raycast tick queries the registry.

### 1.4 onDisable, onPreUnload, drain

`HoloUI implements ReloadAware`. `onDisable()` calls `drain()` directly. `onPreUnload(PreUnloadReason)` logs `BileTools pre-unload hook fired (%s)...` and calls `drain()`, so a BileTools-driven reload tears down before the class loader is dropped.

`drain()` is guarded by `alreadyDrained.compareAndSet(false, true)` and is a no-op on every call after the first, so the disable path and the pre-unload path can both fire without double teardown. The flag is never reset; a plugin instance drains exactly once. Every step is null-guarded:

1. `placeholderRegistration.unregister()`
2. `apiService.unregister()`
3. `integrationService.unregister()` (also calls `HoloUiTelemetry.clear()`)
4. `containerProtection.shutdown()`
5. `configManager.shutdown()` — writes `settings.json`
6. `sessionManager.destroyAll()`
7. `itemProviders.shutdown()`
8. `PreviewScaleService.shutdown()` — persists `preview-scales.json`, releases HUD claims
9. `hudLanes.shutdown()`
10. `hudSlots.shutdown()`
11. `PacketEvents.getAPI().terminate()` when the API is non-null
12. `SpigotPacketEventsBuilder.clearBuildCache()`
13. `metrics.shutdown()`
14. `SchedulerUtils.cancelPluginTasks(this)`
15. `INSTANCE = null` when `INSTANCE == this`

`destroyAll()` synchronously closes every menu and preview holder at step 6, while PacketEvents is still active, so teardown can send the required destroy packets. Only after the holders are empty does step 11 terminate PacketEvents; repeating tasks are cancelled at step 14. `PacketUtils` has no null check on `PacketEvents.getAPI()`, so this order is required.

## 2. Session architecture

```
MenuSessionManager
  holders: ConcurrentHashMap<Player, SessionHolder>
  SessionHolder
    session: MenuSession?          (guarded by sessionLock)
      components: List<MenuComponent>
        currentIcon: MenuIcon      -> List<UUID> registered in DisplayEntityManager
    preview: ContainerPreview?     (guarded by previewLock)
DisplayEntityManager (static)
  displayEntities:  Map<UUID, DisplayEntity>
  playerVisibility: Map<UUID, Player>
```

`holders` is keyed by the `Player` instance, not by UUID, so a holder lives for exactly one login. Each holder carries at most one `MenuSession` and at most one `ContainerPreview`, under two separate locks; a menu and a container preview can be open at the same time and do not interact. `session` and `lastSession` are `volatile`; `hasSession()` and `snapshotClick()` read them without locking, and every mutation path takes the lock.

There is no menu stack, no nesting, and no per-plugin partitioning: an API menu and a config menu compete for the same single slot, and opening a second menu replaces the first.

### 2.1 Open path

| Caller | Path |
| --- | --- |
| `/holoui open <id>` or `menu=<id>` | `HoloUiCommandService` rewrites bare ids to `menu=`; `HoloCommand.openMenu` checks `holoui.command.open` and `holoui.open.<id>`, then `createNewSession(player, definition)` |
| `/holoui back` | `MenuSessionManager.openLastSession` |
| Third-party API | `HoloUiServiceImpl` -> `HoloUiBackend.openSession` -> `createNewSession(player, definition, handle)` |

`MenuSessionManager.createNewSession(Player, MenuDefinitionData, ApiMenuHandle)`:

1. `ApiEvents.fireOpen` fires `HoloUiMenuOpenEvent`. The event object is constructed only when the handler list is non-empty. A cancelled event returns `false`; on the API path the caller terminates the handle with `DENIED`.
2. `holders.computeIfAbsent(player, SessionHolder::new)`, then `SessionHolder.openSession(data, handle)`.
3. `SessionHolder.openSessionLocked` runs under `sessionLock`:
   - When `!player.isOnline()`, no session is created and the incoming handle is terminated with `QUIT`.
   - `detachSession(true)` tears down any existing session, records its id as `lastSession`, publishes `null` into the open-menu snapshot store, and returns its handle. That handle is terminated with `REPLACED` after the lock is released.
   - `openMenus.publish(playerId, data.getId())` — published *before* the session is constructed, so a component that resolves a placeholder in its constructor (every toggle, through its `condition` and its two eagerly built icons) already reads the incoming menu id.
   - `new MenuSession(data, player, handle)` computes the anchor and constructs components. Every built-in component factory returns a component; the loop skips a `null` returned by any future implementation. A duplicated id keeps the first component and logs a warning; later duplicates are not added to the render, tick, or click lists.
   - `MenuSession.open()`.
   - `handle.markOpen()` when the session came from the API.
   - `HoloUiTelemetry.incrementMenusOpen()` only after construction and open both succeed.

If construction or `open()` throws, the holder closes the partial session, which removes any display entities already spawned, clears the session reference and placeholder snapshot, and does not increment the open-menu counter. The detached previous handle still terminates with `REPLACED`; an incoming API handle terminates with `OPEN_FAILED`. The failure is then rethrown for the command or API caller to log, and the failed session is not retained.

`MenuSession.open()` captures `initialY = -player.getEyeLocation().getYaw()` once and then calls `component.open()` for every component. `MenuComponent.open()`:

1. `adjustRotation()` rotates the component location about the player's eye by `initialY`. The icon is still null here, so no teleport packet is produced.
2. `createIcon()`. `MenuIcon.createIcon` dispatches on the icon data type; a `MenuIconException` is logged and falls back to the missing-icon `TextImageMenuIcon`. If that fallback also throws, an `IllegalStateException` carrying both failures aborts the open and reaches the transactional rollback path.
3. `icon.spawn()` — creates the display entities and sends spawn and metadata packets to the session player.
4. `onOpen()`. For `ClickableComponent` this is `refreshPlane()`.
5. `open = true`.

The `PlayerSnapshotStore<String> openMenus` written in this path is what `HoloUiPlaceholderExpansion` answers `menu.open` and `menu.id` from (see `07 - Expressions & Placeholders.md`). The holder captures the player UUID once at construction and always publishes under that captured id.

### 2.2 Menu history

`lastSession` is recorded only when a session is detached with `history = true`, which happens exclusively on the replace path. `/holoui close` calls `destroySession(player, false)`, which sets `lastSession = null`. `/holoui back` therefore reopens a menu that was displaced by another open and reports "no previous menu" after an explicit close. Holder removal on quit means history does not survive a relog.

`openLastSession` re-resolves the id through `ConfigManager` and goes through `createNewSession`, so `HoloUiMenuOpenEvent` fires again and a deleted definition simply fails.

Holder removal happens on quit and on tick-time dispatch failure only. A holder with no session and no preview stays in the map until the player disconnects.

## 3. Tick loop and scheduling

Two independent 1-tick tasks are started in the `MenuSessionManager` constructor, plus two optional 2-tick debug tasks.

| Task | Period | Scope |
| --- | --- | --- |
| Session/preview tick | 1 tick | iterates `holders` |
| Container-preview raycast (`listenToInventoryPreview`) | 1 tick | iterates `Bukkit.getOnlinePlayers()`, not `holders` |
| Hitbox debug (`debugHitbox`) | 2 ticks | draws hitbox outlines for every open clickable component |
| Position debug (`debugPosition`) | 2 ticks | draws the menu center and each component location |

`SchedulerUtils.scheduleSyncTask(plugin, 1L, task, false)` is a self-rescheduling loop, not `runTaskTimer`: it runs the body and then schedules the next delay. With `delayStart = false` the first iteration runs on the next tick. Cancellation is cooperative — the flag is checked at the top of each iteration — and an uncaught throwable from a body permanently stops that chain.

Per iteration of the session tick:

1. `System.nanoTime()` is sampled, then every holder is visited.
2. Each holder's work is wrapped in `SchedulerUtils.runEntity(plugin, player, tickTask)`. A `false` return (plugin disabled, entity retired, Folia scheduling refused) removes the holder and closes it with `QUIT` on the dispatch thread.
3. `tickTask`: when the player is offline, close with `QUIT` and remove the holder; otherwise `holder.tick()`, and remove the holder when it returns `true`.
4. `HoloUiTelemetry.addTickNanos` records the elapsed time of the dispatch loop only.

`SessionHolder.tick()` re-checks `player.isOnline()` and, when offline, closes the session with `QUIT`, closes the preview, and returns `true` so the manager drops the holder. Otherwise it runs `session.drainApiUpdates()` and `component.tick()` for every component under `sessionLock`, then `preview.tick()` under `previewLock`; a `false` return or a thrown exception closes the preview, and the exception is logged rather than propagated.

`MenuSession.drainApiUpdates()` does work only when the handle reports `dirty()`. Each staged icon is applied via `MenuComponent.applyIcon`: a text-only update to a `TextMenuIcon` with an unchanged line count mutates the existing text displays in place; anything else builds a replacement icon and swaps it, destroying and recreating that component's display entities. Per-component exceptions are caught and logged.

`MenuComponent.tick()` is a no-op while `!open`. It calls `onTick()` and then `currentIcon.tick()`, which is where the animated text-image icon advances frames.

`refreshVisuals()` — triggered by a `uiScale` or `previewScale` change, or by an image asset hot reload — closes and reopens every open component, producing brand-new display entities.

### 3.1 Scheduler per operation

| Operation | Scheduler |
| --- | --- |
| Session/preview dispatch loop, preview raycast loop, debug loops | `SchedulerUtils.scheduleSyncTask` -> `FoliaScheduler.runGlobal`, i.e. the global region scheduler on both Paper and Folia; the plain Bukkit sync scheduler only where the global region scheduler is absent |
| Per-player tick, holder close, `refreshVisuals`, `destroyAllType`, debug draw, preview management | `SchedulerUtils.runEntity(plugin, player, task)`. Reload and refresh work is dropped when entity scheduling fails; it is not run directly on the wrong thread |
| Container preview built from a block | `FoliaScheduler.runRegion(plugin, block.getLocation(), task)`; on non-Folia only, an inline run when that returns false |
| Container preview built from an entity | `SchedulerUtils.runEntity(plugin, entity, task)` |
| Preview open (`openPreviewIfCurrent`) | `runEntity` on the viewing player; runs inline when scheduling fails |
| Ender-chest preview build | `runEntity` on the viewing player, because the inventory belongs to the viewer rather than the block |
| Console-source menu command actions | `SchedulerUtils.runGlobal` |
| API open/close (`HoloUiBackend.schedule`) | `FoliaScheduler.runEntity(plugin, player, task, 0L, retired)`; on Folia a failure is logged and refused, elsewhere it falls back to `runGlobal` |
| Bukkit event handlers | The event thread — on Folia, the region thread owning the player |

`SchedulerUtils.runEntity` runs the task inline when the caller already owns the entity's region. On Paper, region ownership degrades to "is primary thread", so the entire per-player tick executes synchronously inside the dispatch loop. On Folia the global-region dispatch thread does not own player entities, so each holder's tick is handed to that player's entity scheduler and runs slightly later; the deferred work is consequently not included in the `addTickNanos` measurement.

A `false` return from the 3-argument `runEntity` cannot distinguish a retired entity from any other failure. The API path uses the 5-argument `FoliaScheduler.runEntity` with a `retired` callback specifically so a handle can be terminated with `QUIT` when the player entity is gone.

`FoliaScheduler.runRegion` never runs inline, even when the caller already owns the region, and on Folia a `false` return means the work is dropped — which is why the block-preview fallback is guarded with `&& !FoliaScheduler.isFolia(...)`.

### 3.2 Region safety

- Normal runtime mutations of a player's menu state run on the thread owning that player. If entity scheduling fails, the tick, definition-reload, visual-refresh, and preview-close paths do not execute that work directly as a fallback. Shutdown is the exception: `destroyAll()` closes all holders synchronously before PacketEvents termination so their packet entities are removed during teardown.
- Container previews read block inventories on the block's region thread and then hop back to the player's thread to open, re-verifying with `isStillLookingAt` that the target has not changed; the two hops are not atomic, so the look check is deliberately re-run.
- API handle termination always happens outside `sessionLock` and `previewLock`, so `onClosed` callbacks never run under a lock.
- `holders` is a `ConcurrentHashMap` and removal during `forEach` is safe. The tick loop removes with the two-argument `remove(key, value)` form so a concurrently replaced holder is not dropped; the `PlayerQuitEvent` handler uses the one-argument form.
- `openMenus` is the only structure read from foreign threads, because PlaceholderAPI resolves on whatever thread calls it. It is a `ConcurrentHashMap` holding immutable strings.
- `DisplayEntityManager`'s maps are `ConcurrentHashMap`s but are global rather than per-region; operations on a given entity are naturally serialized because each entity belongs to exactly one player.
- Telemetry counters are atomics and safe from any thread.

## 4. Rendering

### 4.1 Packet-only entities

Nothing in the menu or preview runtime calls `World.spawn`, `World.spawnEntity`, or any other Bukkit entity factory. `DisplayEntity` is a data holder for a synthetic entity id and UUID; all rendering is PacketEvents wrappers built in `DisplayEntity` and sent through `PacketUtils`.

Entity ids come from `DisplayEntity.Builder.NEXT_ID`, an `AtomicInteger` starting at `Integer.MIN_VALUE` and counting up. When the next value would reach `0`, it logs two `SEVERE` lines (`Entity IDs overflow` / `Please restart your server!`) and resets to `Integer.MIN_VALUE`. Negative ids never collide with server-assigned ids.

`DisplayEntityManager.unsupportedVersion()` gates every mutator: when PacketEvents reports a server version older than `1.19.4`, all spawn/move/update calls become no-ops and a single `WARNING` is logged (`HoloUi display-entity renderer requires Minecraft 1.19.4 or newer.`).

Only two entity types are used: `EntityTypes.TEXT_DISPLAY` and `EntityTypes.ITEM_DISPLAY`. There are no `Interaction` entities, no armor stands, and no block displays anywhere in the runtime.

`DisplayEntity.dataPacket()` writes the shared indices and then type-specific ones:

| Index range | Contents |
| --- | --- |
| 0, 5 | entity flags, no-gravity (default `true`) |
| 8-22 | interpolation delay/duration, teleport duration, translation, scale, left/right rotation quaternions, billboard, brightness (`-1`), view range (`1`), shadow radius/strength (`0`), width, height, glow color override (`-1`) |
| 23-27 (TEXT_DISPLAY) | Adventure component, line width, background color, text opacity (`0xFF`), text flags |
| 23-24 (ITEM_DISPLAY) | item stack (converted with `SpigotConversionUtil.fromBukkitItemStack`), item display type |

`lineWidth` defaults to `200` on the record, but every builder sets `2000`.

`spawn()` returns `WrapperPlayServerSpawnEntity` plus `WrapperPlayServerEntityMetadata`; `remove()` returns `WrapperPlayServerDestroyEntities`; `goTo`, `move`, and `rotate` all return `WrapperPlayServerEntityTeleport` with `onGround = true` and mutate the cached location and rotation.

### 4.2 Single-viewer visibility

`DisplayEntityManager` holds two static maps:

- `displayEntities: Map<UUID, DisplayEntity>` — every created entity, keyed by a fresh random UUID returned from `add()`. That key is not the entity's own `uuid` field.
- `playerVisibility: Map<UUID, Player>` — which player currently sees each entity.

`playerVisibility` holds one player per entity, so a `DisplayEntity` is visible to at most one viewer; a `spawn` for a second player would overwrite the record and orphan the first viewer's copy. Every session and preview builds its own entities, so the invariant holds by construction.

Every mutator except `delete` and `location` requires a live `playerVisibility` entry, so an entity that was added but never spawned cannot be moved or updated. `changeName` and `changeTextBackground` require `TEXT_DISPLAY`; `changeItem` requires `ITEM_DISPLAY`; mismatches are silent no-ops.

Visibility scoping is absolute: packets go only to `session.getPlayer()` via `MenuIcon`, or to the preview's owner. There is no broadcast path. Other players never receive the spawn packet, cannot see the menu, and cannot interact with it.

### 4.3 DisplayEntityManager lifecycle

- Normal close: `MenuIcon.remove()` calls `DisplayEntityManager.delete(uuid, player)` for each entity. That removes both map entries and sends a destroy packet to the recorded viewer, or to the passed fallback player when the visibility record is already gone.
- `MenuSessionManager.destroyAll()` — reached from plugin disable and from BileTools `onPreUnload`, both through the idempotent `HoloUI.drain()` — closes every holder with `HOLOUI_SHUTDOWN`, then clears `holders` and `openMenus`.
- No boot-time orphan sweep exists and none is needed for server-side state: no real entity is ever created, so nothing persists in a world or a chunk across a restart.
- Client-side ghosts are possible when the process dies without sending destroy packets (`SIGKILL`, crash). They clear on relog or dimension change.
- There is no bulk registry clear during drain. Session and preview teardown must retain each registered display until `delete` sends its destroy packet; dropping the maps first would leave client-side entities visible during a hot reload.

## 5. Positioning

### 5.1 Anchor

`MenuSession` computes its anchor once, at open time:

```java
this.offset = data.getOffset().clone().multiply(new Vector(-1, 1, 1));
this.offsetDistance = offset.lengthSquared();
this.centerPoint = p.getLocation().clone().add(offset);
```

The anchor derives from the player's feet location (`getLocation()`), not the eye location. The definition's X offset is negated so a positive configured X reads as "right" from the player's point of view.

`MenuComponent` applies the same negation plus `uiScale`:

```java
double scale = HuiSettings.uiScale();
this.offset = data.offset().clone().multiply(new Vector(-scale, scale, scale));
this.location = session.getCenterPoint().clone().add(offset);
this.location.setYaw(0F);
this.location.setPitch(0F);
```

Component locations always carry zero yaw and pitch. `uiScale` is clamped to `[0.25, 4.0]`; see `01 - Installation & Configuration.md`.

### 5.2 Frozen rotation, live pivot

`initialY = -player.getEyeLocation().getYaw()` is captured in `MenuSession.open()` and is the only rotation the layout ever uses. `MenuComponent.adjustRotation()` calls `MathHelper.rotateAroundPoint(location, session.getPlayer().getEyeLocation(), 0, initialY)`, which translates the component location into eye-relative space, rotates it around Y by `initialY` degrees, and translates back.

The angle is frozen at open time; the pivot is the player's live eye location. A menu that is moved by follow, respawn, or teleport therefore re-anchors around wherever the eye is at that moment while keeping the original facing.

`MenuSession.getCenterInitialYAdjusted()` applies the same rotation to a clone of `centerPoint` using the live eye location. It is used for `MENU`-anchored hitbox origins and for the position debug particle.

### 5.3 Freeze, follow, static

| `lockPosition` | `followPlayer` | Behavior |
| --- | --- | --- |
| true | any | The player cannot translate: the move handler copies `from`'s coordinates into `to` and zeroes velocity. The menu never moves. `lockPosition` wins and the follow branch is unreachable |
| false | true | Every accepted move calls `session.move(to.clone())`: `centerPoint = to + offset`, and every component moves to `centerPoint + componentOffset` and is re-rotated about the current eye |
| false | false | The menu stays where it was opened. The player may walk away until `maxDistance` is exceeded, which closes it with `MOVED_OUT_OF_RANGE` |

`lockPosition` freezes the player, not the menu. The menu is stationary in both the frozen and the plain static case. Yaw and pitch are left untouched while frozen, so the player can still look around.

### 5.4 Facing

Display entities are not billboarded. `MenuIcon.billboardMode()` returns `0` (FIXED), no shipped icon overrides it, and `MenuIcon.rotate` is a no-op whenever `billboardMode() != 0`. Yaw is applied explicitly instead:

- `ItemMenuIcon.spawn()` rotates the item display toward the player's look direction at spawn time (two `rotate` calls, the second overwriting the first). Block-type items are additionally orbited around the icon position so the block face points at the viewer.
- `ClickableComponent.onTick` rotates the `CollisionPlane`, not the entity, to face the eye every tick. The hitbox tracks the player continuously; the rendered geometry does not.

### 5.5 Per-icon offsets

- `MenuIcon.spawn()` spawns at `position - (0, NAMETAG_SIZE * uiScale, 0)` where `NAMETAG_SIZE = 3.5 / 16`. `position` keeps the unshifted value and `teleport()` moves by the delta, so this baseline shift persists across every subsequent move.
- `TextMenuIcon` stacks one text display per line, spaced by `NAMETAG_SIZE * uiScale`, centered on the anchor.
- `ItemMenuIcon` offsets blocks by `-0.95 * uiScale` and non-blocks by `-(1.0 + countOffset) * uiScale` on Y, where `countOffset` is `0.09` while the stack size is 1. Stacks larger than 1 spawn a second text display for the count and shift the item by `±0.09 * uiScale` when the count crosses 1.

Hitbox geometry and bounding-box derivation are covered in `04 - Components & Hitboxes.md`.

## 6. Click pipeline

### 6.1 Capture

One listener, registered in the `MenuSessionManager` constructor:

```java
Events.listen(HoloUI.INSTANCE, PlayerInteractEvent.class, EventPriority.MONITOR, this::dispatchClick);
```

`dispatchClick` returns immediately unless the action is `LEFT_CLICK_AIR` or `LEFT_CLICK_BLOCK`. Right clicks are never processed. There is no `PlayerInteractEntityEvent` listener, no `Interaction` entity, and no raycast in the click path.

### 6.2 Selection is computed on the tick loop

`ClickableComponent.onTick`, once per tick per component:

1. Clone the player's eye location and `rotateToFace` the plane toward it.
2. `plane.isLookingAt(eyePosition, eyeDirection)` — a ray/plane intersection followed by a rectangular bounds test in the plane's own axes. `CollisionPlane.isLookingAt` returns `false` when the ray is parallel to the plane (`proj == 0`) or the intersection is behind the eye (`distance < 0`), and uses strict `<` against the half extents, so exactly-on-edge is a miss.
3. On a rising edge, `selected = true` and the icon moves by `highlightMod` along the plane normal. On a falling edge, `selected = false` and the icon teleports back to `location`.

`SessionHolder.snapshotClick()` reads the volatile `session` field without taking `sessionLock` and collects every component that is both `isOpen()` and `isSelected()`. Consequences:

- Click resolution carries up to one tick of latency and uses the plane orientation from the previous tick.
- A component that is open but has not ticked yet has `selected == false` and cannot be clicked.
- Overlapping hitboxes all fire; there is no nearest-hit arbitration.

### 6.3 Dispatch

When the snapshot is non-null — at least one selected component — the interact event is cancelled with `event.setCancelled(true)` at `MONITOR` priority, so the click does not reach the world. When nothing is selected the event is left untouched.

Then, per selected component:

1. `ApiEvents.fireClick` fires `HoloUiMenuClickEvent`, constructed only when the handler list is non-empty. Cancelling skips that one component; the remaining components still fire.
2. `component.onClick()` inside a try/catch that logs and continues. `ButtonComponent` executes its resolved `MenuAction` list; a `CommandMenuAction` with a console source hops to `SchedulerUtils.runGlobal`, and a player source dispatches directly. `ToggleComponent` runs the branch actions, swaps to the other icon, and flips its cached `state`; the placeholder condition is evaluated once, in the constructor, and the state is purely local afterwards.
3. When the session came from the API and `handle.live()`, the owner's `HoloClickHandler` is dispatched through `ApiClickGuard`.

`ApiClickGuard` (`DEFAULT_FAULT_LIMIT = 5`, `DEFAULT_SLOW_MILLIS = 5`) skips quarantined and inactive owners, catches `Throwable`, counts faults per owner name, and quarantines an owner after 5 faults — its menus stay open but stop receiving clicks. Handlers taking at least 5 ms produce a warning at most once per owner per minute, stating that click handlers run on the clicking player's region thread and must not block.

### 6.4 Sneak

The menu click path never reads sneak state. There is no sneak-click distinction for menus, buttons, or toggles. The only sneak handling in the runtime is `PreviewScaleService`, which applies to container previews only and is documented in `09 - Container Previews.md`.

## 7. Close paths

`HoloCloseReason` has thirteen constants. The reason affects only the API handle's `onClosed` callback; visual teardown is identical for every reason.

| Trigger | Code path | Reason |
| --- | --- | --- |
| Opening a menu while one is open | `SessionHolder.openSessionLocked` -> `detachSession(true)` | `REPLACED` |
| Open requested for an offline player | `openSessionLocked` early return | `QUIT` (incoming handle; no session created) |
| `/holoui close` | `destroySession(player, false)` | `CLOSED_BY_COMMAND` |
| `HoloMenuHandle.close()` | `HoloUiServiceImpl.requestClose` | `CLOSED_BY_OWNER` |
| Move to a location failing `isValid` | `PlayerMoveEvent` handler | `MOVED_OUT_OF_RANGE` |
| Death with `closeOnDeath` | `PlayerDeathEvent` handler | `DEATH` |
| Respawn location failing `isValid` | `PlayerRespawnEvent` handler | `RESPAWN` |
| Teleport with `closeOnTeleport`, or destination failing `isValid` | `PlayerTeleportEvent` handler | `TELEPORT` |
| Quit or offline detected in tick | `PlayerQuitEvent` handler / tick loop | `QUIT` |
| Menu definition file changed or deleted | `ConfigManager` -> `destroyAllType` | `DEFINITION_RELOADED` |
| Plugin disable or BileTools pre-unload | `HoloUI.drain` -> `destroyAll` | `HOLOUI_SHUTDOWN` |
| Owner plugin disabled | `HoloUiServiceImpl` `PluginDisableEvent` listener | `OWNER_DISABLED` |
| API open where the definition is unresolvable, permission is denied, or `createNewSession` returned false | `HoloUiServiceImpl.openNow` | `DENIED` |
| API open that could not be scheduled or threw | `HoloUiServiceImpl.dispatchOpen` / `openNow` | `OPEN_FAILED` |

Teardown mechanics in `SessionHolder.detachSession`:

1. `lastSession = history ? session.getId() : null`.
2. `session.close()` iterates components, each `close()` inside its own try/catch, so one failing component cannot strand the rest. `MenuComponent.close()` sets `open = false`, calls `icon.remove()` (which deletes every display entity of that icon), then `onClose()`.
3. `openMenus.publish(playerId, null)` and `HoloUiTelemetry.decrementMenusOpen()`.
4. The API handle is terminated after the lock is released.

### 7.1 Event handlers

All listeners are registered in the `MenuSessionManager` constructor through `art.arcane.volmlib.util.bukkit.Events`, which registers with `ignoreCancelled = false`. Cancelled events are still delivered, so any cancellation filtering is explicit in the handler.

**Quit** — `PlayerQuitEvent` at the default `NORMAL` priority removes the holder from the map and calls `holder.close(QUIT)`, closing both session and preview. `PreviewScaleService` separately clears its per-player sneak and adjust state. The tick loop's `isOnline` check is a redundant backstop for the same case.

**Move** — `PlayerMoveEvent` at `HIGHEST`, returning early when the event is cancelled or `getTo()` is null. Order inside `inspectSession`: `freezePlayer` first rewrites `to`'s X/Y/Z back to `from`'s, zeroes a non-zero velocity, and returns; otherwise an `isValid` failure closes with `MOVED_OUT_OF_RANGE`, then `followPlayer` calls `session.move(to.clone())`. A locked menu therefore does not close from the ordinary movement range check, while respawn and teleport keep their separate validity checks.

**Death** — `PlayerDeathEvent` at `MONITOR`. Closes with `DEATH` only when the definition sets `closeOnDeath`. Otherwise the session survives; the display entities are packet-only and are not removed by the client's respawn.

**Respawn** — `PlayerRespawnEvent` at `MONITOR`. A respawn location failing `isValid` closes with `RESPAWN`; otherwise the menu moves to the respawn location.

**Teleport** — `PlayerTeleportEvent` at `MONITOR`. Closes with `TELEPORT` when `closeOnTeleport` is set or the destination fails `isValid`; otherwise the menu moves to the destination.

**World change** — there is no `PlayerChangedWorldEvent` listener. A cross-world move is caught only by the teleport handler, whose `isValid` compares `centerPoint.getWorld()` against the destination world; a mismatch fails the check and produces `TELEPORT`.

```java
centerPoint.getWorld() != null
    && Objects.equals(loc.getWorld(), centerPoint.getWorld())
    && centerPoint.distanceSquared(loc) <= maxDistance * maxDistance + offsetDistance;
```

`offsetDistance` is `offset.lengthSquared()`, already squared. `maxDistance` defaults to `6E7` and is clamped to `[0, 6E7]` by `MenuDefinitionData.getMaxDistance()`.

## 8. PacketUtils and ParticleUtils

`PacketUtils` is stateless and exposes `send(Player, PacketWrapper)`, `send(Player, Collection<PacketWrapper>)`, and `send(Collection<Player>, Collection<PacketWrapper>)`; the first two funnel into the third, and a null player is ignored. The collection form records `players.size() * packets.size()` into `HoloUiTelemetry.countPackets` before sending, then loops `PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet)`. There is no bundling or batching, so an icon spawn is two packets per display entity. `vector(Vector3d)` and `vector3d(Vector)` are the only conversion between PacketEvents and Bukkit vector types in the codebase.

Packets are used for everything HoloUi renders. The properties the code depends on are per-viewer visibility with no server-side tracker involvement, negative entity ids that never enter the server's id space, no chunk load or entity tick or persistence, and teardown that is a single destroy packet. Bukkit API is still used, on the main or region thread, for block and entity raycasts, particles, sounds, inventory reads for previews, permission checks, and command dispatch.

`ParticleUtils` has one method:

```java
w.spawnParticle(REDSTONE, v.getX(), v.getY(), v.getZ(), 5, new Particle.DustOptions(c, 1));
```

`REDSTONE` is resolved once via `RegistryUtil.find(Particle.class, "redstone", "dust")` because the constant was renamed across Minecraft versions. This is the Bukkit API rather than a packet, so debug particles are visible to everyone in range, unlike the display entities. It is used only by `MenuSessionManager.controlPositionDebug` (yellow at `getCenterInitialYAdjusted()`, orange at each component location) and `ClickableComponent.highlightHitbox` (blue interpolated outline plus a red ray along the plane normal), both on 2-tick tasks that are started and cancelled reactively when the corresponding setting changes.

## 9. VolmLib integration service

`HoloUiIntegrationService` implements VolmLib's `IntegrationServiceContract` for in-process health and metric polling by other plugins. `onEnable` registers the instance against `IntegrationServiceContract.class` at `ServicePriority.Normal`; `drain()` unregisters that exact instance and clears `HoloUiTelemetry`. This is an internal VolmLib contract, not part of `art.arcane.holoui.api`.

### 9.1 Identity and protocol negotiation

| Contract method | Result |
|---|---|
| `pluginId()` | `holoui` |
| `pluginVersion()` | The enabled plugin description version, or `unknown` when the plugin instance is unavailable |
| `supportedProtocols()` | `1.0` and `1.1` |
| `capabilities()` | `handshake`, `heartbeat`, `metrics` |
| `metricDescriptors()` | Every descriptor in `IntegrationMetricSchema` whose key begins with `holoui.` |
| `metricGroups()` | Empty list from the interface default |

`handshake(request)` selects the highest protocol version common to HoloUi and `request.supportedProtocols()`. A successful response carries `accepted=true`, the selected version, message `ok`, both supported-protocol and capability sets, and the current timestamp. A null request returns `accepted=false` with message `missing request`; no common version returns `accepted=false` with message `no-common-protocol`. Failed handshakes do not change the current protocol.

The negotiated version is one volatile value shared by the service, initialized to `1.1` and replaced after every successful handshake. `heartbeat()` reports that current version with `healthy=true`, message `ok`, and the current timestamp; it does not probe a subsystem before reporting healthy.

### 9.2 Metric sampling

`sampleMetrics(metricKeys)` samples the requested keys at one `System.currentTimeMillis()` timestamp. A null or empty set requests all eleven HoloUi keys from `IntegrationMetricSchema.holouiKeys()`. A non-empty set returns one entry for each requested key; a non-HoloUi or otherwise unsupported key returns an unavailable sample with message `unsupported-key`.

| Metric key | Type and unit | Source and availability |
|---|---|---|
| `holoui.session-holders` | integer, players | `MenuSessionManager.holderCount()`; unavailable as `session-manager-not-ready` before that manager exists |
| `holoui.menus-open` | integer, menus | Current `HoloUiTelemetry.menusOpen()` counter |
| `holoui.previews-open` | integer, previews | Current `HoloUiTelemetry.previewsOpen()` counter |
| `holoui.display-entities` | integer, entities | Number of entries held by `DisplayEntityManager` |
| `holoui.display-entities-visible` | integer, entities | Number of display entities currently associated with a viewer |
| `holoui.menu-definitions` | integer, menus | `ConfigManager.keys().size()`; unavailable as `config-manager-not-ready` before that manager exists |
| `holoui.packets-per-second` | double, packets per second | Packet send count divided by the elapsed telemetry window |
| `holoui.spawns-per-second` | double, entities per second | Display-entity spawn and despawn operations divided by the elapsed telemetry window |
| `holoui.tick-ms` | double, milliseconds per second | Measured session-dispatch nanoseconds converted to milliseconds and divided by the elapsed telemetry window |
| `holoui.preview-refresh-per-second` | double, operations per second | Scheduled preview refreshes divided by the elapsed telemetry window |
| `holoui.builder-server-running` | integer, boolean | Always unavailable with message `builder-server-removed`; HoloUi no longer hosts the editor |

Rate metrics use a shared 1000 ms minimum window in `HoloUiTelemetry`. The first sample establishes the baseline and returns the current stored rate, initially `0`; later calls recompute all four rates once at least 1000 ms has elapsed. Samples are aggregate process counters only and contain no player, world, menu, or document identifiers.

## 10. Runtime notes

- The `displayEntities` key is a fresh random UUID from `add()`, not the entity's own `uuid` field.
- `PacketUtils` performs no null check on `PacketEvents.getAPI()`, so a send before PacketEvents init or after `terminate()` throws.
- `ToggleComponent` evaluates its placeholder condition only in the constructor; after that the toggle state is local and does not track the underlying value.
