# Container previews

A container preview is the holographic card HoloUi draws when a player looks at a chest, furnace, hive,
cauldron, jukebox, minecart or shelf. Previews are **not menus** — they never open a session, never set
`%holoui_menu_open%`, and the `art.arcane.holoui.api` menu types have nothing to do with them.

Every preview is a JSON document. The shipped defaults are extracted to `plugins/holoui/previews/` on
first start; editing one takes effect within a few ticks, no reload command and no restart.

| Path                          | Contains                                                       |
|-------------------------------|----------------------------------------------------------------|
| `plugins/holoui/previews/*.json` | Every preview document, shipped and user-authored           |
| `schema/holoui-preview.schema.json` | JSON Schema for the format, documentation grade         |

The Java parser (`PreviewDocumentParser`) is the format's source of truth. The schema documents it; it
does not define it.

---

## Document structure

```json
{
  "match":    { "blocks": ["CHEST"], "priority": 10, "vars": { "accent": "#F2A535" } },
  "variants": [ { "blocks": ["TRAPPED_CHEST"], "vars": { "accent": "#EC6464" } } ],
  "card":     { "title": "'&f&l' + plain(lang('holoui.preview.theme.title.chest'))", "accent": "vars.accent" },
  "elements": [ { "type": "slot", "x": 0, "y": 0, "size": 18, "index": 0 } ]
}
```

### match

| Key        | Type       | Meaning                                                                        |
|------------|------------|---------------------------------------------------------------------------------|
| `blocks`   | string[]   | Block materials this document draws. `*` is the only wildcard: `"*_SHULKER_BOX"` |
| `entities` | string[]   | Entity types, same matching rules                                               |
| `special`  | string     | `enderChest`, `locked` or `anyInventoryHolder` — see below                       |
| `priority` | int        | Default `0`. Shipped documents use `10`                                          |
| `vars`     | object     | Constants the document reads as `vars.<name>`                                    |

Resolution: **highest `priority` wins**. Within one priority an exact name beats a glob, which beats the
`anyInventoryHolder` fallback. A genuine tie is broken by document name (stable across restarts) and
warned about once. To override a shipped preview, drop a file next to it with a higher `priority`; to
preview a block nothing claims, name its material in a document — in `match.blocks` or in any variant's
`blocks`, which count the same for resolution.

A block is only raycast-eligible when some document names its material, in the base match or in a
variant. `anyInventoryHolder` contributes no materials — it exists for inventory-holding carts and
boats.

The three `special` markers name documents the plugin looks up by role rather than by target:

- `enderChest` — drawn from the *viewer's own* ender chest, not from a tile entity. The session takes
  this path when the document that wins the block resolution carries the marker, so a higher-priority
  user document naming `ENDER_CHEST` gets the ordinary block path instead.
- `locked` — the target-less card shown when a viewer may not open the container.
- `anyInventoryHolder` — the entity fallback.

### variants

A variant reuses the match shape but only `blocks`/`entities` and `vars` are read. Its vars merge over
the document's own and the element templates are unchanged, so one `furnace.json` draws a furnace, a
blast furnace and a smoker from the same geometry with three palettes.

**Variants extend matching as well as restyling it.** The registry grades a target against the base
match *and* every variant, so a material named only in a variant makes the whole document resolvable
for it — it does not also have to appear in `match.blocks`. `chest.json` is the shipped example: its
base match names only `CHEST`, `TRAPPED_CHEST` and `BARREL`, while every shulker box and the
`*COPPER_CHEST` glob live in variants and are matched through them.

Variants are tried in declaration order and the first match wins, so an earlier variant takes an
overlap. A target no variant claims gets the document's own vars.

### vars

Values are JSON primitives, converted to double / boolean / string. **A string var is never parsed as an
expression** — `vars.titleKey` is that exact literal. The one exception is a string leading with `#`,
which is read as a colour literal so `"accent": "#FFB02E26"` arrives as the unsigned ARGB number JSON
cannot express without losing the alpha byte to a signed int. A leading `#` that is not a valid literal
fails to compile rather than silently rendering as text. A MiniMessage tag like `"<#F2A535>"` does not
lead with `#`, so it stays text.

### card

