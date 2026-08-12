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
| 7 | Register outgoing `BungeeCord` | Enables proxy-connect actions |
| 8 | Construct `HoloUiPersistenceCoordinator` and `HoloUiProjectTransaction`, then run recovery under the coordinator write lease | Completes rollback or archival for every durable editor-sync transaction before any menu watcher or board store can observe the files |
| 9 | Construct `ConfigManager(getDataFolder())` | Creates `images/`, `menus/`, `settings.json`, the shared menu mutation worker, and the 5-tick and 20-tick reload tasks |
| 10 | `new BoardService(this)`, then `start()` | Starts the single serialized board-storage worker and asynchronously loads `boards/` |
| 11 | `new PreviewDocumentRegistry(getDataFolder())`, then `startWatching()` | Creates `previews/`, extracts shipped documents that are missing, compiles every `*.json`, arms a 5-tick folder watcher |
| 12 | `new ItemProviderRegistry(this)`, then `activateAll()` | Registers plugin enable/disable listeners; activates providers only when `customItems` is true |
| 13 | `new ContainerProtectionService(this)`, then `activate()` | Container access checks for previews |
| 14 | `new MenuSessionManager()` | Starts the two 1-tick tasks, registers the Bukkit listeners, seeds the debug tasks from the current settings |
| 15 | `new BoardRuntimeManager(this, boardService)` | Atomically subscribes to the board-service snapshot, starts viewer/follow updates, and registers quit cleanup |
| 16 | Construct and start `EditorSyncService` | Loads secure relay capabilities, quarantines invalid individual entries, and starts outbound polling. A corrupt root store disables only editor sync and leaves one-way handoffs available |
| 17 | `PreviewScaleService.init(this)` | Loads `plugins/holoui/preview-scales.json`; registers `PlayerToggleSneakEvent`, `PlayerItemHeldEvent`, `PlayerQuitEvent` listeners |
| 18 | `new HoloUiCommandService(this)`, then `register()` | Binds executor and tab completer to `holoui` and builds the Director runtime engine. If `getCommand("holoui")` returns null, logs `Failed to find command 'holoui'` and returns without registering |
| 19 | `HoloUiMetrics.start(this, BSTATS_PLUGIN_ID)` | Guarded by `if (BSTATS_PLUGIN_ID > 0)`, a compile-time constant, so it always runs |
| 20 | `new HoloUiIntegrationService()`, then `register()` | Registers `IntegrationServiceContract` at `ServicePriority.Normal` |
| 21 | `new HoloUiServiceImpl(this)`, then `register()` | Public API service |
| 22 | `new PlaceholderRegistration(getLogger())`; when `PlaceholderRegistration.isPlaceholderApiEnabled()`, `HoloUiPlaceholderInstaller.install(...)` | PAPI expansion installed only when PlaceholderAPI is enabled |

One ordering constraint is recorded in source: `PreviewDocumentRegistry` is fully published before `MenuSessionManager` exists, because the session manager's raycast tick queries the registry.

### 1.4 onDisable, onPreUnload, drain

`HoloUI implements ReloadAware`. `onDisable()` calls `drain()` directly. `onPreUnload(PreUnloadReason)` logs `BileTools pre-unload hook fired (%s)...` and calls `drain()`, so a BileTools-driven reload tears down before the class loader is dropped.

`drain()` is guarded by `alreadyDrained.compareAndSet(false, true)` and is a no-op on every call after the first, so the disable path and the pre-unload path can both fire without double teardown. The flag is never reset; a plugin instance drains exactly once. Every step is null-guarded:

1. `placeholderRegistration.unregister()`
2. `apiService.unregister()`
3. `integrationService.unregister()` (also calls `HoloUiTelemetry.clear()`)
4. `containerProtection.shutdown()`
5. `commandService.shutdown()` — clears staged board edits and their previews
6. `editorSyncService.shutdown()` — stops new polling, cancels active relay exchanges and any owning-scheduler publication future, and drains completion stages for at most 30 seconds; every session mutation is already persisted synchronously, while persistence work still active at the deadline is not interrupted and closes its daemon executor when finished
7. `configManager.shutdown()` — drains the menu mutation worker and writes `settings.json`
8. `boardRuntime.shutdown()` — stops board ticks and closes packet-only board views on their viewer schedulers
9. `boardService.shutdown()` — rejects queued work and waits for active board disk I/O to quiesce
10. `sessionManager.destroyAll()`
11. `itemProviders.shutdown()`
12. `PreviewScaleService.shutdown()` — persists `preview-scales.json`, releases HUD claims
13. `hudLanes.shutdown()`
14. `hudSlots.shutdown()`
15. `PacketEvents.getAPI().terminate()` when the API is non-null
16. `SpigotPacketEventsBuilder.clearBuildCache()`
17. `metrics.shutdown()`
18. unregister the outgoing `BungeeCord` channel
19. `SchedulerUtils.cancelPluginTasks(this)`
20. `INSTANCE = null` when `INSTANCE == this`

