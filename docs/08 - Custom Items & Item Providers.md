# Custom Items & Item Providers

A HoloUi menu icon can draw an item that belongs to another plugin — an ItemsAdder ruby, an MMOItems
sword, a HeadDatabase head — instead of a vanilla `Material`. Ten adapters resolve a foreign item id
into a Bukkit `ItemStack` on the server at the moment the icon is built, and the stack is sent to the
client as it is, so whatever the host plugin puts on it (model, name, lore, data components) is what
players see. This document also covers the catalog export that feeds the web editor.

Source: `src/main/java/art/arcane/holoui/integration/`.

---

## Provider model

### Detection

Detection is plugin-presence first, class loading second.

`ItemProviderRegistry` holds a fixed list of `ProviderDefinition(pluginName, className)` records. The
class name is a `String`, not a `Class`, because each adapter imports its host plugin's API types
directly; loading an adapter whose host is absent would raise `NoClassDefFoundError`.

`activate(ProviderDefinition)` runs, in order:

1. `Bukkit.getPluginManager().getPlugin(definition.pluginName())`. A `null` plugin, or a plugin that
   is not `isEnabled()`, aborts silently.
2. A duplicate check by plugin name against the already-active set.
3. `Class.forName(definition.className(), true, ItemProviderRegistry.class.getClassLoader())`,
   `asSubclass(ItemProvider.class)`, `getDeclaredConstructor().newInstance()`.
4. The `customItemProviders` allowlist check.
5. Registration, which clears the prototype cache and logs
   `[items] provider <id> active from <pluginName>`.

Any `Throwable` from steps 3–5 is caught and logged as
`[items] failed to activate the <pluginName> item provider:`. The rest of the registry is unaffected.

One adapter performs an additional class probe of its own. `HeadDatabaseItemProvider` resolves
`me.arcaniax.hdb.api.HeadDatabaseAPI` with `initialize = false` inside its constructor, because other
plugins have shipped under the name `HeadDatabase` and the plugin name alone is not proof that the
API exists. An unrelated plugin named `HeadDatabase` therefore produces a logged activation failure
and an `inactive` status row, not a linkage crash.

Every item plugin is listed under `softdepend` in `plugin.yml`. There is no hard dependency on any of
them.

### Activation timing and ordering

- `activateAll()` registers the registry as a `Listener` once — this happens even when custom items
  are disabled, so a later settings change can take effect — then walks the definition list in
  declaration order when `customItems` is true.
- `PluginEnableEvent` activates the matching definition when a host plugin enables after HoloUi. This
  is the normal path for ItemsAdder and HeadDatabase.
- `PluginDisableEvent` removes the matching provider regardless of the `customItems` setting, clears
  the prototype cache, and logs `[items] provider for <pluginName> dropped, it was disabled`.

Declaration order is `CraftEngine`, `ItemsAdder`, `Oraxen`, `Nexo`, `MMOItems`, `ExecutableItems`,
`EcoItems`, `Slimefun`, `MythicMobs`, `HeadDatabase`.

The live provider list is in **activation order**, which equals declaration order only for hosts that
were already enabled when HoloUi enabled. Late enablers are appended in the order their
`PluginEnableEvent` fires. `auto` resolution walks this list, not the declaration list.

### Resolution

`ItemProviderRegistry.resolve(providerId, itemId)`:

- Returns `null` when `itemId` is null or blank, or when `customItems` is disabled.
- `providerId` is normalized: null, blank, or whitespace becomes `auto`; otherwise it is trimmed and
  lowercased. Provider matching is case-insensitive.
- The prototype cache key is `normalizedProvider + " " + itemId`, so a hit under `auto` is cached
  separately from a hit under an explicit provider id.
- Cache hits and misses both return a **clone**, and the cached prototype is itself a clone of what
  the host plugin returned. ItemsAdder and Slimefun hand back live registry instances, so nothing the
  caller receives can mutate a host registry.
- **Negative results are not cached.** An unresolvable id is re-attempted on every icon build.
- `auto` iterates the active list in activation order and takes the first non-null result.