| Key            | Type              | Default | Meaning                                     |
|----------------|-------------------|---------|----------------------------------------------|
| `framed`       | bool / expression | `true`  | Draw the chrome at all                       |
| `title`        | expression        | none    | Title text; evaluated once when the preview opens |
| `accent`       | expression        | grey    | Chrome accent colour, low 24 bits only; evaluated once |
| `minHalfWidth` | int               | `82`    | Minimum panel half-width in pixels           |

Omit the whole `card` object for bare content with no chrome. Declaring one at all means asking for the
chrome, which is why `framed` defaults to true inside it.

A framed card emits its chrome first, so a built card reads: frame, panel, tray (only when the content
has at least one cell or slot), title bar, title, then your elements.

### elements

Drawn in list order. `x` is pixels right of centre, `y` is pixels up from centre, `z` is depth (higher
draws in front).

Every type accepts `x`, `y`, `z`, `visible` and `repeat`.

| `type`  | Required                 | Also accepts                          | Default `z` |
|---------|--------------------------|----------------------------------------|-------------|
| `panel` | `width` `height` `color` | —                                      | `1`         |
| `cell`  | `size` `color`           | —                                      | `4`         |
| `slot`  | `size` `index`           | `wellColor` (default `#FF15151B`)      | `4`         |
| `label` | `text`                   | `background` (default transparent)     | `6`         |

`slot` renders the item in inventory slot `index`. Nothing clamps the index — guard it against
`inventory.size` yourself, as the shipped documents do.