Board and personal-session shutdown both complete while PacketEvents is active, so teardown can send the required destroy packets. Only after those holders are empty does PacketEvents terminate; repeating tasks are cancelled afterward. `PacketUtils` has no null check on `PacketEvents.getAPI()`, so this order is required.

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

`holders` is keyed by the `Player` instance, not by UUID, so a holder lives for exactly one login. Each holder carries at most one `MenuSession` and at most one `ContainerPreview`, under two separate locks; a menu and a container preview can be open at the same time and do not interact. `session` is `volatile`; `hasSession()` and the event-time click snapshot read it without locking, while every mutation path takes `sessionLock`.

Each personal holder owns one active menu plus a root id and a history deque used by native submenu navigation. It does not keep inactive sessions alive: navigation constructs and opens the replacement transactionally, commits history only after success, then closes the previous session. An API menu and a config menu still compete for the same active slot.

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
   - The current session remains attached while the replacement is prepared.
   - `openMenus.publish(playerId, data.getId())` happens before replacement construction, so constructor-time placeholders see the incoming menu id.
   - `new MenuSession(data, player, MenuSessionOptions.personal(...))` computes the transform and constructs components. A duplicated id keeps the first component and warns; later duplicates are absent from render, tick, and click lists.
   - `replacement.open()` spawns its packet entities.
   - Only after success does the holder commit root/history state, swap `session`, mark an API handle open, increment telemetry, close the previous session, and terminate its handle with `REPLACED`.

If construction or `open()` throws, the holder closes the partial replacement, restores the previous session and placeholder snapshot, leaves navigation and telemetry unchanged, terminates an incoming API handle with `OPEN_FAILED`, and rethrows the failure. A failed submenu or replacement therefore cannot blank a working menu.

`MenuSession` creates a `MenuTransform` from the player's feet anchor, the definition offset, the current yaw and `uiScale`. `MenuSession.open()` refreshes its facing from the player's eye yaw and then calls `component.open()` for every component. `MenuComponent.open()`:

1. `applyTransform()` resolves the component's raw local offset, display yaw and scale through the session transform. The icon is still null here, so no teleport packet is produced.
2. `createIcon()`. `MenuIcon.createIcon` dispatches on the icon data type; a `MenuIconException` is logged and falls back to the missing-icon `TextImageMenuIcon`. If that fallback also throws, an `IllegalStateException` carrying both failures aborts the open and reaches the transactional rollback path.
3. `icon.spawn()` — creates the display entities and sends spawn and metadata packets to the session player.
4. `onOpen()`. For `ClickableComponent` this is `refreshPlane()`.
5. `open = true`.

The `PlayerSnapshotStore<String> openMenus` written in this path is what `HoloUiPlaceholderExpansion` answers `menu.open` and `menu.id` from (see `07 - Expressions & Placeholders.md`). The holder captures the player UUID once at construction and always publishes under that captured id.

`/holoui move` takes a separate mutation path: `HoloCommand.move` checks `holoui.command.move`, then `MenuSessionManager.moveSession` delegates to `SessionHolder.moveSession` under `sessionLock`. The holder returns false when no menu is open; otherwise `MenuSession.move` replaces only the transform anchor with the player's current feet location, then reapplies component positions, icon orientation and clickable planes. Facing yaw and scale are preserved, so the command is translation-only. It does not replace the session, publish a different placeholder snapshot, change history or telemetry, fire open/close events, terminate an API handle, or persist JSON.

### 2.2 Menu history

Native `push` appends the outgoing id to the front of a per-holder deque, `replace` swaps the active menu without adding history, `back` resolves and removes the first history entry only after a successful open, and `home` resolves the captured root then clears history on success. `close`, definition teardown, quit, and holder removal clear root and history; history never survives a relog.

Every target is re-resolved through `ConfigManager`, requires `holoui.open.<id>`, and fires `HoloUiMenuOpenEvent`. A missing target, denied permission, cancelled event, or failed replacement leaves the current session and history intact.

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

`MenuSession.drainApiUpdates()` does work only when the handle reports `dirty()`. Each staged icon is applied via `MenuComponent.applyIcon`: a text-only update mutates existing text displays in place when the line count is stable and respawns only that icon when it changes; other icon types build a replacement. Per-component exceptions are caught and logged.

`MenuComponent.tick()` is a no-op while `!open`. It first calls `currentIcon.tick()`, where animated images advance and text icons refresh PlaceholderAPI content at their configured interval. A changed text result increments the icon geometry revision; the component then reapplies the icon transform and calls `onIconChanged()` so clickables rebuild their plane before `onTick()` performs hover selection.

`refreshVisuals()` — triggered by a `uiScale` or `previewScale` change, or by an image asset hot reload — closes every open component, refreshes the session transform's scale, reapplies component geometry, and reopens those components with brand-new display entities.

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
| Player `/holoui move` | Director's immediate command thread, the same player-owned command context used by `/holoui open`, `/holoui back`, and `/holoui close` |
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