Per-provider guards applied during resolution:

- A `requiresMainThread()` provider is skipped when `!Bukkit.isPrimaryThread()`, with a one-time
  warning per provider id: `[items] provider <id> is main thread only and was skipped off thread`. An
  off-thread caller never blocks a tick; it simply misses that lookup.
- `isReady()` false skips resolution entirely, so a late-loading registry cannot poison the prototype
  cache with a permanent miss.
- Any `Throwable` from `isReady`, `resolve`, or `listIds` is swallowed and reported once per provider
  id: `[items] provider <id> faulted, lookups against it will keep failing:`.

Main-thread and fault warnings are emitted once per provider id, not once per failed lookup.

### Cache and lifecycle

| Method | Effect |
|---|---|
| `activateAll()` | Registers the listener, then activates every allowed definition |
| `reload()` | Clears all providers and re-activates. Bound to changes of `customItems` and `customItemProviders` |
| `invalidate()` | Clears the prototype cache only. Bound to `previewScale` / `uiScale` changes via `HuiSettings.refreshVisuals()` |
| `shutdown()` | Unregisters the listener and clears providers, prototypes, and both one-time-warning sets |

### Provider status

`providerStatuses()` returns one row per **definition** — always ten rows, in declaration order —
built on demand for `/holoui items status` and never on a menu path.

```java
record ProviderStatus(String id, String pluginName, boolean pluginPresent,
                      boolean active, boolean ready, int itemCount)
```

| Field | Meaning |
|---|---|
| `id` | The provider's `id()`; when no provider is active, the plugin name lowercased (every built-in id equals its plugin name lowercased) |
| `pluginName` | The Bukkit plugin name from the definition |
| `pluginPresent` | The plugin is installed **and** `isEnabled()` |
| `active` | An adapter instance is registered for that plugin name |
| `ready` | `active` and `isReady()` returned true |
| `itemCount` | `listIds().size()` when `ready`, otherwise `0`. Enumeration is not free, hence the on-demand build |

The command maps these to four display states: **missing** (`!pluginPresent`), **inactive** (present
but not active — excluded by the allowlist, or activation threw), **loading** (`active && !ready`),
and **ready**. `statusSummary()` returns `custom items disabled`, `0/10 providers active`, or
`N/10 providers active: id, id, ...`.

### When a provider's plugin is absent

Nothing is class-loaded, no adapter exists, `resolve` returns `null` for that provider id, and the
status row reports `pluginPresent=false`. An icon referencing it falls back to the missing-icon
checker. There is no error at startup and no configuration to remove.

### Extending the provider set

The definition list is a private `List.of(...)` and there is no public registration method.
Third-party plugins cannot add an `ItemProvider` at runtime; the list is fixed at compile time.

---

## Providers

All ten adapters clone each successfully resolved `ItemStack` before returning it. Host API calls may throw; `ItemProviderRegistry.resolveFrom` catches those failures, warns once per provider id, and treats that lookup as a miss.

| Provider id | Plugin | Id format in menu JSON | API entry point | `isReady()` | Main thread only |
|---|---|---|---|---|---|
| `craftengine` | `CraftEngine` | `namespace:id`; a bare id is also accepted and resolved by a cross-namespace path search. Passed through verbatim | `CraftEngineItems.byId` → `BukkitItemDefinition.buildBukkitItem()` | always | no |
| `itemsadder` | `ItemsAdder` | `namespace:id` | `CustomStack.getInstance` | `ItemsAdder.areItemsLoaded()` | no |
| `oraxen` | `Oraxen` | bare yml key, no namespace | `OraxenItems.getItemById` → `ItemBuilder.build()` | always | no |
| `nexo` | `Nexo` | bare yml key, no namespace | `NexoItems.itemFromId` → `ItemBuilder.build()` | always | no |
| `mmoitems` | `MMOItems` | `TYPE:ID`, split on the first colon; anything without exactly two halves is a miss | `MMOItems.plugin.getItem(type, id)` | `MMOItems.plugin != null` | **yes** |
| `executableitems` | `ExecutableItems` | bare config id | `ExecutableItemsAPI.getExecutableItemsManager().getExecutableItem(id).buildItem(1, Optional.empty())` | manager non-null | no |
| `ecoitems` | `EcoItems` | `ecoitems:my_item`; a bare id is namespaced for you | `Items.lookup(...).getItem()` | always | no |
| `slimefun` | `Slimefun` | bare `UPPER_SNAKE_CASE` id | `SlimefunItem.getById` (exact map get) | always | no |
| `mythicmobs` | `MythicMobs` | bare item config name, no namespace | `MythicBukkit.inst().getItemManager().getItemStack(id)` | item manager non-null | no |
| `headdatabase` | `HeadDatabase` | numeric head id string, e.g. `7129` | `new HeadDatabaseAPI().getItemHead(id)` | always (see runtime notes) | no |