Every numeric, colour and boolean field accepts either a JSON constant or an expression string.
Constants are folded at compile time. Of the expressions, **only `cell.color` and `label.text` are
live** — everything else, including `visible` and `repeat.count`, is evaluated once when the preview
opens. See [What is live](#what-is-live).

### repeat

```json
{ "type": "cell", "repeat": { "count": "vars.segments", "var": "i" }, "x": "-24 + i * 7", "y": 10, "size": 5, "color": "..." }
```

`count` may be an expression, so a grid sizes itself from `inventory.size`. It is evaluated once when
the preview opens, not per frame.

Limits differ by how the count is written. A **constant** count above 1024 is a compile error and the
document is rejected; so is a compiled total above 4096 expanded elements. A **dynamic** count is not
knowable at compile time, so it is truncated to 1024 at build with an error reported against the
document.

`var` defaults to `i`, must be a valid identifier, and may not collide with `vars` or a state variable
name — such a name would resolve to the state namespace and the loop variable would be unreachable, so
it fails to compile.

---

## Expression DSL

One expression per field. Hand-written parser and evaluator; no scripting engine, no sandbox escape.

**Literals** — numbers; strings, single- or double-quoted (`'idle'` and `"idle"` are the same, and
single quotes read better inside JSON); colours `#RGB`, `#RRGGBB`, `#AARRGGBB`; `true`/`false`; array
literals `[a, b, c]` (for `palette`).

Inside a string the escape set is `\\`, `\'`, `\"` and `\n`. Any other escape is a parse error, so
a lone backslash must be written `\\`. Remember JSON eats one level of backslash first: a document
field holding `'a\\nb'` is written `"'a\\\\nb'"` in the file.

`#RGB` and `#RRGGBB` are opaque — the parser prefixes `FF`. Only `#AARRGGBB` carries its own alpha, and
only a bare JSON number can express a colour whose alpha byte is zero.

**Operators**, lowest to highest precedence:

```
a ? b : c
||
&&
==  !=
<  <=  >  >=
+  -
*  /  %
!  -   (unary)
```

`+` concatenates when either side is a string.

`%` and `mod` are **not** the same. `%` is Java's truncating remainder, so it keeps the sign of the left
operand: `-1 % 3` is `-1`. `mod(a, b)` uses floor semantics and always returns a non-negative result for
a positive divisor: `mod(-1, 3)` is `2`. Use `mod` for wraparound on a value that can go negative — a
chase index, a palette cycle, a grid column — and `%` only when a negative result is what you want. Both
throw on a zero divisor. Both behaviours are pinned in `src/test/resources/expr_test_vectors.json`.

**Variables** are dotted names: `cookTime`, `surge.active`, `inventory.size`, `vars.accent`. A name that
resolves to nothing is an error at evaluation, reported against the document and logged at most once a
minute per document; that element is skipped and the rest of the preview still draws.

### What is live

Exactly two fields are re-evaluated while a preview is on screen, every four ticks:

- `cell.color`
- `label.text`

Everything else is evaluated **once**, when the preview is built: `x`, `y`, `z`, `width`, `height`,
`size`, `index`, `panel.color`, `slot.wellColor`, `label.background`, `visible`, `repeat.count`, and the
card's `framed`, `title` and `accent`. An element hidden by `visible` at build time stays hidden for the
life of that preview, and a grid does not resize itself as items move.

That is a hard constraint on how a document animates: anything that must change on screen has to be
expressed as a cell colour or a label string. A folded-constant `cell.color` or `label.text` is
evaluated once too — a constant label is parsed a single time and the same component handed back on
every poll.

A preview is rebuilt from scratch when the access decision changes or the document is edited, which is
when the once-only fields are recomputed.

### Functions

| Function                     | Returns | Notes                                                             |
|------------------------------|---------|--------------------------------------------------------------------|
| `clamp(x, lo, hi)`           | number  |                                                                   |
| `lerp(a, b, t)`              | number  | Not clamped                                                        |
| `min(a, b)` `max(a, b)`      | number  | Exactly two arguments                                              |
| `floor(x)` `ceil(x)` `round(x)` `abs(x)` | number |                                                     |
| `mod(a, b)`                  | number  | Floor semantics; throws on `b == 0`                                |
| `sin(x)` `cos(x)`            | number  | Radians                                                            |
| `rgb(r, g, b)`               | colour  | Opaque; channels clamped to 0..255                                 |
| `argb(a, r, g, b)`           | colour  |                                                                   |
| `alpha(color, a)`            | colour  | Replaces the alpha byte                                            |
| `mix(c1, c2, t)`             | colour  | Per-channel; `t` clamped to 0..1                                   |
| `palette([c, ...], index)`   | colour  | Index wraps; list must be non-empty numbers                        |
| `str(x)`                     | string  | `42.0` stringifies as `42`                                         |
| `fixed(x, digits)`           | string  | `digits` a whole number in 0..20                                   |
| `plain(text)`                | string  | Strips legacy `&` codes, leaves everything else                    |
| `readable(id)`               | string  | `IRON_ORE` becomes `Iron Ore`                                      |
| `lang(key, ...)`             | string  | Localized message; positional args fill its placeholders in order  |
| `count(slot)`                | number  | Item count in an inventory slot, `0` when empty or out of range    |
| `occupied(slot)`             | boolean | Slot holds a non-empty stack                                       |
| `item(slot)`                 | string  | Material id in a slot, empty string when empty or out of range     |

`lang` binds positional arguments onto the resolved key's own placeholder names — argument 1 fills the
first `{name}` in the English template, argument 2 the second. Values are inserted as untrusted text, so
a player-set container name cannot smuggle colour codes into a title. An unknown key renders as itself.

---

## Variables

Always available:

| Name         | Type    | Meaning                                                                  |
|--------------|---------|---------------------------------------------------------------------------|
| `time`       | number  | World game time in ticks (a wall-clock tick count with no world)          |
| `blockType`  | string  | Material of the previewed block, or the material an entity maps to        |
| `customName` | string  | Player-given name of the container or entity; blank collapses to empty    |

Available whenever the target has an inventory (including furnaces, brewing stands and jukeboxes):

| Name                  | Type   | Meaning                    |
|-----------------------|--------|-----------------------------|
| `inventory.size`      | number | Slot count                  |
| `inventory.occupied`  | number | Non-empty slot count        |

Then one group, chosen from the target:

**furnace** — `cookTime`, `cookTimeTotal`, `burnTime`, `fuelSeconds`, `bankedXp` (`-1` on a server with
no `getRecipesUsed`), `lit`, `surge.active`, `surge.gain`.

**brewing** — `brewTime`, `brewTotal` (fixed 400), `fuelLevel`, `maxFuel` (20), `surge.active`,
`surge.gain`.

**beehive** — `bees`, `maxBees`, `honey`, `maxHoney`.

**cauldron** — `level`, `maxLevel`, `fluid` (`empty`, `water`, `lava`, `powder_snow`).

**jukebox** — `playing`, `record` (readable name, empty when none).

`surge.active` is true while a tick counter has been observed advancing faster than the game clock — a
hopper-fed boost, or a plugin fast-forwarding a brew — and `surge.gain` is the seconds gained.

The canonical machine-readable copy of this catalog is `src/test/resources/preview-variables.json`; a
test fails when it drifts from the code.

---

## The multi-styled label idiom

A label is one expression producing one string, and that string is then parsed for legacy `&` codes and
MiniMessage tags. So a label with several differently coloured runs is one concatenation, not several
elements:

```
"(burnTime > 0 ? '&e' + lang('holoui.preview.stat.fuel_seconds', fuelSeconds)
               : '&8' + lang('holoui.preview.stat.no_fuel'))
 + (bankedXp > 0 ? '<dark_gray>  •  </dark_gray><green>' + lang('holoui.preview.stat.xp_gain', str(bankedXp)) + '</green>' : '')"
```

The same trick styles a title: `'&f&l' + plain(lang(vars.titleKey))` strips the catalog entry's own
codes and applies the document's.

---

## Worked example: a furnace progress bar

Eight cells, filled left to right by cook progress, with the leading cell pulsing and an idle chase when
the furnace is burning but not cooking:

```json
{
  "type": "cell",
  "repeat": { "count": "vars.segments", "var": "i" },
  "x": "-24 + i * 7",
  "y": 10,
  "size": 5,
  "color": "cookTime > 0 && cookTimeTotal > 0 ? (i < floor(cookTime / cookTimeTotal * vars.segments) ? vars.fill : (i == floor(cookTime / cookTimeTotal * vars.segments) ? (mod(floor(cookTime / 4), 2) == 0 ? vars.pulseBright : vars.pulseDim) : vars.wellColor)) : (burnTime > 0 && i == mod(floor(burnTime / 4), vars.segments) ? vars.chase : vars.wellColor)"
}
```

Every colour comes from a var, so the blast-furnace and smoker variants restyle the identical geometry
by overriding `fill`, `pulseBright`, `pulseDim`, `chase` and `wellColor`. See `previews/furnace.json` for
the shipped version, which adds the surge branch.

---

## Contributing variables from another plugin

Implement `art.arcane.holoui.api.PreviewStateProvider` and register it:

```java
import art.arcane.holoui.api.PreviewStateProvider;
import art.arcane.holoui.api.PreviewStateProviders;

public final class MyProvider implements PreviewStateProvider {

    @Override
    public String namespace() {
        return "myplugin";
    }

    @Override
    public Map<String, Object> snapshot(Block block, Entity entity, Player player) {
        return Map.of("charge", chargeOf(block));
    }
}

PreviewStateProviders.register(new MyProvider());
```

Documents then read `myplugin.charge`. Rules:

- `snapshot` is called on the region thread owning the target, at most once per game tick per preview.
  Bukkit access is safe; expensive work is not.
- Values are coerced to the runtime's types — any `Number` becomes a double, `Boolean` and `String` pass
  through, anything else is dropped.
- A namespace that would shadow a built-in variable is rejected whole, and warned about once.
- A provider that throws is skipped for that sample. It cannot take a preview down.

---

## Commands

| Command                              | Permission                      | Does                                                                    |
|--------------------------------------|---------------------------------|--------------------------------------------------------------------------|
| `/holoui previews list`              | `holoui.command.previews`       | Every loaded document with its block/entity counts, `special` and priority |
| `/holoui previews reset [name=<n>]`  | `holoui.command.previews.reset` | Rewrites shipped defaults over the files on disk. `name` defaults to `*`   |
| `/holoui previews dump <name>`       | `holoui.command.previews.dump`  | Builds the document once and prints element counts plus any build errors  |

`reset` restores the thirteen shipped files; it does **not** delete extra user documents that may shadow
them. `dump` builds against the block you are looking at when it matches that document, otherwise
against a statics-only scope, so it works from console.

Editing, adding or deleting a file in `plugins/holoui/previews/` reloads within a few ticks and closes
open previews (priority changes can move a target between documents). A document that fails to compile
logs `previews/<name>.json: <message>` and is skipped; on a reload the previously compiled version stays
live, so a half-saved edit never blanks a preview.

---

## Access

Reading container contents requires `holoui.preview` (operator-only by default). A viewer who lacks it,
cannot physically open the container, cannot satisfy a held-item lock, or is denied by an access provider
gets the `locked` document — one padlock marker, nothing from the inventory. Access is decided before any
document is built and before any inventory slot is read. See [menus.md](menus.md#configuration) for
`HoloUiContainerPreviewAccessEvent` and the WorldGuard integration.