Text, item, and block icons use `EntityTypes.TEXT_DISPLAY`, `EntityTypes.ITEM_DISPLAY`, and `EntityTypes.BLOCK_DISPLAY`. Entity icons use the configured living entity type as a raw packet-only entity. HoloUI creates no `Interaction` entities, armor stands, or Bukkit world entities.

`DisplayEntity.dataPacket()` writes the shared indices and then type-specific ones:

| Index range | Contents |
| --- | --- |
| 0, 5 | entity flags, no-gravity (default `true`) |
| 8-22 | interpolation delay/duration, teleport duration, translation, scale, left/right rotation quaternions, billboard, brightness (`-1`), view range (`1`), shadow radius/strength (`0`), width, height, glow color override (`-1`) |
| 23-27 (TEXT_DISPLAY) | Adventure component, line width, background color, text opacity (`0xFF`), text flags |
| 23-24 (ITEM_DISPLAY) | item stack (converted with `SpigotConversionUtil.fromBukkitItemStack`), item display type |
| 23 (BLOCK_DISPLAY) | global block-state id converted with `SpigotConversionUtil.fromBukkitBlockData` |
| raw entity | only shared indices 0 and 5; display metadata indices are not emitted |

`lineWidth` defaults to `200` on the record, but every builder sets `2000`.

`spawn()` returns `WrapperPlayServerSpawnEntity` plus `WrapperPlayServerEntityMetadata`; `remove()` returns `WrapperPlayServerDestroyEntities`; `goTo`, `move`, and `rotate` all return `WrapperPlayServerEntityTeleport` with `onGround = true` and mutate the cached location and rotation.

### 4.2 Single-viewer visibility

`DisplayEntityManager` holds two static maps:

- `displayEntities: Map<UUID, DisplayEntity>` — every created entity, keyed by a fresh random UUID returned from `add()`. That key is not the entity's own `uuid` field.
- `playerVisibility: Map<UUID, Player>` — which player currently sees each entity.

`playerVisibility` holds one player per entity, so a `DisplayEntity` is visible to at most one viewer; a `spawn` for a second player would overwrite the record and orphan the first viewer's copy. Every session and preview builds its own entities, so the invariant holds by construction.

Every mutator except `delete` and `location` requires a live `playerVisibility` entry, so an entity that was added but never spawned cannot be moved or updated. `changeName` and `changeTextBackground` require text display kind; `changeItem` requires item display kind; mismatches are silent no-ops.

Visibility scoping is absolute: packets go only to `session.getPlayer()` via `MenuIcon`, or to the preview's owner. There is no broadcast path. Other players never receive the spawn packet, cannot see the menu, and cannot interact with it.

### 4.3 DisplayEntityManager lifecycle

- Normal close: `MenuIcon.remove()` calls `DisplayEntityManager.delete(uuid, player)` for each entity. That removes both map entries and sends a destroy packet to the recorded viewer, or to the passed fallback player when the visibility record is already gone.
- `MenuSessionManager.destroyAll()` — reached from plugin disable and from BileTools `onPreUnload`, both through the idempotent `HoloUI.drain()` — closes every holder with `HOLOUI_SHUTDOWN`, then clears `holders` and `openMenus`.
- No boot-time orphan sweep exists and none is needed for server-side state: no real entity is ever created, so nothing persists in a world or a chunk across a restart.
- Client-side ghosts are possible when the process dies without sending destroy packets (`SIGKILL`, crash). They clear on relog or dimension change.
- There is no bulk registry clear during drain. Session and preview teardown must retain each registered display until `delete` sends its destroy packet; dropping the maps first would leave client-side entities visible during a hot reload.

## 5. Positioning

### 5.1 Canonical transform

`MenuSession` owns one immutable `MenuTransform` containing an anchor, the raw menu offset, normalized facing yaw, pitch, roll, and the session scale. A personal session uses the player's feet anchor with zero pitch and roll; a world board supplies its effective absolute transform. The definition's menu offset is not scaled. Component, icon and hitbox local vectors are scaled, mirror X so positive configured X reads as "right", then rotate by roll around Z, pitch around X, and `layoutYaw = -facingYaw` around Y.

`menuOrigin()` resolves the menu offset from the anchor. `componentPosition()`, `localPosition()` and `localVector()` resolve all other geometry. Component and icon locations carry `displayYaw = facingYaw + 180` and the transform pitch; fixed-display metadata and `createPlane()` also use transform roll. This keeps component placement, fixed-display orientation, block-item depth orbit, bounding boxes and custom hitbox offsets in one coordinate frame.

At open, the transform's facing is refreshed from the player's eye yaw. `uiScale` is clamped to `[0.25, 4.0]`; a visual refresh replaces the transform scale before rebuilding components. See `01 - Installation & Configuration.md`.