Providers with no `displayName` override return the id itself.

Case sensitivity is a property of the host plugin, not of HoloUi. HoloUi passes the id through
untouched, the single exception being EcoItems namespacing. Whether `MY_ITEM` and `my_item` are the
same id is decided by the plugin that owns the id.

### Runtime notes

| Provider | Behavior |
|---|---|
| `craftengine` | `listIds()` maps `CraftEngineItems.loadedItems()` keys through `Key.asString()`, so enumeration always yields fully namespaced ids even though bare ids resolve |
| `itemsadder` | Items load asynchronously long after startup. Until `areItemsLoaded()` is true the provider is **active but not ready** and every lookup is skipped. `displayName` comes from `CustomStack.getDisplayName()`; enumeration is `CustomStack.getNamespacedIdsInRegistry()` |
| `oraxen` | Enumeration is `OraxenItems.getNames()` |
| `nexo` | Nexo builds lazily, so a malformed yml entry only throws at `build()`, not at `itemFromId`. That exception is caught and reported as a miss. Enumeration is `NexoItems.itemNames()` |
| `mmoitems` | `requiresMainThread()` is true; off-thread lookups are skipped, not blocked. `has()` goes through `Type.get` plus the template manager. Enumeration is every type × `getTemplateNames(type)`, joined as `TYPE:ID` |
| `executableitems` | The API classes ship inside SCore, which ExecutableItems hard depends on, so the ExecutableItems presence check covers both. Enumeration is `manager.getExecutableItemIdsList()` |
| `ecoitems` | `Items.lookup` never returns null — it returns an empty testable item whose stack is `AIR`, which the adapter treats as a miss. Enumeration filters `Items.getCustomItems()` to the `ecoitems` namespace and re-emits ids as `ecoitems:key` |
| `slimefun` | Enumeration covers **enabled** items only (`Slimefun.getRegistry().getEnabledSlimefunItems()`), so a disabled Slimefun item still resolves in a menu but never appears in the catalog. `displayName` comes from `SlimefunItem.getItemName()` |
| `mythicmobs` | `MythicBukkit.inst()` is null between class load and MythicMobs finishing its own enable, which is what `isReady()` covers. Enumeration is `ItemExecutor.getItemNames()` |
| `headdatabase` | The constructor probes `me.arcaniax.hdb.api.HeadDatabaseAPI` before any HDB type is resolved. HeadDatabase dereferences its head map instead of reporting a miss until the database lands, so `NullPointerException` from `getItemHead` / `isHead` / `getHeads` is caught and treated as "not loaded". Enumeration walks `CategoryEnum.values()` **excluding `ONLINE_PLAYERS` and `DISABLED`**, and returns an empty list until the database has loaded |

---

## The `customItem` icon

`MenuIconType.CUSTOM_ITEM` has the serialized name `customItem` and deserializes into
`CustomItemIconData`. Record component names are the JSON keys verbatim; the Gson instance applies no
naming policy. See "05 - Icons.md" for the other icon types.

