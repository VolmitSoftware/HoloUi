# HoloUi placeholders

HoloUi registers a `%holoui_…%` PlaceholderAPI expansion so a scoreboard, a tab list, a hologram or
another plugin's text can ask whether a player currently has a holographic menu open, and which one.

It is a small surface on purpose: three keys, no per-component detail, and no way to open or close
anything. Anything beyond "is a menu open, and which one" belongs in [the menu API](menus.md), which
gives you a live handle instead of a string.

This document also covers the other direction — what happens to placeholders you write *inside* a menu
definition — because that behaviour has a limitation you will otherwise meet as a bug.

---

## Registration

| Property           | Value                                                          |
|--------------------|-----------------------------------------------------------------|
| Identifier         | `holoui`                                                        |
| Author             | `Volmit Software`                                               |
| Version            | `1.0.0`                                                         |
| Required plugin    | `holoui`                                                        |
| Persists           | Yes — it survives `/papi reload`                                |

HoloUi registers the expansion at the end of its own enable, and only if PlaceholderAPI is already
enabled at that moment. HoloUi soft-depends on PlaceholderAPI, so in normal operation PlaceholderAPI has
loaded first and the registration succeeds. **There is no retry.** If PlaceholderAPI is installed or
enabled after HoloUi, the expansion is absent until HoloUi is reloaded or the server restarts. HoloUi
unregisters the expansion on disable and on reload.

---

## The keys

Paths are dot-separated and lowercase. The lookup lowercases what PlaceholderAPI hands it, so
`%holoui_MENU.OPEN%` resolves the same as `%holoui_menu.open%`.

| Placeholder             | Value                                                                     |
|-------------------------|----------------------------------------------------------------------------|
| `%holoui_available%`    | Always `true`. The expansion only exists while HoloUi is enabled            |
| `%holoui_menu.open%`    | `true` when the player has a holographic menu open, `false` otherwise       |
| `%holoui_menu.id%`      | The id of the menu that player has open, or `---` when there is none        |

That is the complete list. There is no fourth key, and the separator is a dot in every one of them —
`%holoui_menu_open%` is not a spelling variant, it is an unknown path and renders literally.

### Value grammar

Three answers are possible, and they are distinguishable:

- **A path that is not in the table above returns nothing**, so PlaceholderAPI re-emits the literal
  `%holoui_…%` text and a typo stays visible on screen instead of silently rendering as blank.
- **A known path with no value right now returns `---`.**
- **A real value** is plain text: no colour codes, no `§` sequences, no `%` character, and no
  units or padding. Booleans are exactly the lowercase words `true` and `false`.

### What `menu.id` actually names

It is the session id — the same string as `HoloMenuHandle#menuId()` and
`HoloUiMenuOpenEvent#getMenuId()`.

- For a menu opened from `plugins/holoui/menus/`, that is the file's base name: `welcome.json` reads as
  `welcome`.
- For a menu another plugin built in code, it is that plugin's chosen id **after sanitisation** — the
  form filtered to `A-Z a-z 0-9 _ - .` and truncated to 64 characters. A plugin that asked for
  `"example shop"` reads as `exampleshop`.

The value is published the moment the session is created and cleared the moment it ends, so it tracks
opens, replacements and closes within the same tick. It never lingers after a menu closes and there is no
grace window.

Container previews — the inventory HUD HoloUi draws when a player looks at a chest — are not menus. They
never set `menu.open` or `menu.id`. They are JSON documents in `plugins/holoui/previews/` with their own
expression language, which has no access to PlaceholderAPI; see [previews.md](previews.md).

A player with no menu open reads `false` and `---`. So does a request with no player attached at all,
which is what a server-scoped placeholder parse produces; `%holoui_available%` still answers `true`
there, because it describes HoloUi rather than anyone in particular.

### If you are a plugin, do not parse these