### 5.2 Translation and follow rotation

`MenuSession.move(location)` replaces only the anchor. This is the `/holoui move` path and intentionally preserves the current facing yaw and scale.

`MenuSession.follow(location)` replaces both anchor and facing yaw. Following menus therefore rotate around the player on rotation-only `PlayerMoveEvent`s as well as translating on positional moves. Respawn and allowed teleport handling call `follow` for following menus and translation-only `move` for other menus.

### 5.3 Freeze, follow, static

| `lockPosition` | `followPlayer` | Behavior |
| --- | --- | --- |
| true | true | The player cannot translate: the move handler copies `from`'s coordinates into `to` and zeroes velocity. The menu anchor stays fixed but `session.follow(to)` adopts the allowed look yaw, so the menu still turns with the player |
| true | false | The player cannot translate and the static menu keeps its anchor and facing |
| false | true | Every accepted move calls `session.follow(to)`, replacing both anchor and facing yaw and reapplying all component, icon and hitbox geometry |
| false | false | The menu stays where it was opened. The player may walk away until `maxDistance` is exceeded, which closes it with `MOVED_OUT_OF_RANGE` |

`lockPosition` freezes the player's coordinates, not their look direction. A locked following menu is stationary but rotates with the allowed yaw; a locked non-following menu remains completely static.

### 5.4 Facing

An omitted icon style resolves to `billboard: fixed`, so the transform's explicit display yaw, pitch, and roll determine its face. Authored `vertical`, `horizontal`, and `center` billboard modes delegate visual facing to the client. Their clickable planes are re-oriented toward the viewer on the owning tick and again from the event-time eye before intersection testing; fixed planes retain the complete session or board transform. This keeps rendered and clickable orientation aligned while personal following and board follow transforms change.

Block-type item icons additionally use transform-local `(0, -0.95, 0.3)`, so their depth orbit changes with the same transform and scale.

### 5.5 Per-icon offsets

- `MenuIcon.spawn()` spawns at transform-local `(0, -NAMETAG_SIZE, 0)` where `NAMETAG_SIZE = 3.5 / 16`. `position` keeps the unshifted value and `applyTransform()` moves by the delta, so this baseline shift persists across every subsequent move.
- `TextMenuIcon` stacks one text display per line, spaced by `NAMETAG_SIZE * uiScale`, centered on the anchor.
- `ItemMenuIcon` offsets blocks by `-0.95 * uiScale` and non-blocks by `-(1.0 + countOffset) * uiScale` on Y, where `countOffset` is `0.09` while the stack size is 1. Stacks larger than 1 spawn a second text display for the count and shift the item by `±0.09 * uiScale` when the count crosses 1.

Hitbox geometry and bounding-box derivation are covered in `04 - Components & Hitboxes.md`.

## 6. Click pipeline

### 6.1 Capture

One listener, registered in the `MenuSessionManager` constructor:

```java
Events.listen(HoloUI.INSTANCE, PlayerInteractEvent.class, EventPriority.HIGHEST, this::dispatchClick);
```

`dispatchClick` accepts main-hand `LEFT_CLICK_AIR`, `LEFT_CLICK_BLOCK`, `RIGHT_CLICK_AIR`, and `RIGHT_CLICK_BLOCK`, then maps action plus `player.isSneaking()` to the exact `HoloClickTrigger`. It ignores off-hand, physical, and already-cancelled events. There is no `PlayerInteractEntityEvent` listener or `Interaction` entity.

### 6.2 Selection is computed on the tick loop

`ClickableComponent.onTick`, once per tick per component:

1. `plane.isLookingAt(eyePosition, eyeDirection)` performs a ray/plane intersection followed by a rectangular bounds test in the plane's transform-aligned axes. `CollisionPlane.isLookingAt` returns `false` when the ray is parallel to the plane (`proj == 0`) or the intersection is behind the eye (`distance < 0`), and uses strict `<` against the half extents, so exactly-on-edge is a miss.
2. On a rising edge, `selected = true` and the icon moves by `highlightMod` along the plane normal. On a falling edge, `selected = false` and the icon reapplies its component transform.

`SessionHolder.snapshotClick()` reads the volatile `session` field without taking `sessionLock`, recomputes each clickable's event-time ray intersection, and keeps the smallest positive distance. `BoardRuntimeManager.findClickTarget()` does the same across visible, interactive board views, capped by each board's interaction range. Consequences:

- Click resolution does not depend on the tick's `selected` flag. `intersectionDistance` re-orients non-fixed billboard planes against the event-time eye before testing them.
- The nearest personal and board candidates are compared; only one component fires. Strict `<` keeps the first declared personal component on an exact internal tie, and an exact personal/board tie goes to the personal menu.
- Before cancellation, `rayTraceBlocks` rejects a block obstruction strictly closer than the winning plane. Fluids and passable blocks are ignored.