```json
{
  "type": "customItem",
  "provider": "itemsadder",
  "item": "myitems:ruby",
  "count": 1
}
```

| Key | Type | Required | Omitted value | Meaning |
|---|---|---|---|---|
| `type` | string | yes | — | Literally `customItem` |
| `provider` | string | no | `null`, treated as `auto` | A provider id, matched case-insensitively after trim and lowercase, or `auto` to try every active provider in activation order |
| `item` | string | yes | — | The provider's own id, verbatim and case preserved |
| `count` | int | no | `0`, rendered as `1` | Stack size; the icon applies `count > 0 ? count : 1` |

The id is passed to the provider unmodified. HoloUi never parses, splits, or case-folds `item` —
splitting on `:` is a provider-level behavior (MMOItems `TYPE:ID`, EcoItems namespacing), not a
registry-level one. `namespace:id` means whatever the named provider means by it.

`item` is deliberately the same key the vanilla `item` icon uses, so the two icon types read alike.

### `auto` resolution

`auto` walks the active provider list in **activation order** and takes the first non-null result.
That order equals the declaration order only when every host plugin was already enabled at HoloUi
enable; hosts that enable later — typically ItemsAdder and HeadDatabase — are appended in enable
order. Name the provider explicitly when the order matters or when more than one provider is
installed.

Each `auto` hit is cached under its own key (`auto <itemId>`), separately from the same item resolved
under an explicit provider id.

### Fallback when unresolvable

Custom item resolution throws `MenuIconException` when the registry returns null, which covers a
disabled integration layer, an unknown provider id, an inactive or not-ready provider, and an unknown
item id. Icon creation catches it and:

1. Logs the exception stack under
   `An error occurred while creating a Menu Icon for the component "<componentId>":`, whose message is
   `Unable to resolve custom item "<item>" from provider "<provider>"`, with `auto` substituted when
   `provider` was null or blank.
2. Logs `Falling back to missing icon.` at `WARNING`.
3. Returns a plain `TextImageMenuIcon` — the missing-icon checker.

Two records are therefore emitted per failure, and the first names the **component id**, not the
provider. The menu itself still opens. Because negative resolutions are not cached, the fallback is
re-evaluated each time the icon is built, so an item becomes visible as soon as its provider is
ready.

---

## Commands

| Command | Permission | Behavior |
|---|---|---|
| `/holoui items status` | `holoui.command.items` | One row per definition: id, plugin, and state (missing / inactive / loading / ready with id count). Rows come from `providerStatuses()`, which enumerates every ready provider |
| `/holoui items export` | `holoui.command.items.export` | Writes the catalog. Refuses when an export is already running |

Both abort with the `ITEMS_DISABLED` message when `customItems` is false. The status output appends a
clickable `/holoui items export` line when the sender also holds the export permission.

Counting ids is the one place enumeration cost is paid; it is never paid while a menu is open.

`export` is asynchronous and reports back long after the command has returned, so it plays its own
success or error sound and re-dispatches its completion message onto the sender's scheduler
(`SchedulerUtils.runEntity` for players, `runGlobal` otherwise). See "02 - Commands & Permissions.md".

---

## The custom item catalog

`CustomItemCatalogWriter` writes `custom-items.json` into the plugin data folder — the same directory
as `settings.json`, so `plugins/holoui/custom-items.json`. Its consumer is the web editor at
`https://holoui.volmitsoftware.com` (the `builderUrl` setting default, linked by `/holoui builder`),
which has no connection to the server and must be handed the file by hand:
**Settings → Import custom item catalog**. Without a catalog the editor's custom item field degrades
to free text. See "12 - Web Editor & Schemas.md".

The editor cannot verify ids offline. With a catalog loaded it can autocomplete and report that an id
is not in it; without one it accepts anything typed. Either way the id is only really checked by the
server when the menu opens, and an unknown id is a console warning and a checkered icon, never a
broken menu.

### Shape

Serialized by Gson with pretty printing from the `CustomItemCatalog` record. Component names are the
JSON keys the editor reads.

