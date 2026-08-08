# API - Previews

Container previews expose two integration points in `art.arcane.holoui.api`: `PreviewStateProvider`, which contributes named variables that preview documents read, and `HoloUiContainerPreviewAccessEvent`, the last gate before HoloUi builds a preview for a viewer. This document defines those public contracts and links to the canonical built-in variable and access-control references in `09 - Container Previews.md`.

The preview document format and expression language they feed are covered in `09 - Container Previews.md`.

---

## `PreviewStateProvider`

```java
public interface PreviewStateProvider {
  String namespace();
  Map<String, Object> snapshot(Block block, Entity entity, Player player);
}
```

`Block`, `Entity` and `Player` are `org.bukkit.block.Block`, `org.bukkit.entity.Entity` and `org.bukkit.entity.Player`. Every entry the provider returns is published to preview expressions as `namespace() + "." + entryKey`, so a provider with namespace `"adapt"` returning `{"level": 12}` publishes `adapt.level`.

Contract, as implemented in `PreviewStateContext#mergeProviders`:

- `snapshot` runs on the region thread that owns the preview target, at most once per game tick per preview — the snapshot map is cached by tick. Bukkit access is safe; expensive work is not.
- `block` is null when previewing an entity or a bare inventory. `entity` is null when previewing a block or a bare inventory. `player` is null when the preview has no viewer context.
- Values are narrowed to the expression runtime's types: `Double`, `String` and `Boolean` pass through, any other `Number` becomes a `Double`, and anything else is dropped. A null key or a null narrowed value is dropped.
- Returning `null` or an empty map contributes nothing.
- A namespace that is null, blank, or reserved by a built-in is dropped whole — not partially merged — and warned about once.
- A provider that throws a `RuntimeException` from `namespace()` or `snapshot()` is skipped for that sample and warned about once, keyed by namespace, or by the implementing class name when `namespace()` itself threw. It cannot take a preview down.

Reserved namespaces (`PreviewStateAdapters.RESERVED_NAMESPACES`) are `vars`, every full built-in variable name, and the first segment of every dotted built-in name — that is, `inventory` and `surge`. The same set is what a document's `repeat.var` may not collide with.

---

## `PreviewStateProviders`

```java
public final class PreviewStateProviders {
  public static void register(PreviewStateProvider provider);
  public static void unregister(PreviewStateProvider provider);
  public static List<PreviewStateProvider> all();
}
```

| Method | Behaviour |
|---|---|
| `register` | `addIfAbsent` on the backing list, so registering the same instance twice is a no-op. A null provider throws `NullPointerException("provider")` |
| `unregister` | Removes the instance. A null argument and an unknown instance are both ignored |
| `all()` | Unmodifiable view, in registration order. Iterated on region threads during every preview snapshot |

The registry is a `static` `CopyOnWriteArrayList` on the `PreviewStateProviders` class. It lives for the lifetime of the JVM and is not tied to any plugin, so HoloUi does not and cannot clear your entry when your plugin disables. **Unregister on disable**, or you retain your provider — and through it your classloader — across every reload.

This is the one thing a HoloUi consumer must clean up. Menus need no cleanup: HoloUi listens for `PluginDisableEvent` and closes every session whose owner name matches the disabling plugin.

```java
package com.example.charge;

import art.arcane.holoui.api.PreviewStateProvider;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ChargeProvider implements PreviewStateProvider {

  @Override
  public String namespace() {
    return "myplugin";
  }

  @Override
  public Map<String, Object> snapshot(Block block, Entity entity, Player player) {
    if (block == null) {
      return Map.of();
    }

    return Map.of("charge", chargeOf(block));
  }

  private double chargeOf(Block block) {
    return block.getBlockPower();
  }
}
```

```java
package com.example.charge;

import art.arcane.holoui.api.PreviewStateProviders;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChargePlugin extends JavaPlugin {
  private final ChargeProvider chargeProvider = new ChargeProvider();

  @Override
  public void onEnable() {
    PreviewStateProviders.register(chargeProvider);
  }

  @Override
  public void onDisable() {
    PreviewStateProviders.unregister(chargeProvider);
  }
}
```

---

## How provider variables surface in documents

`PreviewStateContext` is the scope a preview document is evaluated against. Names resolve in this order:

1. A repeat element wraps the context in a `RepeatScope`, which answers its own loop variable name exactly and delegates everything else to the parent.
2. Names beginning with `vars.` read the document's injected constant map — `match.vars` with the first matching variant's `vars` merged over it. Variables are reachable only under that prefix, so a `vars.` name can never shadow, or be shadowed by, a state name.
3. Everything else reads the sampled snapshot: the built-in adapter variables, plus every registered provider's entries merged flat as `<namespace>.<key>`.

The snapshot is sampled lazily on the first lookup and re-sampled whenever the tick changes, so one refresh reads each Bukkit getter and calls each provider exactly once no matter how many expressions reference them. The tick is `world.getGameTime()`, or `System.currentTimeMillis() / 50` when the context has no world. The tick and the map it produced are published together in one immutable record behind a single `volatile` field, so a reader can never pair one sample's tick with another sample's map.