### 6.3 Dispatch

When there is an unobstructed candidate, the interact event is cancelled with `event.setCancelled(true)` at `HIGHEST` priority, so the click does not reach the world. A miss or obstructed hit is left untouched.

Then, for the one winning component:

1. `ApiEvents.fireClick` fires `HoloUiMenuClickEvent` with the exact trigger, constructed only when the handler list is non-empty. Cancellation skips both JSON actions and the API handler.
2. `component.onClick(trigger)` runs inside a try/catch. `MenuAction.execute` scans in declaration order and executes only `any` or exact-trigger bindings; matching navigation returns `STOP`. A console command hops to `SchedulerUtils.runGlobal`, while a player command dispatches directly. A toggle swaps its icon and cached state unless a matching navigation stopped its branch.
3. When a personal session came from the API and `handle.live()`, the owner's `HoloClickHandler` receives a `HoloClick` carrying the same trigger through `ApiClickGuard`. Boards have no API-menu handler.

`ApiClickGuard` (`DEFAULT_FAULT_LIMIT = 5`, `DEFAULT_SLOW_MILLIS = 5`) skips quarantined and inactive owners, catches `Throwable`, counts faults per owner name, and quarantines an owner after 5 faults — its menus stay open but stop receiving clicks. Handlers taking at least 5 ms produce a warning at most once per owner per minute, stating that click handlers run on the clicking player's region thread and must not block.

### 6.4 Trigger bindings

The four delivered interaction values are `LEFT_CLICK`, `RIGHT_CLICK`, `SHIFT_LEFT_CLICK`, and `SHIFT_RIGHT_CLICK`. Configured actions may additionally bind `ANY`; omitted and explicit-null action triggers resolve to `ANY`. An exact binding matches only itself, while `ANY` matches all four interactions. The container-preview sneak controls in `PreviewScaleService` are separate from menu action binding.

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

1. Preserve the outgoing id in navigation history only for the explicit history-bearing path; otherwise clear root and history.
2. `session.close()` iterates components, each `close()` inside its own try/catch, so one failing component cannot strand the rest. `MenuComponent.close()` sets `open = false`, calls `icon.remove()` (which deletes every display entity of that icon), then `onClose()`.
3. Clear `session`, publish no open menu for PlaceholderAPI, and decrement telemetry.
4. Terminate the API handle after the lock is released.

### 7.1 Event handlers

All listeners are registered in the `MenuSessionManager` constructor through `art.arcane.volmlib.util.bukkit.Events`, which registers with `ignoreCancelled = false`. Cancelled events are still delivered, so any cancellation filtering is explicit in the handler.

**Quit** — `PlayerQuitEvent` at the default `NORMAL` priority removes the holder from the map and calls `holder.close(QUIT)`, closing both session and preview. `PreviewScaleService` separately clears its per-player sneak and adjust state. The tick loop's `isOnline` check is a redundant backstop for the same case.

**Move** — `PlayerMoveEvent` at `HIGHEST`, returning early when the event is cancelled or `getTo()` is null. Inside `inspectSession`, `freezePlayer` first rewrites `to`'s X/Y/Z back to `from`'s and zeroes a non-zero velocity; when that locked menu follows, it still calls `session.follow(to)` so rotation-only changes update facing. An unlocked `isValid` failure closes with `MOVED_OUT_OF_RANGE`; otherwise `followPlayer` calls `session.follow(to)`. A locked menu therefore does not close from the ordinary movement range check, while respawn and teleport keep their separate validity checks.

**Death** — `PlayerDeathEvent` at `MONITOR`. Closes with `DEATH` only when the definition sets `closeOnDeath`. Otherwise the session survives; the display entities are packet-only and are not removed by the client's respawn.

**Respawn** — `PlayerRespawnEvent` at `MONITOR`. A respawn location failing `isValid` closes with `RESPAWN`; otherwise the menu re-anchors there. Following menus also adopt the respawn yaw; other menus preserve their facing.

**Teleport** — `PlayerTeleportEvent` at `MONITOR`. Closes with `TELEPORT` when `closeOnTeleport` is set or the destination fails `isValid`; otherwise the menu re-anchors there. Following menus also adopt the destination yaw; other menus preserve their facing.

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

`REDSTONE` is resolved once via `RegistryUtil.find(Particle.class, "redstone", "dust")` because the constant was renamed across Minecraft versions. This is the Bukkit API rather than a packet, so debug particles are visible to everyone in range, unlike the display entities. It is used only by `MenuSessionManager.controlPositionDebug` (yellow at the transformed menu origin, orange at each component location) and `ClickableComponent.highlightHitbox` (blue interpolated outline plus a red ray along the plane normal), both on 2-tick tasks that are started and cancelled reactively when the corresponding setting changes.

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

## 10. Persistent board storage contract