```json
{
  "version": 1,
  "generated": 1730000000000,
  "providers": ["itemsadder"],
  "items": [
    { "provider": "itemsadder", "id": "myitems:ruby", "name": "Ruby", "material": "diamond" }
  ]
}
```

| Field | Source |
|---|---|
| `version` | `CustomItemCatalog.VERSION`, currently `1` |
| `generated` | `System.currentTimeMillis()` at export |
| `providers` | Provider ids that contributed **at least one** entry. A ready provider whose ids all failed to resolve is absent |
| `items[].provider` | `ItemProvider.id()` |
| `items[].id` | The provider's own id, trimmed, case preserved |
| `items[].name` | `ItemProvider.displayName(id)` with legacy section colors stripped (`(?i)§[0-9a-fk-orx]`) and trimmed; falls back to the id when null, blank, or throwing |
| `items[].material` | `stack.getType().getKey().getKey()` — the vanilla key **path only**, lowercase, with the `minecraft:` namespace stripped — so the editor can draw an approximate sprite |

### Harvest rules

- Providers are visited in active (activation) order. Ids within a provider are sorted with
  `String::compareTo` before probing, so repeat exports of an unchanged server are byte-identical and
  truncation is deterministic.
- Every id is probed by calling `provider.resolve(id)` **directly**, bypassing the registry so the
  icon prototype cache is not filled with ids nobody opened. A null stack, an `AIR` stack, or a thrown
  exception drops the id and increments `discarded`. The catalog therefore never advertises an id that
  would render as the missing-icon checker.
- `MAX_ITEMS_PER_PROVIDER = 10000` caps **accepted entries** per provider; discarded ids do not
  consume the budget, so a provider can be probed well past 10000 ids before the cap is reached. On
  reaching the cap the writer logs
  `[items] provider <id> exposes <n> ids, the catalog keeps the first <max>` and stops that provider.
  Truncated ids still resolve in a menu; they are only absent from editor autocomplete. HeadDatabase
  is the provider that hits this in practice.
- Providers that are not ready contribute nothing.

### Threading and concurrency

- `exportAsync(callback)` guards on a process-wide `AtomicBoolean` — one export at a time for the
  whole server — and returns `false` when an export is already running or `SchedulerUtils.runAsync`
  could not schedule. The callback runs on the async thread.
- `export()` runs on the calling thread. Providers that declare `requiresMainThread()` are hopped onto
  the global scheduler for the duration of their enumeration only, with a 30 second join. A scheduling
  failure or a timeout excludes that provider from the catalog and logs it; everything else stays
  asynchronous.
- On success the writer logs
  `[items] catalog written to <path> providers=<n> items=<n> discarded=<n>`.

### Result

```java
record CustomItemCatalogResult(boolean success, int providerCount, int itemCount,
                               int discarded, String path)
```

`success` is false only when the file could not be written. `itemCount == 0` with `success == true` is
reported separately by the command as an empty export.

---

## Settings

`plugins/holoui/settings.json`:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `customItems` | boolean | `true` | Master switch. False makes `resolve` return null, blocks provider activation, and disables both `/holoui items` subcommands |
| `customItemProviders` | string | `""` | Comma-separated allowlist, empty meaning every provider. Entries are trimmed and lowercased and are matched against **either** the provider id **or** the plugin name, so `ItemsAdder` and `itemsadder` are equally valid |

Both entries call `ItemProviderRegistry.reload()` on change. The settings file is polled every 5
ticks behind a `FileWatcher`, so edits apply without a restart. Settings are written back only during
a clean shutdown, so a key introduced by a new version does not appear in an existing `settings.json`
until the server stops cleanly; add it by hand to use it before then. See
"01 - Installation & Configuration.md".

---

## Related preview access control

Custom item providers do not participate in container-preview authorization. The permission, physical-openability, lock, WorldGuard, and access-event sequence is documented once in `09 - Container Previews.md`; the public `HoloUiContainerPreviewAccessEvent` contract is in `16 - API - Previews.md`.
