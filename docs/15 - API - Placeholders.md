# API - Placeholders

HoloUi publishes a `%holoui_…%` PlaceholderAPI expansion with three keys, so a scoreboard, tab list, hologram or another plugin's configured text can ask whether a player has a holographic menu open and which one it is. This document covers that expansion for external consumers, and the separate question of what happens to placeholders written inside HoloUi's own JSON.

---

## The expansion

`art.arcane.holoui.service.HoloUiPlaceholderExpansion` extends VolmLib's `art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion`, which extends `me.clip.placeholderapi.expansion.PlaceholderExpansion` and overrides `onRequest` only.

| Property | Value |
|---|---|
| Identifier | `holoui` |
| Author | `Volmit Software` |
| Version | `1.0.0` |
| Required plugin | `holoui` |
| `persist()` | `true` — the expansion survives `/papi reload` |
| `getPlaceholders()` | `available`, `menu.id`, `menu.open`, sorted |

The expansion does not implement `Relational`. PlaceholderAPI's relational path never invokes it.

---

## The keys

`onRequest` lower-cases the request path with `Locale.ROOT` and matches it exactly against the key map. HoloUi registers no group resolvers, so there are no arguments, no tails and no `.*` wildcard forms — the three keys below are the entire surface.

| Placeholder | Arguments | Output |
|---|---|---|
| `%holoui_available%` | none | Always `true` |
| `%holoui_menu.open%` | none | `true` when the player has an open menu session, `false` otherwise |
| `%holoui_menu.id%` | none | The open session's menu id, or `---` when there is none |

Separators are dots. `%holoui_menu_open%` is not a spelling variant — it is an unknown path, and PlaceholderAPI re-emits it literally.

Values are plain text. Booleans are exactly the lowercase words `true` and `false`. There are no colour codes, no `§` sequences, no `%` characters, no units and no padding.

### What `menu.id` names

It is the session's menu id — the same string as `HoloMenuHandle#menuId()` and `HoloUiMenuOpenEvent#getMenuId()`.

- A menu opened from `plugins/holoui/menus/` reads as the JSON file's base name: `welcome.json` is `welcome`.
- A menu another plugin built through the API reads as that plugin's chosen id after `HoloText.sanitizeId`: filtered to `A-Z a-z 0-9 _ - .` with every other character dropped, then truncated to 64 characters. A plugin that asked for `"example shop"` reads as `exampleshop`.

The value passes through `PlaceholderValues.text`, which strips every `%` and every `§` (along with the character following a `§`) and returns `---` when the input, or the stripped result, is null or empty.

Container previews are not menus. They never set `menu.open` or `menu.id`; see `09 - Container Previews.md`.

---

## Resolution and threading

All three keys are safe to resolve from any thread.

Backing state is a single `PlayerSnapshotStore<String>` keyed by player `UUID`. Resolving `menu.open` or `menu.id` is one `ConcurrentHashMap` lookup, a comparison against the entry's stored expiry stamp, and the construction of a short string. `available` is registered as `playerId -> PlaceholderValues.TRUE`: a constant resolver that reads no map, compares no stamp and constructs no string.

Nothing reads a `World`, an `Entity`, a `Location` or a `Player`. The resolver is handed a `UUID`, never the `OfflinePlayer` PlaceholderAPI passed in. No scheduler is touched and no lock is taken.

`PlayerSnapshotStore` supports a grace window (`DEFAULT_GRACE_MS = 60_000`), but HoloUi never calls `evictAfterGrace`, so every entry's `expiresAtMs` stays `0` and no entry ever expires on its own. The id is published immediately before the session is constructed and removed when the session detaches, both within the same tick, from the region thread that owns the player. The map's own visibility guarantees make a read from another thread correct.

---

## Failure policy

| Situation | Result |
|---|---|
| `params` is null or blank | Returns `null`; PlaceholderAPI re-emits `%holoui_…%` literally |
| Unknown path, including `menu_open` with an underscore | Returns `null`; re-emitted literally |
| A resolver throws | Returns `---` and logs one `WARNING` naming the exact path, with the throwable |
| The same path keeps throwing | Logged once per distinct path, and only while fewer than 64 distinct paths have been logged |
| No player attached | `available` is `true`, `menu.open` is `false`, `menu.id` is `---` |
| PlaceholderAPI missing or disabled | No expansion exists, so nothing HoloUi owns is resolvable |
| HoloUi disabled or reloading | The expansion is unregistered; `%holoui_…%` renders literally until it is back |

A throwing resolver never propagates out of PlaceholderAPI and never takes down the text it appeared in.

---

## Registration and the installer

`HoloUiPlaceholderInstaller.install(registration, openMenus, logger)` is one line:

```java
registration.register(() -> new HoloUiPlaceholderExpansion(openMenus, logger));
```

It is a pure local registration. It downloads nothing, contacts no PlaceholderAPI eCloud, issues no `%papi% download`, and creates no files.