`art.arcane.holoui.board` owns persistent world-board storage and runtime rendering. `HoloUI.onEnable()` starts `BoardService` against `plugins/holoui/boards/`, constructs `BoardRuntimeManager`, and then registers the command tree. Startup load and every write or reload are serialized through the service's async queue; successful operations publish an immutable in-memory spatial index and notify the runtime only after disk work completes.

Board ids are lowercase canonical paths. Each slash introduces a nested directory, so id `spawn/shops/main` maps exactly to `boards/spawn/shops/main.json`. The whole id is at most 255 characters; each segment is at most 64 characters and matches `[a-z0-9][a-z0-9._-]*`. Empty, `.`, `..`, absolute, backslash-separated, non-ASCII and otherwise non-canonical paths are rejected, and symbolic-link paths are never followed for reads, writes, or deletes.

The version-1 document shape is:

```json
{
  "schemaVersion": 1,
  "id": "spawn/shops/main",
  "uuid": "3d89a77d-a9c8-45a6-8b83-c74a38fca963",
  "revision": 1,
  "rootMenuId": "shop",
  "transform": {
    "worldKey": "minecraft:overworld",
    "worldUuid": "8bb2c705-ddb6-4a1f-bad4-f6f55fe71c13",
    "x": 12.5,
    "y": 65.0,
    "z": -4.5,
    "yaw": 90.0,
    "pitch": 0.0,
    "roll": 0.0,
    "scale": 1.0
  },
  "follow": {
    "mode": "none",
    "targetPlayerUuid": null,
    "rotation": "fixed"
  },
  "visibility": {
    "mode": "public",
    "viewPermission": null,
    "interactPermission": null,
    "viewRange": 64.0,
    "interactionRange": 8.0
  }
}
```

`uuid` is the stable board identity and cannot change during an update or reload. `revision` starts at `1`; repository updates require the caller's expected revision and atomically write the next revision, rejecting stale callers with `BoardRevisionConflictException`. `rootMenuId` is a portable menu reference with the same length, character, and traversal rules as board-id segments, except that case is preserved; `/` may reference a nested menu.

`transform` requires both an explicit namespaced world key and a world UUID, finite coordinates and rotations, and a scale in `[0.05, 16.0]`. Angles are canonicalized into `[-180, 180)`. The repository treats the world fields as opaque value data: it never asks Bukkit to resolve a world or load a chunk, so a well-formed board for an unavailable world remains loadable.

`follow.mode` is `none` or `player`. `none` requires no target and `fixed` rotation; `player` requires `targetPlayerUuid` and accepts `fixed`, `yaw`, or `full`. `visibility.mode` is `public`, `permission`, or `hidden`; permission visibility requires `viewPermission`, public visibility may independently require `interactPermission`, and hidden visibility carries neither. `viewRange` and `interactionRange` are required finite positive block distances, capped at `256.0` and `32.0` respectively, and interaction range cannot exceed view range; factory-created definitions use `64.0` and `8.0`.

Writes use a same-directory temporary file, flush the file, replace the target with `ATOMIC_MOVE`, and flush the containing directory; a failed write does not publish the candidate to the in-memory registry. `load()` scans nested JSON files in stable path order. A malformed, stale-revision, identity-changing, duplicate-UUID, missing-world-field, symlinked, or non-canonical file is reported in `BoardLoadResult.failures`; an id cannot claim the stable UUID of a different previously published board even if its file sorts first. When that id already has a valid in-memory definition, the last good value is retained. A cold start has no last-good value to recover, and removing a file removes its entry on the next load.

### 10.1 Effective transforms and follow storage

A non-following board stores its absolute world transform. A player-following board stores a target-relative offset and relative facing: `fixed` translates the absolute offset without target rotation, `yaw` rotates the horizontal offset and adds target yaw, and `full` also adds target pitch. `BoardRuntimeManager` samples each online target on its entity scheduler and publishes resolved absolute copies into a separate effective spatial index. The last scheduler-owned pose remains available while the target is offline, so the board keeps its last world pose and can be unfollowed or edited; sampling resumes when the target returns.

Operator location reporting, near queries, teleport, staged previews, and transform mutations use the effective absolute pose. `/holoui boards follow` converts the current absolute pose to relative storage so enabling follow does not jump the board; `unfollow` materializes the current effective pose before clearing follow. World-space move, movehere, rotate, and align operations on a following board are re-encoded against a scheduler-captured target location. A `~` value is therefore relative to the current effective pose, not the persisted relative offset.

### 10.2 Per-viewer board runtime

The runtime ticks from a one-tick global scheduling chain, then hands each viewer update to that player's entity scheduler. It queries effective candidates by world and horizontal range, checks full three-dimensional distance against `viewRange`, applies `public`, `permission`, or `hidden` visibility, and owns one packet-only `BoardViewSession` per visible board per viewer. Board visibility is the admission rule for the root menu, so a public board does not additionally require `holoui.open.<root>`; navigation to any non-root menu still requires its `holoui.open.<id>` permission. Every root and submenu open fires the cancellable API open event. Each view constructs an independent positioned `MenuSession`, so display entities remain viewer-scoped; missing worlds or root menus leave that revision unavailable until a definition or menu refresh clears the failure, while dynamic permission or event denial is retried rather than cached.