The expansion exists for text that admins configure. Java code already has both answers directly, with
no PlaceholderAPI dependency, no string parsing and no `---` sentinel to special-case:

```java
import art.arcane.holoui.api.HoloUiService;
import org.bukkit.entity.Player;

public static boolean hasMenuOpen(HoloUiService holoUi, Player player) {
    return holoUi.isOpen(player);
}
```

And if you opened the menu yourself, `HoloMenuHandle#menuId()` and `HoloMenuHandle#state()` describe your
own session precisely, including the `PENDING` window that no placeholder can express.

---

## Threading

**These three keys are safe to resolve from any thread**, including PlaceholderAPI's async relational
pool, and that claim is not optimism about locking.

Resolving one is a lookup in a `ConcurrentHashMap` keyed by player `UUID`, a comparison against a stored
expiry stamp, and the construction of a short string. Nothing reads a `World`, an `Entity`, a `Location`
or a `Player`; the expansion is handed a `UUID`, never the `OfflinePlayer` PlaceholderAPI passed in. No
scheduler is touched and no lock is taken.

The value a menu publishes is written from the region thread that owns the player, at the instant the
session opens or closes, and read back by whatever thread asks. The map's own visibility guarantees are
what make a read from another thread correct.

---

## Failure policy

| Situation                                   | Result                                                                          |
|---------------------------------------------|----------------------------------------------------------------------------------|
| Unknown path                                | Nothing is returned; PlaceholderAPI re-emits `%holoui_…%` literally               |
| Empty or blank parameters                   | Same — nothing is returned                                                       |
| A resolver throws                           | `---` is returned, and one `WARNING` naming the exact path is logged              |
| The same path keeps throwing                | The warning is logged **once per distinct path**, capped at 64 distinct paths     |
| PlaceholderAPI is missing or disabled       | No expansion exists, so nothing HoloUi owns is resolvable                         |
| HoloUi is disabled or reloading             | The expansion is unregistered; `%holoui_…%` renders literally until it is back    |

A throwing resolver never propagates out of PlaceholderAPI and never takes down the text it appeared in.

---

## Placeholders inside a menu definition

The other direction: a text component in a menu — whether it came from a JSON file or from
`HoloIcon.text(...)` in another plugin — may contain any placeholder, from any expansion, and HoloUi
expands it against the viewing player.

**It is expanded exactly once, when the menu opens, and the result is frozen for that session.**

There is no refresh timer and no per-tick re-expansion. A component that reads
`<gray>Balance: %vault_eco_balance%` shows the balance the player had at the instant they opened the menu
and keeps showing it until the menu closes, no matter what happens to the real balance in between. The
same applies to the condition on a JSON toggle component: it is evaluated when the component is built and
not re-evaluated afterwards.

This is a genuine limitation of menu definitions, not a bug to work around with a shorter tick. What
updates live is the handle:

| To show a value that changes | Use                                                              |
|------------------------------|-------------------------------------------------------------------|
| Text                         | `HoloMenuHandle#setText(componentId, miniMessage)`                 |
| An item                      | `HoloMenuHandle#setItem(componentId, stack)`                       |
| Any icon, including images   | `HoloMenuHandle#setIcon(componentId, icon)`                        |

`setText` expands placeholders again on the string you pass, so pushing
`"<gray>Balance: %vault_eco_balance%"` through the handle on a repeating task does produce a live value.
Reading the number yourself and pushing a plain string is cheaper and easier to reason about.

Two more facts about definition-side expansion:

- **Without PlaceholderAPI installed, nothing is expanded at all** and the player sees the literal
  `%vault_eco_balance%`. Do not put a placeholder in a menu you also expect to look right on a server
  without PlaceholderAPI.
- `%holoui_menu.id%` inside a text component of a menu resolves to that same menu's id, because the
  session publishes its id before any component draws.

Both the JSON side and `HoloIcon.text` treat `\n` as a line break, and each line is expanded separately.