`PlaceholderRegistration#register`:

1. returns `true` immediately when an expansion is already registered;
2. returns `false` when `Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")` is false;
3. builds the expansion — a `SEVERE` log and `false` if the factory throws — then calls `PlaceholderExpansion#register()`, logging `WARNING "PlaceholderAPI rejected expansion holoui"` if PlaceholderAPI refuses.

`HoloUI#onEnable` calls the installer once, last, and only when `PlaceholderRegistration.isPlaceholderApiEnabled()` is already true. There is no retry and no scheduled re-attempt: if PlaceholderAPI enables after HoloUi, the `%holoui_…%` expansion is absent until HoloUi reloads or the server restarts.

`HoloUI#drain` — reached from `onDisable` and from the BileTools `onPreUnload` hook, one-shot through an `alreadyDrained` flag — calls `PlaceholderRegistration#unregister`.

`plugin.yml` lists `PlaceholderAPI` under `softdepend`.

---

## Placeholders inside HoloUi JSON

Only two fields in a menu definition receive placeholder expansion. Actions, commands, item icons, image icons and component ids are never expanded, and preview documents never call PlaceholderAPI at all. `07 - Expressions & Placeholders.md` covers both substitution systems in full.

| Field | Class | Substitution |
|---|---|---|
| Text icon `text` | `TextMenuIcon` (`TextIconData`) | `Placeholders.setPlaceholders(player, line)` per `\n`-separated line, before `TextUtils.parse` |
| Toggle `condition` | `ToggleComponent` (`ToggleComponentData`) | `Placeholders.setPlaceholders(player, condition)`, then `.equalsIgnoreCase(expectedValue)` |

### Mechanism

`art.arcane.volmlib.util.bukkit.Placeholders#setPlaceholders` reflectively invokes `me.clip.placeholderapi.PlaceholderAPI#setPlaceholders(Player, String)`. It returns the input unchanged when the player is null, when the text contains no `%`, or when the method cannot be resolved. The reflective lookup is re-probed every 1000 ms, so definition-side expansion begins working within about a second of PlaceholderAPI enabling — unlike HoloUi's own expansion registration, which has no retry. A failed invocation logs one `WARNING` for the JVM lifetime and serves the text unresolved.

### Timing

| Site | Expanded |
|---|---|
| Visible text icon | During construction and every resolved `refreshTicks`; omission is `10`, `0` disables later refreshes |
| Toggle `condition` | During the `ToggleComponent` constructor, once per session |
| `HoloMenuHandle#setText` / text `setIcon` | When applied, then on later refresh intervals using the replacement source |

Only text containing a paired `%name%` token performs periodic work. A changed resolved value updates display metadata in place when line count is stable; a changed line count respawns that text icon, and either change refreshes automatic hitbox geometry. API text uses the 10-tick default because `HoloIcon.Text` has no refresh-interval field.

### Toggle conditions

`ToggleComponent`'s constructor evaluates `state = isValid()`, which is:

```java
Placeholders.setPlaceholders(session.getPlayer(), condition).equalsIgnoreCase(expected)
```

The comparison is case-insensitive against the JSON field `expectedValue`, and a `condition` containing no `%` never reaches PlaceholderAPI at all — it is compared as a literal string. The condition is evaluated once, in the constructor, and never re-evaluated.

### `%holoui_menu.id%` inside a menu

`SessionHolder` calls `openMenus.publish(playerId, data.getId())` *before* it constructs the `MenuSession`, so the id is already readable while components are being built.

That matters for toggles, the only components that resolve placeholders during construction: `ToggleComponent`'s constructor evaluates `condition` and eagerly builds `trueIcon` and `falseIcon` through `MenuIcon.createIcon`. A `%holoui_menu.id%` in any of the three expands to the menu being opened, and `%holoui_menu.open%` there reads `true`. Non-toggle components, whose icons are built in `MenuComponent#open` during `session.open()`, resolve later still and see the same value.

Detaching the previous session published `null` earlier in the same method, so a toggle never reads the id of the menu it is replacing. When a component constructor throws, the id is unpublished before the exception propagates: a failed open leaves `menu.id` at `---` and `menu.open` at `false` rather than stranding the id of a session that does not exist.

---

## Do not parse these from Java

The expansion exists for text an admin configures. Java code has both answers directly, with no PlaceholderAPI dependency, no string parsing and no `---` sentinel to special-case.

```java
package com.example.menus;

import art.arcane.holoui.api.HoloUiService;
import org.bukkit.entity.Player;

public final class MenuQuery {

  private MenuQuery() {
  }

  public static boolean hasMenuOpen(HoloUiService holoUi, Player player) {
    return holoUi.isOpen(player);
  }
}
```

If you opened the menu yourself, `HoloMenuHandle#menuId()` and `HoloMenuHandle#state()` describe your own session precisely, including the `PENDING` window no placeholder can express. To surface external data inside a container preview, register a `PreviewStateProvider`; see `16 - API - Previews.md`.