Interaction raycasts run against the viewer's open board sessions. `interactPermission` is independent of public viewing, and `interactionRange` is checked against both board distance and ray intersection distance. Native navigation changes the menu inside the same board view using its private history stack.

Definition create, update, delete, and reload notifications update the runtime indexes without rescanning disk on the tick thread. Player-follow sampling and viewer updates use concurrent registries with per-target and per-viewer in-flight guards. Shutdown cancels the tick task, closes every view, clears previews and indexes, and unregisters the service listener before the board service stops.

### 10.3 Staged operator previews

`/holoui boards edit` snapshots the published definition and revision plus its effective transform. Supported mutations update an in-memory per-player definition and a private runtime preview without disk writes; that preview is forced visible and interactable for the editor even outside published permissions and range. Save performs one revision-checked service update and clears the preview only after success. Cancel and player quit discard the staged state and clear its preview; plugin drain clears remaining command sessions before the board runtime shuts down.

### 10.4 Persistent menu content writer

`art.arcane.holoui.config.menu` owns command-driven menu writes. `ConfigManager` constructs one `MenuMutationService` for `plugins/holoui/menus/`; it serializes mutations and copies on an owned daemon storage thread whose context classloader is the plugin's loader. Shutdown rejects new work, cancels queued futures, lets an already-started atomic write finish without interruption, and waits for that executor to quiesce before settings are written. A queued operation reads no Bukkit state and performs all source reads, JSON transformation, validation, temporary-file flushes, conflict checks, atomic publication, and persisted-byte verification off gameplay threads.

The optimistic revision is SHA-256 of the exact UTF-8 source last published by `ConfigManager`. `MenuDocumentRepository` compares that revision to disk before invoking a mutation and rereads the target immediately before replacement, so a watcher edit or queued command based on stale source cannot silently overwrite the newer document. Existing-menu writes use an atomic replacement; copies use atomic no-clobber file creation and fail if another writer creates the target. Paths use case-preserving slash ids with alphanumeric-start segments, reject traversal and symbolic links, and prepare only real nested directories. Mutations operate on a deep copy of the Gson tree, which preserves extension fields not intentionally replaced; the resulting source is parsed through the same `MenuDocumentParser` used by normal config loading before and after persistence.

`MenuRowMutations` supplies the in-game content operations used by `/holoui menus` and the board-root aliases. Row numbers are one-based component-array indexes. Text insertion/removal and absolute or `~`-relative offsets share the same queue with icon replacement and display-style editing. `seticon` accepts text, image, animated image, item, block, custom-item, and entity data; a non-entity replacement carries forward the previous icon style, while entity icons reject style. `style` validates each property against its runtime range and treats `*` as removal. Whole-menu `image` replacement intentionally replaces the component list with one centered image decoration while preserving other top-level fields. Board-root forms resolve the published or staged root menu id and require the board command permission, but persist menu content immediately rather than joining the staged board-definition transaction.

Image-backed command mutations call `ConfigManager.getImage` on the storage worker before changing JSON. Its canonical resolver requires a readable regular file beneath `plugins/holoui/images/`, and Apache Commons Imaging must decode it; absolute paths, traversal, symlink escapes, and missing files are rejected. The command path performs no URL fetch or avatar lookup. Animated values validate each comma-separated frame independently.

The disk future is not the public completion point. `ConfigManager` schedules publication on the global scheduler, verifies that the in-memory revision is still the expected source (or already equals the just-written source when the folder watcher won the race), then replaces both definition and exact-source registries. Matching personal sessions are deliberately closed with `DEFINITION_RELOADED` on their per-player entity schedulers; this path does not reopen or rebuild them in place. `BoardRuntimeManager` does live-rebuild matching board views through their viewer schedulers. The folder watcher compares source revisions and suppresses a duplicate publication when it later observes the same atomic write.

### 10.5 Legacy hologram import pipeline

`ConfigManager` owns one `LegacyHologramImportService`, so its scanner shuts down before the shared menu writer and before `HoloUI` stops the board service. A command first schedules a global task to capture immutable loaded-world descriptors. YAML discovery and parsing then run on one owned daemon scanner thread; conversion, target classification, and publication return to the global scheduler, and sender feedback returns to that sender's entity scheduler or the global scheduler for console. Shutdown rejects new work, completes exposed operations exceptionally, waits up to 30 seconds for a scan, interrupts an overlong scan, and prevents any late callback from publishing.