Compile-time name checking (`PreviewDocumentParser.checkVariableName`):

| Reference | Result |
|---|---|
| A cataloged built-in name | Accepted |
| `vars.<declared>` | Accepted |
| `vars.<undeclared>` | Compile error `unknown variable: vars.<name>` |
| A bare name in the enclosing repeat scope | Accepted |
| Any other bare name | Compile error |
| A dotted name whose prefix is reserved | Compile error |
| A dotted name whose prefix is not reserved | `WARNING` `references provider namespace '<name>', not verifiable at parse time`; compiled, resolved at runtime |

A document referencing `myplugin.charge` therefore compiles with a warning even when your plugin is absent. At render, an unresolved name throws `unknown variable: myplugin.charge`; that element is skipped and the rest of the document still draws, with the message logged at most once per document per 60 seconds.

Preview expressions never resolve PlaceholderAPI placeholders — the lexer has no `%…%` form, and a bare `%` is the modulo operator. `PreviewStateProvider` is the supported route for getting external data into a preview. See `15 - API - Placeholders.md` for where placeholders do apply, and `07 - Expressions & Placeholders.md` for the expression grammar.

---

## Built-in namespace constraints

Every context publishes the `universal` group. It additionally publishes the `inventory` group when the target has an inventory and one category group selected from the target type. All full built-in names and the first segment of dotted built-ins are reserved, so providers cannot claim `inventory`, `surge`, or another built-in namespace.

The canonical built-in variable table, including types, fallback values, and category-selection rules, is in [09 - Container Previews.md](09%20-%20Container%20Previews.md#43-variable-catalog). The machine-readable copy is `src/test/resources/preview-variables.json`.

---

## `HoloUiContainerPreviewAccessEvent`

```java
public final class HoloUiContainerPreviewAccessEvent extends Event implements Cancellable {
  public HoloUiContainerPreviewAccessEvent(Player player, Block block);
  public HoloUiContainerPreviewAccessEvent(Player player, Entity entity);
  public Player getPlayer();
  public Block getBlock();
  public Entity getEntity();
  public boolean isCancelled();
  public void setCancelled(boolean cancel);
  public HandlerList getHandlers();
  public static HandlerList getHandlerList();
}
```

The event extends `org.bukkit.event.Event` directly with its own `HandlerList` and is constructed with the no-arg `Event()` superconstructor, so it is a synchronous event — which on Folia means "on whichever region thread fired it", not "on the main thread".

**Block and entity are exclusive.** The block constructor sets `entity` to null; the entity constructor sets `block` to null. Exactly one of `getBlock()` / `getEntity()` is non-null in every dispatch. Both constructors reject a null `player`, and each rejects a null target, with `NullPointerException`.

### The gates that precede it

The event is fired from `ContainerProtectionService#canAccess`, the last step of `ContainerPreviewAccess.canOpen`, on the region thread that owns the target. Viewer permission and the active protection provider must already have allowed the request; block targets must also pass the physical-openability and lock checks for both halves of a double chest. The complete gate order and WorldGuard behavior are in [09 - Container Previews.md](09%20-%20Container%20Previews.md#5-access-control).

### Cancelling

Cancelling denies the preview. HoloUi builds the `locked` document instead of the container's contents, so the viewer sees an unframed padlock card and no inventory contents. Because locked previews are constructed with `showsContents = false`, that card renders even for a viewer with no `holoui.preview` permission at all, as long as `previewEnabled` is true.

Access is re-checked every 10 ticks. If the decision flips while the viewer is looking, the session closes and the raycast loop rebuilds it down the other path on the next tick.

HoloUi performs every one of these checks before it builds any preview document or reads a single inventory slot, and it never synthesizes player-interact or inventory-open events while a player looks at a container.

### Deny on throw

`canAccess` wraps the provider call and the event dispatch in one `try`. A `ReflectiveOperationException`, `RuntimeException` or `LinkageError` from either logs once at `SEVERE` — `"Container access protection failed. Previews will remain locked until the protection provider recovers."` — and **returns false**, denying the preview. The log latch is a single `AtomicBoolean`, so a persistent failure produces one line rather than one per look.

```java
package com.example.protection;

import art.arcane.holoui.api.HoloUiContainerPreviewAccessEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PreviewGate implements Listener {
  private final MyProtection myProtection;

  public PreviewGate(MyProtection myProtection) {
    this.myProtection = myProtection;
  }

  @EventHandler(ignoreCancelled = true)
  public void onPreviewAccess(HoloUiContainerPreviewAccessEvent event) {
    if (event.getBlock() != null && myProtection.denies(event.getPlayer(), event.getBlock())) {
      event.setCancelled(true);
    }
  }
}
```

`ContainerProtectionProvider` is internal and has no registration API. `HoloUiContainerPreviewAccessEvent` is the supported extension point: the installed provider runs first, so an event listener can deny access that it allowed but cannot override a provider denial.