Source locations are fixed beneath the server's `plugins` directory and are not configurable: `GHolo/holos`, `DecentHolograms/holograms`, `HolographicDisplays/database.yml`, and `FancyHolograms/holograms.yml`. Scanner paths are normalized and required to remain beneath that trusted root. Symbolic-link roots, path segments, and source entries are rejected; directory sources are non-recursive, include only regular `.yml`/`.yaml` files, and sort case-insensitively before conversion. Reads use no-follow file handles and never modify source bytes.

A scan accepts at most 4,096 directory entries, 4,096 holograms, 1,024 rows per hologram, 8,192 characters per row, and 8 MiB per YAML document. Malformed files and individual conversion failures become plan issues without discarding other valid candidates. World lookup accepts captured name, namespaced key, or UUID, and no source scan reads live Bukkit world state. Generated menu JSON is parsed through `MenuDocumentParser` and typed block/item/entity ids are validated against the live registries before it becomes a candidate.

Targets use `imports/<source>/<canonical-id>` for both menu and board. In-source canonical collisions mark every owner as conflict. Before apply, the service snapshots loaded menu source bytes and published board ids: a board collision or different menu is a conflict, while a byte-identical menu without a board is resumable. Apply processes candidates deterministically in scan order. It publishes a new menu through `ConfigManager.createMenu`, whose repository uses atomic no-clobber creation and persisted-byte validation, then creates the board through `BoardService`; neither source seam overwrites an existing target. Per-candidate failures are retained in the result and later candidates continue. A failed second step leaves an explicit menu-only result that a subsequent identical run resumes by creating only the missing board.

### 10.6 Round-trip editor synchronization

`art.arcane.holoui.editor.sync` owns outbound relay sessions. Session creation snapshots immutable menu, board, and image bytes off gameplay threads, posts them through a bounded Java `HttpClient`, and stores only the relay's server capability in `editor-sync-sessions.json`. Responses require the exact version-1 envelope and JSON content type; redirects are disabled, response streams are bounded by the project cap plus a small envelope, and each whole exchange has a 20-second deadline driven by an independent daemon scheduler that cancels a stalled or trickling body subscription. Connect timeout is 10 seconds, and endpoints are restricted to versioned HTTPS or loopback HTTP. Tokens, editor URLs, and project bodies are never logged.

The store preserves JSON nulls because null board permission and follow fields participate in the canonical revision. Its file and every path ancestor are checked without following symbolic links, writes flush the replacement file and parent directory, POSIX permissions are owner-only where supported, malformed individual entries are quarantined with an operator warning, and a malformed root disables only this optional service. Admission is capped at 32 active sessions, 64 MiB of canonical base plus pending snapshots, and an 80 MiB store file. A relay capability that was created but cannot be stored locally is revoked before the create operation fails. If an atomic replacement becomes visible but its parent-directory flush still fails after retries, new creates and publication pulls pause until restart instead of continuing with divergent memory and disk state; list, status, and remote revoke remain usable.

Automatic polling runs on one daemon scheduler and never reads Bukkit world or entity state. `editorSyncEnabled=false` stops automatic and manual pulls. Transient failures use per-session exponential backoff up to five minutes; manual pull bypasses the delay. Revoke reserves the same in-flight slot as polling, so a capability cannot be applied after successful revocation. Shutdown atomically cancels active network exchanges, rejects late scheduler-publication registrations, and completes already-queued global publication futures exceptionally. It drains for at most 30 seconds; a persistence transaction still running at that deadline keeps its daemon worker only until its durability or recovery continuation finishes and is never interrupted by lifecycle teardown.

`HoloUiPersistenceCoordinator` is the one fair persistence gate shared by `MenuMutationService`, `BoardService`, the menu/image watcher reads, and sync transactions. A sync publication revalidates its optimistic project hash and the exact current menu, image, and board bytes while holding the external write lease. `HoloUiProjectTransaction` then writes staged replacements, original backups, per-file hashes, and a prepared/publishing/published journal; every file and directory transition is flushed. Startup recovery runs before services load, rolls back every uncommitted journal, preserves any target changed independently after staging, archives committed backups, and retains the newest 20 archives.

After disk publication, menu definitions are republished on the global scheduler and the board index is reloaded before the transaction receives a durable committed marker and the relay receives `applied`. The pending acknowledgement and rebuilt actual server snapshot are stored before the relay call, so an acknowledgement failure or restart retries idempotently without incrementing a board revision again. A commit-marker directory flush is retried; if durability remains uncertain, HoloUi sends no acknowledgement, pauses that session, preserves its journal and pending snapshot, and requires startup recovery to select commit or rollback. Conflicts return a freshly rebuilt current snapshot, while rejected editor data never changes the server base.

## 11. Runtime notes

- The `displayEntities` key is a fresh random UUID from `add()`, not the entity's own `uuid` field.
- `PacketUtils` performs no null check on `PacketEvents.getAPI()`, so a send before PacketEvents init or after `terminate()` throws.
- `ToggleComponent` evaluates its placeholder condition only in the constructor; after that the toggle state is local and does not track the underlying value.
