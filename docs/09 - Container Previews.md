# Container Previews

A container preview is a holographic card HoloUi draws in front of a player who looks at a container, built from client-side display entities that only that player sees. Previews are not menus: they open no menu session, fire no menu events, and share no types with the menu API. Every preview is described by a JSON *preview document*; the shipped defaults are extracted to `plugins/holoui/previews/` on first start and hot reloaded from there. This document covers the player-facing model, the complete document format, the compile pipeline, the state variable catalog, and access control.

| Path | Contains |
|------|----------|
| `plugins/holoui/previews/*.json` | Every live preview document, shipped and user-authored |
| `plugins/holoui/preview-scales.json` | Per-player scale factors |
| `plugins/holoui/settings.json` | `previewEnabled`, `previewLookDistance`, `previewScale` |
| `src/main/resources/previews/*.json` | The 13 documents baked into the jar |
| `schema/holoui-preview.schema.json` | JSON Schema (2020-12) for the format, documentation grade |
| `src/test/resources/golden/*.json` | 43 golden element snapshots (permanent regression record) |
| `src/test/resources/preview-variables.json` | Machine-readable copy of the variable catalog |

`PreviewDocumentParser` is the format's source of truth. The schema documents it and cannot express its cross-field rules; where the two disagree, the parser is right. See "12 - Web Editor & Schemas.md" for editor integration.

---

## 1. Player-facing model

### 1.1 What triggers a preview

Previews are look-based only. There is no click, no sneak, no held item, no empty-hand requirement, and no gamemode restriction. `MenuSessionManager.listenToInventoryPreview()` schedules a 1-tick repeating task; each pass calls `managePreviewEvents(player)` for every online player.

`getLookedAtPreviewTarget(Player)` casts two rays from the eye location, both limited to `previewLookDistance` (default `10.0`, clamped to `[1.0, 24.0]`):

| Ray | Call | Accepts |
|-----|------|---------|
| Block | `World.rayTraceBlocks(eye, dir, distance, FluidCollisionMode.NEVER, true)` | Non-`AIR` block whose material is in the registry's eligibility set |
| Entity | `World.rayTraceEntities(eye, dir, distance, 0.35, this::isPreviewEntity)` | `InventoryHolder` that is a `Minecart` or `ChestBoat`, and that some document resolves for |

The block wins only when `blockDistanceSquared + 0.01 < entityDistanceSquared`; otherwise the entity wins. A miss contributes `Double.MAX_VALUE`.

Block eligibility is a precomputed `EnumSet<Material>` of every material some loaded document names, exactly or through a glob, in its base match or in any variant. `AIR` is always excluded. `anyInventoryHolder` contributes no materials — it exists for inventory-holding carts and boats.

If `previewEnabled` is `false`, `getLookedAtPreviewTarget` returns `null` before raycasting.

### 1.2 Opening

1. A target is found and either no preview is open or the open preview's target does not match, so `createNewPreviewSession(target, player)` runs.
2. `ContainerPreviewAccess.capture(player)` snapshots the viewer's access state: `previewPermitted` (`previewEnabled && hasPermission("holoui.preview")`), `spectator` (`getGameMode() == GameMode.SPECTATOR`), and a clone of the main-hand item.
3. Block targets build on the block's region thread; entity targets build on the entity's thread.
4. `ContainerPreviewAccess.canOpen(...)` decides which document is used:
   - denied → `ContainerPreview.locked(...)`, using the `locked` document;
   - allowed and the resolved document's `special` is `enderChest` → `ContainerPreview.forEnderChest`, built on the player's thread from `player.getEnderChest()`;
   - allowed otherwise → `ContainerPreview.forBlock` / `ContainerPreview.forEntity`.
5. `openPreviewIfCurrent` runs on the player's thread and re-verifies before showing: the session must pass `canView()` and a second raycast must still hit the same target. A `null` session — no document matched, or the document built zero elements — is dropped.

### 1.3 Closing

| Cause | Mechanism |
|-------|-----------|
| Look away, out of range, at `AIR`, or at an ineligible target | `managePreviewEvents` gets a `null` target → `SessionHolder.closePreview()` |
| Look at a different container | Target mismatch → close, then open the new one |
| `previewEnabled` set to `false` | `ContainerPreview.tick()` returns `false` |
| Permission lost, or access decision flipped | Re-checked every 10 ticks; sets `accessStateMatches = false`, `tick()` returns `false` |
| Exception during tick | Logged, preview closed |
| Player quits or goes offline | `PlayerQuitEvent`, and the holder tick's `!player.isOnline()` branch |
| Plugin shutdown | `MenuSessionManager.destroyAll()` |
| Any preview document created, edited, reset or deleted | `PreviewDocumentRegistry` → `closeAllPreviews()`; the raycast loop rebuilds next tick |
| `previewScale` or `uiScale` config change | `refreshVisuals()` → `close(); open();` |

These are not close conditions: opening a real Bukkit inventory (no `InventoryOpenEvent` listener exists), opening a HoloUi menu, sneaking, death, respawn, or teleport. A world change closes the preview only indirectly, because the next raycast finds nothing.

Hiding is not closing. When `PreviewScaleService.isHidden(player)` is true the session stays alive; `tick()` despawns the display entities and respawns them when the player scales back up.

### 1.4 Tick intervals

| Loop | Interval | Source |
|------|----------|--------|
| Raycast and session management | 1 tick | `MenuSessionManager.listenToInventoryPreview()` |
| Holder render tick | 1 tick | `MenuSessionManager` constructor |
| Content refresh (slot items, live cell colours, live label text) | 4 ticks | `ContainerPreview.REFRESH_INTERVAL = 4` |
| Access re-check | 10 ticks | `ContainerPreview.ACCESS_RECHECK_INTERVAL = 10` |
| Preview document folder watcher | 5 ticks | `PreviewDocumentRegistry.WATCH_INTERVAL_TICKS = 5` |

### 1.5 Scale and positioning

The card is billboarded flat, facing the player, anchored between the eye and the target. Positions in a document are layout pixels; at scale `1.0`, `LAYOUT_PIXELS_PER_BLOCK = 160` layout pixels span one block. `x` is pixels right of the card centre, `y` is pixels up.

`ContainerPreview.recomputeAnchor()` runs every tick:

```
surfaceDistance = max(0.0, distance(eye, targetCenter) - 0.5)      // HALF_BLOCK
anchorDistance  = clamp(surfaceDistance - 0.12, 0.12, 1.5)         // EDGE_MARGIN, MIN_DISTANCE, COMFORT_DISTANCE
distanceFactor  = clamp(anchorDistance / 1.5, 0.08, 1.0)           // MIN_SCALE_FACTOR
scaleTarget     = previewScale * PreviewScaleService.factor(player) * distanceFactor
```

The card sits at `eye + look * anchorDistance`. Three independent multipliers therefore set the final size: the global `previewScale` setting, the viewer's own saved per-player factor, and the distance factor that shrinks the card as the player backs away from the container. They are separate values with separate storage and separate ranges — see §1.6 and §1.7.

Re-scaling only happens when `abs(scaleTarget - appliedScale) > appliedScale * 0.02` (`SCALE_EPSILON`). `rescale()` transforms the existing display entities in place with `changeTransform` / `changeScale`; entities are despawned and respawned only when the hide threshold (`< 0.30`) is crossed, or through `refreshVisuals()`, which the `previewScale` and `uiScale` config listeners trigger.

Target centres: a block uses `blockLocation + (0.5, 0.5, 0.5)`; an entity uses `entityLocation + (0, max(0.35, height * 0.5), 0)`.

`z` is not a paint index. It is a pull toward the eye:

```
shrink = max(0.5, 1.0 - z * 0.04)     // MIN_DEPTH_SHRINK, DEPTH_FRACTION_PER_UNIT
```

Each element is placed on the card plane, then moved along the eye-to-element vector by `shrink`, with its render scale multiplied by `shrink` too. A higher `z` draws in front but slightly smaller, and everything at `z >= 12.5` clamps to the same half-depth. Shipped documents use `z` in `0`–`8`.

A `slot` spawns up to three display entities: the well background at `z`, the item display at `z + 1.5` (`ITEM_Z_OFFSET`, scale `ITEM_PX = 15` pixels, brightness `0xF000F0`), and a bold white stack-count text at `z + 3.0` (`COUNT_Z_OFFSET`) offset to the well's bottom-right corner, only when the stack amount is `> 1`.

Label text renders as a text display; `background` is its background colour, `0` meaning fully transparent.

### 1.6 Per-player scale adjustment

No command changes the per-player factor. The only interaction is:

1. **Double-tap sneak** — two `PlayerToggleSneakEvent` with `isSneaking() == true` inside `DOUBLE_TAP_MS = 400`, while a preview session is open, toggles adjust mode. Leaving adjust mode persists the factor. Double-tapping with no preview open just exits adjust mode.
2. **Hold sneak and scroll the hotbar** while in adjust mode — the `PlayerItemHeldEvent` is cancelled and the factor becomes `clamp(current * pow(1.10, -diff), 0.25, 2.50)`, where `diff = newSlot - previousSlot` wrapped into `[-4, 4]`.

| Constant | Value | Role |
|----------|-------|------|
| `STEP` | `1.10` | Multiplier per hotbar step |
| `MIN_FACTOR` | `0.25` | Lower clamp |
| `MAX_FACTOR` | `2.50` | Upper clamp |
| `HIDE_BELOW` | `0.30` | Below this the preview is hidden entirely |
| `DOUBLE_TAP_MS` | `400` | Double-tap window |
| `ADJUST_IDLE_TIMEOUT_MS` | `20000` | Adjust mode auto-exits and persists after this idle time |

Feedback goes to the action bar, or to a boss-bar lane (`holoui:preview`, `BarColor.BLUE`, `BarStyle.SOLID`) when HUD slot arbitration hands out `HudSurface.BOSS_BAR`. The messages are `holoui.message.preview_scale.adjusting`, `.size`, `.hidden`, `.saved` and `.saved_hidden`.

Persistence is `plugins/holoui/preview-scales.json`, a pretty-printed map keyed by player UUID. Only factors other than `1.0` are written, rounded to two decimals. Loading clamps to `[0.25, 2.50]` and skips NaN, infinite and unparseable UUID entries.

### 1.7 Settings

In `plugins/holoui/settings.json`; see "01 - Installation & Configuration.md" for the file as a whole.

| Key | Type | Default | Clamp | On change |
|-----|------|---------|-------|-----------|
| `previewEnabled` | boolean | `true` | — | None (read live) |
| `previewLookDistance` | double | `10.00` | `[1.00, 24.00]` | None (read live) |
| `previewScale` | double | `0.65` | `[0.25, 4.00]` | `refreshVisuals()` on every session |

A null, NaN or infinite value falls back to the default. `previewEnabled` treats `null` as enabled. There are no per-player toggles in this file, and `previewScale` is not the sneak-and-scroll factor from §1.6 — the two multiply.

---

## 2. The preview document format

One document per file. The file's base name is the document name used by `/holoui previews`, by log lines, and by tie-breaking.

### 2.1 Top level

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `match` | object | no | empty | A document with no `blocks`, `entities` or `special` claims nothing and is never resolved |
| `variants` | object[] | no | `[]` | Alternate `vars` sets; also extend matching |
| `card` | object | no | none (`null`) | Absent means no chrome at all |
| `elements` | object[] | no | `[]` | Built in list order |

Unknown top-level keys are dropped silently — Gson binds by field name against `PreviewDocument` / `MatchDef` / `VariantDef` / `CardDef` / `ElementDef` / `RepeatDef`.

A document whose build produces zero elements yields no preview session.

Minimal document:

```json
{
  "match": { "blocks": ["DIRT"] },
  "elements": [ { "type": "cell", "x": 0, "y": 0, "size": 16, "color": "#FF8B5A2B" } ]
}
```

Full shape, every top-level key:

```json
{
  "match":    { "blocks": [], "entities": [], "special": null, "priority": 0, "vars": {} },
  "variants": [ { "blocks": [], "entities": [], "vars": {} } ],
  "card":     { "framed": true, "title": "<expr>", "accent": "<expr>", "minHalfWidth": 82 },
  "elements": [ { "type": "panel|cell|slot|label", "...": "..." } ]
}
```

### 2.2 `match`

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `blocks` | string[] | no | `[]` | Block material names, uppercased with `Locale.ROOT` |
| `entities` | string[] | no | `[]` | Entity type names, same rules |
| `special` | string | no | `null` | One of `enderChest`, `locked`, `anyInventoryHolder` |
| `priority` | integer | no | `0` | Highest wins. All shipped documents use `10` |
| `vars` | object | no | `{}` | Constants read as `vars.<name>` |

**Names and globs.** An entry containing `*` compiles to a glob predicate: the entry is split on `*` (`split("\\*", -1)`, keeping trailing empties), every literal segment is `Pattern.quote`d, `*` becomes `.*`, and the whole is anchored `^…$`. `*` is the only wildcard — no `?`, no character classes, no alternation. So `*_SHULKER_BOX` matches `RED_SHULKER_BOX`, and `*COPPER_CHEST` matches `COPPER_CHEST` and `EXPOSED_COPPER_CHEST`. Every other entry is an exact name.

An exact name that is not a known `Material` (for `blocks`) or `EntityType` (for `entities`) logs `<doc>: unknown block material 'X' at match.blocks[0], still compiling` and still compiles, so a name from a future game version or a modded server keeps working once it exists. Glob entries are never checked against the known name set. A `null` array entry rejects the document: `match.blocks[2]: must be a string, got null`.

**`special`.** Three markers name documents the plugin looks up by role rather than by target. Only the top-level match's `special` is used.

| Value | Meaning |
|-------|---------|
| `enderChest` | Built from the viewer's own ender chest inventory rather than a tile entity. The session takes this path when the document that wins the ordinary block resolution for the looked-at block carries the marker, so a higher-priority user document naming `ENDER_CHEST` without the marker gets the normal block path instead |
| `locked` | Target-less card shown when a viewer may not open the container. Built against a statics scope: no inventory, no block, no entity |
| `anyInventoryHolder` | Entity fallback, at the weakest grade, for any `InventoryHolder` entity no document names by type. Contributes no block materials |

Any other value rejects the document: `match.special: must be one of enderChest, locked, anyInventoryHolder, got 'foo'`.

`PreviewDocumentRegistry.special(marker)` looks a marker document up by marker alone; the highest priority wins and a tie warns. Its `vars` come back unmerged — a special target has no material or entity type for a variant to key off.

### 2.3 `variants`

A variant reuses the match shape; only `blocks`, `entities` and `vars` are read.

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `blocks` | string[] | no | `[]` | Same rules as `match.blocks` |
| `entities` | string[] | no | `[]` | Same rules as `match.entities` |
| `vars` | object | no | `{}` | Merged over the document's own `vars` |

A variant both restyles and extends matching: `CompiledPreviewDocument.matchesBlock` / `matchesEntity` test the base match and every variant, so a material named only in a variant makes the whole document resolvable for it. `chest.json` is the shipped example — its base match names only `CHEST`, `TRAPPED_CHEST` and `BARREL`; every shulker box and the `*COPPER_CHEST` glob live in variants.

Variants are tried in declaration order and the first match wins, so an earlier variant takes an overlap. A target no variant claims gets the document's own vars unchanged. Element templates are never changed by a variant, so one `furnace.json` draws a furnace, a blast furnace and a smoker from identical geometry with three palettes.

Selection is by name only: `varsForBlock(material)` walks the variants testing `exactBlocks.contains(name) || anyGlob(name)`; `varsForEntity(entity)` does the same against `entities`.

`priority` on a variant is silently ignored. `special` on a variant is validated — an invalid value rejects the document with `variants[0].special: …` — but is never used for resolution. A `null` array entry rejects: `variants[1]: must be an object, got null`.

### 2.4 `vars`

Author-declared constants, read as `vars.<name>`. The union of every name declared in `match.vars` and in every variant's `vars` is what `vars.<name>` may reference anywhere in the document; declaration order does not matter, because the parser collects all of them before compiling `card` and `elements`.

| JSON | Runtime value |
|------|---------------|
| number | `Double` |
| boolean | `Boolean` |
| string | `String`, except the `#` rule below |

Anything else — an object, an array, an explicit `null` — rejects the document: `match.vars.foo: must be a number, boolean, or string constant`.

A string var is never parsed as an expression. The single exception is a string whose first character is `#`: it is parsed with the expression language's own colour-literal grammar and arrives as the unsigned ARGB number JSON cannot express without losing the alpha byte to a signed int.

```
"accent":     "#FFB02E26"   ->  vars.accent is the number 0xFFB02E26
"stateColor": "<#F2A535>"   ->  plain text; it does not lead with '#'
"note":       "#ZZZ"        ->  compile error, document rejected
```

A leading `#` that is not a valid colour literal fails to compile rather than silently rendering as text: `match.vars.note: invalid color literal '#ZZZ': …`.

Vars have their own namespace. `vars.size` can never shadow `inventory.size`, and no state variable can shadow a var, because vars are reachable only under the `vars.` prefix.

### 2.5 `card`

Omit the whole object for bare content. Declaring one at all asks for the chrome, which is why `framed` defaults to `true` inside it.

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `framed` | boolean or expression | no | `true` | Draw the chrome at all. Evaluated once at build |
| `title` | expression string | no | none → `Component.empty()` | Title text. Evaluated once at build |
| `accent` | expression string | no | `0xFFCBD0D9` | Chrome accent; only the low 24 bits are used |
| `minHalfWidth` | integer | no | `82` | Minimum panel half-width in layout pixels |

`title` and `accent` are always expression source strings. Write a literal as `"title": "'Chest'"`; `"title": "Chest"` parses as the variable `Chest` and rejects the document. `minHalfWidth` has no expression form. `title` is rendered through `TextUtils.parse` (legacy `&` codes, then MiniMessage) — see "07 - Expressions & Placeholders.md" for the text pipeline and the multi-styled label idiom.

`accent`'s alpha byte is discarded; the framer rebuilds alpha from its own constants. When `framed` evaluates false, `title`, `accent` and `minHalfWidth` are not used at all.

Failure handling at build: `framed` throwing is reported and treated as unframed; `title` throwing yields an empty title; `accent` throwing yields the default grey.

A framed card emits its chrome first, so a built card reads: frame, panel, tray (only when the content has at least one cell or slot), title bar, title, then the document's own elements. See §2.11.

### 2.6 `elements`

Built in list order. `x`, `y`, `z`, `width`, `height`, `size` and `index` are all rounded to `int` with `(int) Math.round(...)` after evaluation.

| `type` | Required fields | Also accepts | Default `z` |
|--------|-----------------|--------------|-------------|
| `panel` | `width`, `height`, `color` | — | `1` |
| `cell` | `size`, `color` | — | `4` |
| `slot` | `size`, `index` | `wellColor` (default `0xFF15151B`) | `4` |
| `label` | `text` | `background` (default `0`, transparent) | `6` |

Common fields, accepted by every type:

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `type` | string | **yes** | — | `panel`, `cell`, `slot`, `label`. Case-sensitive |
| `x` | number or expression | no | `0` | Layout pixels right of card centre |
| `y` | number or expression | no | `0` | Layout pixels up from card centre |
| `z` | number or expression | no | Per type above | Depth; see §1.5 |
| `visible` | boolean or expression | no | `true` | Evaluated once at build; false skips the element |
| `repeat` | object | no | none | See §2.7 |

A missing or unrecognised `type` rejects the document: `elements[0].type: must be one of panel, cell, slot, label, got 'box'`. A `null` array entry rejects: `elements[3]: must be an object, got null`.

**`panel`** — a flat rectangle. `color` is evaluated once at build.

| Key | Type | Required | Default |
|-----|------|----------|---------|
| `width` | number or expression | **yes** | — |
| `height` | number or expression | **yes** | — |
| `color` | colour or expression | **yes** | — |

**`cell`** — a square swatch; the unit every gauge, flame, bar segment and padlock is built from.

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `size` | number or expression | **yes** | — | Square edge in layout pixels |
| `color` | colour or expression | **yes** | — | **Live**: re-evaluated every 4 ticks unless it folds to a constant |

**`slot`** — an inventory well that renders the stack in it.

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `size` | number or expression | **yes** | — | Square edge in layout pixels |
| `index` | number or expression | **yes** | — | Inventory slot index. Evaluated once |
| `wellColor` | colour or expression | no | `0xFF15151B` | Evaluated once |

Nothing clamps `index`; an out-of-range index renders an empty well. Guard it against `inventory.size` as the shipped documents do (`min(vars.cols * vars.rows, inventory.size)`). If the target has no inventory, the element is skipped with `slot: target has no inventory` and the rest of the document still draws. The item in the well and its count badge are re-read from the live `Inventory` every 4 ticks even though `index` is fixed.

**`label`** — parsed text.

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `text` | expression string | **yes** | — | **Live**: re-evaluated every 4 ticks unless it folds to a constant |
| `background` | colour or expression | no | `0` | Text background colour. Evaluated once |

`text` is always an expression source string. `"text": "Idle"` parses as the variable `Idle` and rejects the document; write `"text": "'Idle'"`. A missing `text` rejects: `elements[1].text: required for type label`. The evaluated string goes through `TextUtils.parse`.

### 2.7 `repeat`

Emits the element once per index, with the 0-based index bound to a loop variable that every field of that element can read.

```json
{
  "type": "cell",
  "repeat": { "count": "vars.segments", "var": "i" },
  "x": "-24 + i * 7",
  "y": 10,
  "size": 5,
  "color": "i < 4 ? vars.fill : vars.wellColor"
}
```

| Key | Type | Required | Default | Notes |
|-----|------|----------|---------|-------|
| `count` | number or expression | **yes** | — | Evaluated once at build, in an empty scope |
| `var` | string | no | `"i"` | Loop variable name |

- `count` is compiled with an empty scope, so it cannot reference its own loop variable. It may reference state variables and `vars.*`, so a grid sizes itself from `inventory.size`. An absent or explicitly-null `count` rejects the document: `elements[0].repeat.count: required`.
- A raw count produces `floor(raw)` instances; anything not `>= 1.0` (including `NaN`) produces zero.
- A **constant** count above `MAX_REPEAT_COUNT = 1024` rejects the document: `elements[0].repeat.count: constant repeat count 2000 exceeds 1024`.
- A **dynamic** count is truncated to `1024` at build with a reported error (`repeat count 5000 exceeds 1024, truncated`).
- The **compiled total** across all elements may not exceed `MAX_TOTAL_TEMPLATES = 4096`; the total is the sum of `floor(constantCount)` for constant repeats and `1` for everything else: `elements: total compiled template count 5000 exceeds 4096`.
- The same `4096` budget is re-enforced across the whole build. A repeat that would cross it is truncated (`repeat of 900 truncated at the 4096 element cap`), every element after it is skipped (`element cap 4096 reached, remaining elements skipped`), and the build returns what it managed. The budget counts attempts, so invisible elements still cost.
- `var` must match `^[a-zA-Z_][a-zA-Z0-9_]*$`, else `elements[0].repeat.var: must be a valid identifier, got '2x'`. An empty or absent `var` becomes `i`.
- `var` may not be `vars`, nor any name in the flat state catalog; such a name would resolve to the state namespace before the loop variable and be unreachable: `elements[0].repeat.var: 'time' collides with a reserved variable name and would be unreachable`.
- Each instance gets its own `RepeatScope` holding its own index. The live `cell.color` and `label.text` closures capture that scope, so instance 3 still reads `3` after the loop finishes.

### 2.8 Field value shapes

| Shape | Accepts | Fields |
|-------|---------|--------|
| number-or-expression | JSON number, or a string parsed as an expression | `x` `y` `z` `width` `height` `size` `index` `color` `wellColor` `background` `repeat.count` |
| boolean-or-expression | JSON boolean, or a string parsed as an expression | `visible` `card.framed` |
| expression-only string | A string, always parsed as an expression | `label.text` `card.title` `card.accent` |

Wrong JSON types produce specific errors:

```
elements[0].width:   must be a number or a string expression, got a boolean
elements[0].visible: must be a boolean or a string expression, got a number
elements[0].color:   must be a number or a string expression          (object or array)
```

The three expression-only fields are plain `String` on the DTO, so Gson coerces a JSON number or boolean into its string form rather than rejecting it. `"text": 5` compiles and renders `5`; `"title": true` parses as the keyword `true`. Nothing warns. Write the quoted expression meant — `"text": "'5'"`.

Gson binds an explicit JSON `null` to `JsonNull`, not to a Java `null`; the parser treats both identically. A required field rejects on either (`elements[0].width: required for type panel`); an optional field takes its default on either.

A colour written as a bare JSON number is the unsigned 32-bit ARGB value, so `"color": 16711680` is `0x00FF0000` — fully transparent red. A bare JSON number is the only way to write a colour whose alpha byte is zero; `#RGB`, `#RRGGBB` and `rgb()` all force alpha to `FF`.

### 2.9 Constant folding and variable validation

An expression containing no variable reference and no function call is constant (`ExprEvaluator.isConstant`). Constants are evaluated at compile time against an empty scope and the value is stored alongside the tree in `CompiledExpr`; build and render use the stored value.

- A constant expression that throws is a compile error: `"x": "1 / 0"` rejects the document.
- `str(1)` is a call, so it is not constant. Anything mentioning any function is non-constant even if its arguments are literals.
- A folded-constant `cell.color` is resolved once at build; a folded-constant `label.text` is parsed once into a `Component` and handed back unchanged by every poll, so a constant label never re-runs MiniMessage.
- Folding does not type-check. `"visible": "1"` folds to the number `1`, compiles, then fails at build with a type error and the element is skipped.

Variable names are checked at compile time by `PreviewDocumentParser.checkVariableName`, in this order:

1. In the flat state catalog (the union of every category's names, regardless of which category the document will run against) → **accepted**.
2. Starts with `vars.` → the suffix must be declared somewhere in the document → else **rejected**: `elements[0].color: unknown variable: vars.fil`.
3. No dot → must be the enclosing element's repeat variable → else **rejected**: `elements[0].x: unknown variable: j`.
4. Dotted with a reserved-namespace prefix (`vars`, `inventory`, `surge`, or any full catalog name) → **rejected**: `elements[0].color: unknown variable: surge.rate`.
5. Dotted with any other prefix → assumed to be a provider namespace, **warned** and compiled.

Because step 1 accepts names from any category, a cauldron document may reference `cookTime` and compile; it fails at render with `unknown variable: cookTime`. Function names are never validated at compile time; an unknown function fails at evaluation with `unknown function: <name>`.

The expression grammar, operators, standard function library and text pipeline are documented in "07 - Expressions & Placeholders.md".

### 2.10 Failure taxonomy

| Situation | Outcome |
|-----------|---------|
| Malformed JSON | Document rejected: `malformed JSON: …` |
| Empty file, or JSON `null` | Document rejected: `empty document` |
| Bad `special`, bad `type`, `null` array entry | Document rejected |
| Bad var value, or bad `#` colour var | Document rejected |
| Missing required element field | Document rejected |
| Wrong JSON type for a field | Document rejected |
| Expression parse error | Document rejected, message ends `… at <charPosition>` |
| Constant expression throws while folding | Document rejected |
| Unknown variable per §2.9 rules 2–4 | Document rejected |
| Unknown exact material or entity name | Warning, compiles |
| Dotted name with a non-reserved prefix | Warning, compiles |
| Element build-time expression throws | That element skipped, throttled log, build continues |
| `slot` on a target with no inventory | That element skipped, throttled log |
| Live `cell.color` throws at render | Cell renders `0x00000000`, throttled log |
| Live `label.text` throws at render | Label renders empty, throttled log |
| Repeat count over 1024 at build | Truncated to 1024, error reported |
| 4096 element budget exhausted | Repeat truncated, remaining elements skipped |
| Anything else during build | Build returns an empty list, so no preview |

A rejected document logs `previews/<name>.json: <message>` and is skipped. Build-time and render-time errors log at most one line per document per minute (`ERROR_LOG_INTERVAL_MS = 60_000`, keyed by document name), because the alternative is a log line every four ticks for as long as the player looks at the block. `/holoui previews dump` passes its own per-invocation `Consumer<String>` sink, which is never throttled.

An exception anywhere inside one template's expansion aborts that whole template — including the remaining instances of a repeat — reports `<type>: <message>`, and the build continues with the next element.

### 2.11 The card framer

When a `card` is declared and `framed` evaluates true, `CardFramer.frame(content, title, accent, minHalfWidth)` measures the emitted content and prepends the chrome. Every constant, every integer division and the emitted order are frozen; the golden snapshots pin all of it. A document with `"framed": false` can rebuild identical chrome by hand from this section.

| Constant | Value | Role |
|----------|-------|------|
| `WELL` | `18` | Assumed height of any non-label element |
| `LINE` | `12` | Assumed height of a label |
| `TRAY_PAD` | `4` | Tray padding around the grid bounds |
| `PANEL_PAD` | `7` | Panel padding |
| `TITLE_BAR_HEIGHT` | `17` | Title bar height |
| `FRAME_BORDER` | `3` | Frame extends this far past the panel |
| `GAP` | `6` | Gap between content top and title bar |
| `PANEL_COLOR` | `0xF21B1B22` | Fixed, not derived from the accent |
| `TRAY_COLOR` | `0xFF33333E` | Fixed |
| `FRAME_ALPHA` | `0xCC` | Alpha applied to the accent for the frame |
| `TITLE_BAR_ALPHA` | `0xE6` | Alpha applied to the accent for the title bar |
| z: frame / panel / tray / title bar / title | `0` / `1` / `2` / `3` / `6` | Chrome depths |

Measurement uses the assumed heights above, not each element's declared `size`:

```
for each content element e:
    halfHeight    = (e is Label) ? 6 : 9
    contentTop    = max(contentTop,    e.y + halfHeight)
    contentBottom = min(contentBottom, e.y - halfHeight)
    if e is Slot or Cell:                      // the "grid"
        gridLeft   = min(gridLeft,   e.x - 9)
        gridRight  = max(gridRight,  e.x + 9)
        gridBottom = min(gridBottom, e.y - 9)
        gridTop    = max(gridTop,    e.y + 9)

if no content at all: contentTop = 9, contentBottom = -9

panelHalfWidth  = max(minHalfWidth, (hasGrid ? (gridRight - gridLeft) / 2 : 9) + 7)
titleBarBottom  = contentTop + 6
panelTop        = titleBarBottom + 17
panelBottom     = contentBottom - 7
panelCenterY    = (panelTop + panelBottom) / 2       // Java integer division, truncates toward zero
panelWidth      = panelHalfWidth * 2
panelHeight     = panelTop - panelBottom
titleBarCenterY = (panelTop + titleBarBottom) / 2    // same truncation

frameColor    = (0xCC << 24) | (accent & 0xFFFFFF)
titleBarColor = (0xE6 << 24) | (accent & 0xFFFFFF)
```

Emitted order:

| # | Element | x | y | z | width | height | color |
|---|---------|---|---|---|-------|--------|-------|
| 1 | panel (frame) | `0` | `panelCenterY` | `0` | `panelWidth + 6` | `panelHeight + 6` | `frameColor` |
| 2 | panel (backdrop) | `0` | `panelCenterY` | `1` | `panelWidth` | `panelHeight` | `0xF21B1B22` |
| 3 | panel (tray), only when `hasGrid` | `(gridRight + gridLeft) / 2` | `(gridTop + gridBottom) / 2` | `2` | `(gridRight - gridLeft) + 8` | `(gridTop - gridBottom) + 8` | `0xFF33333E` |
| 4 | panel (title bar) | `0` | `titleBarCenterY` | `3` | `panelWidth` | `17` | `titleBarColor` |
| 5 | label (title) | `0` | `titleBarCenterY` | `6` | — | — | background `0` |
| 6+ | The document's own content, unchanged, in build order | | | | | | |

`hasGrid` is true when the content holds at least one `slot` or `cell`. With the default accent `0xFFCBD0D9`, the frame is `0xCCCBD0D9` and the title bar `0xE6CBD0D9`.

---

## 3. The pipeline

### 3.1 Parse

`PreviewDocumentParser.parse(name, json)` binds the raw text into the `PreviewDocument` DTO with Gson (JSON syntax errors surface as a `JsonParseException`, wrapped as `PreviewDocumentException`), then walks the DTO graph field by field so every failure can name its exact field path, for example `elements[3].color`. The output is one `CompiledPreviewDocument` holding:

- `name` (the file name including `.json`) and `priority`;
- a `CompiledMatch` (exact block names, block glob predicates, exact entity names, entity glob predicates, `special`);
- a list of `CompiledVariant`, each a `CompiledMatch` plus its own vars;
- the document-level `vars` map (`Double` / `Boolean` / `String` only);
- a `CardTemplate` or `null`;
- a list of `ElementTemplate`, each field a `CompiledExpr` (an `Expr` tree plus a folded constant when the tree needed no live scope).

`PreviewDocumentException.getMessage()` is always `documentName + " " + message`.

### 3.2 Compile-time bounds

Constant repeat counts are capped at `1024`; the sum of constant expansions plus one per non-constant element is capped at `4096`. Non-constant repeat counts are unknowable here, so both bounds are re-enforced at build.

### 3.3 Build

`CompiledPreviewDocument.build(PreviewStateContext)` resolves the compiled document against one live target and returns the `List<PreviewElement>` the renderer draws. The overload `build(PreviewStateContext, Consumer<String> errorSink)` additionally hands every failure to a caller sink, unthrottled.

1. For each element template in list order, with a per-call `Budget` of `4096`:
   - budget exhausted → report and stop;
   - no `repeat` → take 1 from the budget and emit once against the document scope;
   - otherwise evaluate `count` once, truncate to the remaining budget, and emit that many instances, each against a `RepeatScope` carrying its own index.
2. Emitting evaluates `visible` first and returns immediately if false (the budget was already taken).
3. Then `x`, `y`, `z`, then the type's own fields.
4. After all elements, if a `card` is declared and `framed` is true, the chrome is measured from the emitted content and prepended.

Everything structural — positions, sizes, z layers, panel and well colours, `visible`, repeat counts, the card's `framed` / `title` / `accent`, and every chrome number the framer derives — is evaluated exactly once here. Only two things stay live, because `PreviewElement` models them as suppliers the renderer polls:

| Field | Live? |
|-------|-------|
| `cell.color` (`IntSupplier`) | Yes, unless folded to a constant |
| `label.text` (`Supplier<Component>`) | Yes, unless folded to a constant |
| Everything else | No |

That is a hard constraint on animation: anything that must change on screen has to be expressed as a cell colour or a label string. An element hidden by `visible` at build time stays hidden for the life of that preview, and a grid does not resize itself as items move. Two things change without being expressions: a `slot`'s item and its stack-count badge, re-read on the same 4-tick beat.

A preview is built once when the session opens and rebuilt only when the session is recreated — when a document on disk is created, edited, reset or deleted, or when the viewer's access decision changes. Per-player scale changes do not re-run the build.

### 3.4 Render

`ContainerPreview` holds one `Rendered` per `PreviewElement`, each owning up to three display-entity UUIDs (`background`, `item`, `count`). Every 4 ticks it schedules a `readDynamic` pass on the target's region thread, writing to `volatile` pending fields; the render tick then applies only what changed (`changeTextBackground` for cells, `changeName` for labels, `changeItem` / spawn / despawn for slots). Positions are recomputed every tick; scale is reapplied only when it moves by more than 2%. See "11 - Runtime Architecture.md" for the scheduler model.

### 3.5 Loading and hot reload

`PreviewDocumentRegistry` is constructed in `HoloUI.onEnable` with `getDataFolder()`, so the folder is `plugins/holoui/previews/`. The constructor:

1. creates the folder if absent;
2. extracts every shipped document **missing** from it — existing files are never overwritten;
3. compiles every non-directory file whose name ends in `.json` (case-insensitive).

`startWatching()` then arms a `FolderWatcher` on a single 5-tick task that handles modifications, creations and deletions together in one pass. Any change republishes the resolution snapshot and calls `MenuSessionManager.closeAllPreviews()`, because a preview holds the element list it was built from and a priority change can move a target between documents. Menus are untouched.

Change detection compares only a file's last-modified time and its length — no hash, no content compare. An edit that changes neither (a same-length replacement landing inside the filesystem's mtime resolution) is not seen, and stays unseen until some later edit moves one of the two. Touch the file or change its length if a preview does not pick up an edit.

A file that fails to compile logs `previews/<name>.json: <message>` and is skipped. On reload the previously compiled version stays live, so a half-saved edit never blanks a preview. A deleted file drops its document. `load` catches `Throwable` (not just `RuntimeException`) so a pathological document cannot abort plugin enable or kill the hot-reload task; `ThreadDeath` is rethrown.

`resetToDefault(nameOrStar)` rewrites the named shipped document, or all of them for `"*"`, from the jar over whatever is on disk, then reloads and closes open previews. It does not delete extra user documents. A name that is not a shipped document writes nothing.

### 3.6 Resolution order

There is no separate "user overrides bundled" mechanism: user files and extracted shipped files live in the same folder and compete by the same rules. Users drop custom documents into `plugins/holoui/previews/` as `*.json`.

`publish()` sorts every loaded document by name and precomputes the raycast eligibility `EnumSet`. `best(...)` then grades every document against the target:

| Grade | Value | Meaning |
|-------|-------|---------|
| `GRADE_NONE` | 0 | Does not match; skipped |
| `GRADE_FALLBACK` | 1 | `anyInventoryHolder`, entities only |
| `GRADE_GLOB` | 2 | Matched by a `*` pattern |
| `GRADE_EXACT` | 3 | Named exactly |

Comparison is priority first, then grade. A document replaces the incumbent only when strictly better, so a draw on both keys keeps the incumbent — which, because iteration is in name order, is the lexicographically smaller document name. That tie is stable across restarts and warned once per pair per reload:

```
previews: a.json and b.json match the same targets at priority 10, using a.json.
```

A document's grade is the maximum over its own match and every variant, short-circuiting on `exact`. Entities additionally fall back to `anyInventoryHolder` at `GRADE_FALLBACK` when nothing named them.

To override a shipped preview, put a document beside it with a higher `priority` (shipped documents all use `10`). To make a new block previewable, name its material in `match.blocks` or in any variant's `blocks` — both count for both resolution and raycast eligibility.

An entity is eligible only when it is an `InventoryHolder`, is a `Minecart` or a `ChestBoat`, and some document resolves for it. Deleting `minecart.json` stops carts being previewed.

### 3.7 Commands

Full command reference is in "02 - Commands & Permissions.md". The preview-specific subcommands:

| Command | Permission (all `default: op`) | Does |
|---------|-------------------------------|------|
| `/holoui previews list` | `holoui.command.previews` | Lists every loaded document with `blocks=`, `entities=`, `special=`, `priority=` counts from `matchSummary()` |
| `/holoui previews reset [name]` | `holoui.command.previews.reset` | Rewrites shipped defaults over disk; `name` defaults to `*`; runs async; a trailing `.json` is tolerated |
| `/holoui previews dump <name>` | `holoui.command.previews.dump` | Builds the document once and prints element counts plus up to 3 build errors and a `+N more` tail |

A bare `/holoui previews` is normalized to `/holoui previews list`. `list` counts every matcher — exact names and globs, base match and every variant — so `chest.json` reports a large block count.

`dump` for a player uses `MenuSessionManager.lookedAtBlock(player)`; if that block matches the document it builds against the live block with that block's merged variant vars, otherwise it falls back to statics. Console and RCON always get a statics scope, so every `slot` element reports `slot: target has no inventory` — expected, not a document bug. The dump error sink is per invocation and unthrottled, so repeated dumps of the same broken document always show its errors.

---

## 4. State variables

### 4.1 `PreviewStateContext`

`PreviewStateContext` is the `ExprScope` a document is evaluated against. Four factories:

| Factory | Category selection | Inventory |
|---------|--------------------|-----------|
| `forBlock(block, player, vars)` | `PreviewStateAdapters.selectBlock(block, player)` | From the selection |
| `forEntity(entity, player, vars)` | `selectEntity(entity)` | Holder inventory, or none |
| `forInventory(inventory, vars)` | `inventory` | The given inventory |
| `statics(vars)` | `static` | None |

Variable resolution: `vars.<name>` reads the injected variables map, and variables are reachable only under that prefix; everything else reads the cached adapter snapshot, which already contains provider namespaces merged under `<namespace>.<key>`. Unknown names return `null`, which `ExprEvaluator` turns into `unknown variable: <name>`.

The snapshot is sampled lazily on the first lookup and re-sampled whenever the tick changes, so one refresh reads each Bukkit getter once no matter how many expressions reference it. The tick is `world.getGameTime()`, or `System.currentTimeMillis() / 50` when the context has no world. The tick and the map it produced are published together in one immutable `Sampled` record behind a single `volatile` field, so a reader can never pair one sample's tick with another sample's map.

### 4.2 Category dispatch

The category is picked once, at construction. `PreviewStateAdapters.selectBlock` — the first branch that matches wins:

1. `ENDER_CHEST` → `inventory`, backed by the viewer's own ender chest (`null` with no viewer)
2. `BrewingStand` state → `brewing`
3. `Furnace` state → `furnace`
4. `Container` state → `inventory`
5. `Jukebox` state → `jukebox`
6. Any other `InventoryHolder` state → `inventory` (this is how chiseled bookshelves, shelves and lecterns work; it must stay below the jukebox branch, because a jukebox is also a non-`Container` `InventoryHolder`)
7. `BEEHIVE` / `BEE_NEST` → `beehive`
8. `CAULDRON` / `WATER_CAULDRON` / `LAVA_CAULDRON` / `POWDER_SNOW_CAULDRON` → `cauldron`
9. Otherwise → `static`

`selectEntity`: an `InventoryHolder` entity → `inventory`; anything else → `static`.

A context always publishes the `universal` group, publishes the `inventory` group whenever it has a non-null inventory (including furnaces, brewing stands and jukeboxes), and publishes the group named by its own category.

### 4.3 Variable catalog

The canonical machine-readable copy is `src/test/resources/preview-variables.json`; a test fails when it drifts from the code.

**`universal` — always published**

| Name | Type | Meaning |
|------|------|---------|
| `time` | number | World game time in ticks, or `System.currentTimeMillis() / 50` with no world |
| `blockType` | string | `Material.name()` of the block; for an entity, the material its type name maps to (falling back to `MINECART`); `""` when neither |
| `customName` | string | Player-given name of the container or entity; `null` or whitespace-only collapses to `""` |

**`inventory` — whenever the target has an inventory**

| Name | Type | Meaning |
|------|------|---------|
| `inventory.size` | number | `Inventory.getSize()` |
| `inventory.occupied` | number | Count of non-empty slots |

**`furnace`**

| Name | Type | Meaning |
|------|------|---------|
| `cookTime` | number | Ticks the current smelt has cooked (counts up) |
| `cookTimeTotal` | number | Ticks required to finish the current smelt |
| `burnTime` | number | Ticks of fuel remaining |
| `fuelSeconds` | number | `burnTime / 20` as integer division (`TICKS_PER_SECOND = 20`) |
| `bankedXp` | number | Sum of `recipe.getExperience() * timesUsed` over `getRecipesUsed()`; `-1` on a server whose `Furnace` has no `getRecipesUsed` (checked reflectively once) |
| `lit` | boolean | `burnTime > 0` |
| `surge.active` | boolean | See §4.4 |
| `surge.gain` | number | See §4.4 |

`bankedXp` uses `-1` as a "not available" sentinel, which is why `furnace.json` guards with `bankedXp >= 0` before drawing the XP fragment at all and `bankedXp > 0` for the non-zero styling.

**`brewing`**

| Name | Type | Meaning |
|------|------|---------|
| `brewTime` | number | Ticks remaining (counts down to zero) |
| `brewTotal` | number | Fixed `400` (`BREW_TOTAL_TICKS`); Bukkit exposes no per-brew total |
| `fuelLevel` | number | `BrewingStand.getFuelLevel()` — blaze powder charges remaining |
| `maxFuel` | number | Fixed `20` (`MAX_FUEL_LEVEL`) |
| `surge.active` | boolean | See §4.4 |
| `surge.gain` | number | See §4.4 |

Because `brewTime` counts down, progress is `clamp(1 - brewTime / brewTotal, 0, 1)`.

**`beehive`**

| Name | Type | Meaning |
|------|------|---------|
| `bees` | number | `Beehive.getEntityCount()` floored at 0; `0` when the state is missing |
| `maxBees` | number | `Beehive.getMaxEntities()` floored at 1; default `3` (`DEFAULT_MAX_BEES`) |
| `honey` | number | Block data honey level; `0` when unavailable |
| `maxHoney` | number | Block data maximum honey level floored at 1; default `5` (`DEFAULT_MAX_HONEY`) |

**`cauldron`**

| Name | Type | Meaning |
|------|------|---------|
| `level` | number | `0` for an empty `CAULDRON`; else `Levelled.getLevel()` floored at 0; `3` when the data is not `Levelled` |
| `maxLevel` | number | `3` (`CAULDRON_MAX_LEVEL`) for `CAULDRON`; else `Levelled.getMaximumLevel()` floored at 1 |
| `fluid` | string | `empty` (`CAULDRON`), `lava`, `powder_snow`, or `water` for anything else |

**`jukebox`**

| Name | Type | Meaning |
|------|------|---------|
| `playing` | boolean | Has a record and is playing |
| `record` | string | `readable()` of the record's material name, or `""` when none is loaded |

The `static` category publishes only the `universal` group.

Empty strings rather than absent variables are deliberate: documents branch on `blockType != ''`, `customName != ''` and `record != ''`.

### 4.4 `TimeFlowTracker` and `surge.*`

Published by the `furnace` and `brewing` categories only. One tracker is created per context when `PreviewStateAdapters.tracksTimeFlow(category)` is true, with `countsDown` true for brewing. It detects a tick counter advancing faster than the game clock it is sampled against — a hopper-fed boost, a plugin fast-forwarding a brew.

```
if (gameTime == lastGameTime) return;                    // repeated reads at one tick are ignored
gained  = countsDown ? lastValue - value : value - lastValue
elapsed = gameTime - lastGameTime
if (lastGameTime is set && value > 0 && elapsed > 0 && elapsed <= 100 && gained > elapsed + 1) {
    surgeSeconds = (gained - elapsed) / 20.0
    surgeUntil   = gameTime + 60                          // SURGE_HOLD_TICKS
}
lastGameTime = gameTime; lastValue = value;
```

- `surge.active` is `lastGameTime <= surgeUntil` — true for a 60-tick hold after the last detected surge.
- `surge.gain` is the seconds of progress gained beyond real elapsed time in the window that triggered it. It is not reset when the hold expires; read it only while `surge.active`.
- The first sample of a preview can never trigger a surge.
- Instances are single-threaded: one per preview context, sampled from the region thread that owns the block. Evaluating a live supplier mutates the tracker as a side effect, which is why `GoldenSerializer` evaluates each supplier exactly once in list order.

### 4.5 Context functions

Resolved by `PreviewStateContext.call` before the standard function library, so they cannot be shadowed. `CONTEXT_FUNCTIONS = {"lang", "count", "occupied", "item"}`.

| Function | Returns | Semantics |
|----------|---------|-----------|
| `lang(key, …)` | string | Localized message through `HoloLocalization.globalText` |
| `count(slot)` | number | Stack size; `0` when empty, out of range, or no inventory |
| `occupied(slot)` | boolean | `true` when the slot holds a non-empty stack |
| `item(slot)` | string | Material id (`"IRON_ORE"`); `""` when empty, out of range, or no inventory |

`count`, `occupied` and `item` take exactly one numeric argument, floored to an int. A stack is empty when it is `null`, `AIR`, or has amount `< 1`. `item` returns ids rather than display text, matching `blockType`; for text a document writes `readable(item(0))`.

`lang` argument binding, the shipped `holoui.preview.*` message keys and their English templates are documented in "10 - Localization.md"; the function's exact signature and error strings are in "07 - Expressions & Placeholders.md".

### 4.6 Third-party variables

Another plugin contributes variables under its own namespace by implementing `PreviewStateProvider` and registering it with `PreviewStateProviders`. A document then reads `<namespace>.<key>`, which compiles with a warning (§2.9 rule 5) and resolves at render when the provider is present. Reserved namespaces (`PreviewStateAdapters.RESERVED_NAMESPACES`) are `vars`, every full catalog name, and the first segment of every dotted catalog name — `inventory` and `surge`. The same set is what a `repeat.var` may not collide with.

The provider interface, its threading contract, value coercion and failure handling are documented in "16 - API - Previews.md".

---

## 5. Access control

### 5.1 Permissions and the global gate

| Node | Default | Grants |
|------|---------|--------|
| `holoui.preview` | `op` | View contents in holographic container previews |
| `holoui.command.previews` | `op` | List preview documents and their match rules |
| `holoui.command.previews.reset` | `op` | Restore shipped preview document defaults |
| `holoui.command.previews.dump` | `op` | Build a preview document once and print its element counts |

`ContainerPreviewAccess.canView(viewer)` is `previewEnabled && viewer.hasPermission("holoui.preview")`. When `previewEnabled` is false the permission is never looked up.

`ContainerPreview.canView()`:

```java
return ContainerPreviewAccess.isEnabled()
    && accessStateMatches
    && (!showsContents || ContainerPreviewAccess.canView(player));
```

Locked previews are constructed with `showsContents = false`, so the locked card is shown even to a player without `holoui.preview`, as long as `previewEnabled` is true and a raycast-eligible target is in view.

### 5.2 `canOpen` for blocks

`ContainerPreviewAccess.canOpen(Player, Block, ViewerAccess)` returns true only when all of:

1. `access.previewPermitted()` — the `previewEnabled` plus permission check, captured before the build;
2. `isPhysicallyOpenable(block, state)`:
   - a `Chest` or `EnderChest` must not be `isBlocked()`;
   - a `ShulkerBox` must be open, or have viewers, or its lid box must be clear. The lid box is `shulkerLidBounds(x, y, z, facing)` — a half-block volume in the facing direction, inset by `COLLISION_EPSILON = 1.0E-6` — and it must overlap neither the neighbouring block's collision shape nor any valid entity's bounding box. A shulker whose block data is not `Directional` is treated as not openable;
3. `canUnlock(...)` — see §5.3;
4. the same physical and lock checks against `connectedChest(block)`, the other half of a double chest;
5. `ContainerProtectionService.canAccess(viewer, block)` — a `null` service allows.

`canOpen(Player, Entity, ViewerAccess)` is only `access.previewPermitted()` plus the protection check.

`ViewerAccess` is a record of `(boolean previewPermitted, boolean spectator, ItemStack mainHandItem)`, captured once per open and once per 10-tick re-check; a `null` main-hand item is normalized to `new ItemStack(Material.AIR)` and always defensively cloned.

### 5.3 Container locks

For a `Lockable` state that `isLocked()`, `canUnlock` fires a Paper `BlockLockCheckEvent(block, viewer, null, null)` with `setKeyItem(access.mainHandItem().clone())`:

| Event result | Outcome |
|--------------|---------|
| `Event.Result.ALLOW` | Allowed |
| `Event.Result.DENY` | Denied |
| `Event.Result.DEFAULT`, viewer is a spectator | Allowed (spectators bypass locks) |
| `Event.Result.DEFAULT`, otherwise | Key item matched against the lock |

The key item is `event.getKeyItem()` when `event.isUsingCustomKeyItemStack()`, else the captured main-hand item. Matching is reflective and cached: `state.getBlockEntity()` → field `lockKey` → `CraftItemStack.asNMSCopy(ItemStack)` → `LockCode.unlocksWith(nmsItem)`. Any `ReflectiveOperationException`, `RuntimeException` or `LinkageError` logs once at `SEVERE` ("Container lock access could not be evaluated. Locked container previews will remain hidden.") and returns false.

### 5.4 Protection hooks

`art.arcane.holoui.integration.protection.ContainerProtectionService` is constructed in `HoloUI.onEnable` and registers itself as a `Listener`. It holds one `ContainerProtectionProvider`, starting at an internal `ALLOW_ALL`.

- WorldGuard is installed when that plugin is present and enabled, and installed late from a `MONITOR`-priority `PluginEnableEvent`. `PluginDisableEvent` reverts to `ALLOW_ALL`. A successful install logs "Container previews are using WorldGuard access checks."; a failed install swaps in `DENY_ALL`.
- WorldGuard is the only third-party hook. `WorldGuardContainerProtectionProvider` is fully reflective, with no compile-time dependency. Blocks go through `WorldGuardPlugin.createProtectionQuery().testBlockInteract(player, block)`, repeated for the connected half of a double chest. Entities go through `wrapPlayer` → `LocalPlayer.getWorld()` → `ConfigurationManager.get(world).useRegions` (false allows) → `SessionManager.hasBypass` (true allows) → `RegionQuery.testBuild(location, localPlayer, Flags.CHEST_ACCESS)`.
- Any failure during a check logs once at `SEVERE` ("Container access protection failed. Previews will remain locked until the protection provider recovers.") and returns false.

### 5.5 `HoloUiContainerPreviewAccessEvent`

`ContainerProtectionService.canAccess` asks the provider first, then fires this event and returns `!event.isCancelled()`.

```java
public final class HoloUiContainerPreviewAccessEvent extends Event implements Cancellable
```

Two constructors, `(Player, Block)` and `(Player, Entity)`; exactly one of `getBlock()` / `getEntity()` is non-null. Both `player` and the target are `Objects.requireNonNull`. Cancelling makes the preview render as the `locked` card instead of the container's contents.

### 5.6 Re-checks

Every `ACCESS_RECHECK_INTERVAL = 10` ticks, `ContainerPreview.scheduleRefresh(true)` re-captures `ViewerAccess` and re-runs `canOpen` on the target's region thread. If `canOpen != showsContents` — the viewer gained or lost access while looking — `accessStateMatches` is set false, `canView()` fails, and the session closes so the raycast loop rebuilds it down the other path.

### 5.7 `locked.json` behaviour

The locked document is looked up by marker (`registry.special("locked")`) and built with `PreviewStateContext.statics(resolved.vars())`. Only `time`, `blockType` (`""`), `customName` (`""`) and `vars.*` resolve; every `slot` element is skipped with `slot: target has no inventory`. It carries `"card": { "framed": false }`, so no chrome is drawn, and four `cell` elements form a padlock. If the document is missing or produces no elements, `ContainerPreview.locked(...)` returns `null` and no preview is shown at all.

---

## 6. Bundled documents

Thirteen documents, hardcoded in `PreviewDocumentRegistry.SHIPPED`, because a directory listing inside a jar is not portable across class loaders. `ShippedPreviewDocumentTest` pins the list against the resources actually present, so a document added to one and not the other fails the build. All use `priority: 10`.

| File | Covers |
|------|--------|
| `beehive.json` | `BEEHIVE`, `BEE_NEST` — bee count and honey level gauge, no inventory |
| `brewing_stand.json` | `BREWING_STAND` — three bottle slots, ingredient and fuel slots, brew progress bar, fuel cells, state and stat lines |
| `cauldron.json` | `CAULDRON`, `WATER_CAULDRON`, `LAVA_CAULDRON`, `POWDER_SNOW_CAULDRON` — fill-level cells tinted per fluid via variants |
| `chest.json` | `CHEST`, `TRAPPED_CHEST`, `BARREL` plus, through 20 variants, every shulker box and the `*COPPER_CHEST` glob — the canonical 9-wide, up-to-6-row slot grid |
| `chiseled_bookshelf.json` | `CHISELED_BOOKSHELF` — a 3x2 slot grid |
| `dispenser.json` | `DISPENSER` and, via a variant, `DROPPER` — a 3x3 slot grid |
| `ender_chest.json` | `ENDER_CHEST`, marked `special: "enderChest"` — the viewer's own 9-wide ender chest grid |
| `furnace.json` | `FURNACE` and, via variants, `BLAST_FURNACE` and `SMOKER` — input/fuel/output slots, 8-segment cook bar, per-style flame or vent or smoke cells, state and fuel/XP lines |
| `hopper.json` | `HOPPER` — a 5-slot row |
| `jukebox.json` | `JUKEBOX` — the disc slot and a playing/loaded/no-disc label |
| `locked.json` | `special: "locked"` — the unframed four-cell padlock shown when access is denied |
| `minecart.json` | `CHEST_MINECART`, `HOPPER_MINECART`, `*_CHEST_BOAT`, `*_CHEST_RAFT`, and `special: "anyInventoryHolder"` as the entity fallback — a 5-slot row for hopper carts, a grid otherwise |
| `shelf.json` | The `*_SHELF` glob — a slot row for every wood shelf variant |

Of `chest.json`'s 20 variants, 17 are shulker boxes (16 dyed plus the undyed `SHULKER_BOX`), each differing only in `titleKey`, `titleArg` and `accent`. The other three are `TRAPPED_CHEST`, `BARREL` and the `*COPPER_CHEST` glob, and those three carry no `titleArg`.

---

## 7. Golden tests

`src/test/resources/golden/` holds 43 JSON snapshots, one per scenario in `GoldenEquivalenceTest`. They are the permanent regression record for the whole preview engine. `GoldenEquivalenceTest` (a JUnit 4 `Parameterized` test) loads the real resource from `src/main/resources/previews`, resolves variant variables exactly as `PreviewDocumentRegistry` will (`matchesBlock` / `varsForBlock`, `matchesEntity` / `varsForEntity`), builds against a `PreviewStateContext` over the same `GoldenFakes` state the capture used, serializes through `GoldenSerializer`, and compares parsed JSON trees. A document edit that moves a single pixel fails here.

What the goldens lock, per `GoldenSerializer`:

- **Element order** — nothing is sorted or re-ordered, and field order inside each object is fixed, because element order drives render order.
- **Every field** — no field is excluded. Panels carry `type/x/y/z/width/height/color`, cells `type/x/y/z/size/color`, slots the well and index, labels the serialized component.
- **Colours** as unsigned `#AARRGGBB`, never the signed `int` they are stored in.
- **Label components** are `Component.compact()`-ed before Gson serialization, so a programmatically assembled component and the same component parsed from a string are not separated by an empty wrapper node.
- **Supplier evaluation** — each `Cell` / `Label` supplier is evaluated exactly once, in list order. Furnace suppliers mutate a `TimeFlowTracker` as a side effect, so a second pass or a different order would change what the surge scenario renders.

The engine has no `Random`, no wall clock and no identity-derived value inside a golden scenario: every element is a pure function of the fake block state and the game time, both pinned by `GoldenFakes`.

**The goldens are not re-derivable and must be kept as-is for now.** They were captured from the retired hand-written layout classes the JSON documents replaced, and no generator exists anywhere in the test tree — `GoldenEquivalenceTest` only reads and compares. Do not invent a regenerator or hand-edit snapshots to green a suite. A golden failure means the document or the engine changed; fix that source, or deliberately review any intentional output change before replacing a snapshot by hand. `CardFramer`'s arithmetic is frozen for the same reason, including `(panelTop + titleBarBottom) / 2` truncating toward zero, which must not be "cleaned up".

`furnace_surging` additionally drives the tracker the way the capture did: sample once at the earlier tick with the lower cook counter, then advance both and build — 80 counter ticks gained in 30 game ticks. The priming read goes through the context, because the tracker lives on the context, not on the elements.

Adjacent contract tests:

| Test | Locks |
|------|-------|
| `ShippedPreviewDocumentTest` | All 13 shipped documents compile; the `SHIPPED` list matches the resources; the two title paths the captures never exercised (a player-named container, a material-derived name behind a glob) |
| `VariableCatalogSyncTest` | `src/test/resources/preview-variables.json` matches `PreviewStateAdapters.catalog()` and `PreviewStateContext.CONTEXT_FUNCTIONS`, in both directions |
| `PreviewDocumentParserTest` | Every parse rule, default and error message in §2 |
| `PreviewBuildTest` | Build semantics: caps, repeat scoping, `visible`, error containment, constant-vs-live text |
| `PreviewRegistryTest` | Extraction, reload, reset, priority/grade/name resolution, raycast eligibility |
| `PreviewStateContextTest` | Every catalog variable, provider merging, snapshot caching, the `lang` binding rules |
| `ComponentParityProbeTest` | That every label shape in the goldens is reproducible from a string through `TextUtils.parse` |
| `ContainerPreviewAccessTest`, `ContainerProtectionServiceTest`, `HoloUiContainerPreviewAccessEventTest` | Access gating |

---

## 8. Runtime notes

- **A zero-element build is retried every tick.** Raycast eligibility is a precomputed material set, so a document that matches but builds nothing keeps the block eligible; `managePreviewEvents` finds the target again and `createNewPreviewSession` re-runs the null build for as long as the player looks at it.
- **A variant's `special` is validated but never used.** Only `priority` is silently ignored on a variant. An invalid `special` value rejects the whole document (§2.3).
- **Change detection is mtime plus length.** An edit that changes neither is invisible to the watcher (§3.5).
- **The locked card ignores `holoui.preview`.** Locked previews are built with `showsContents = false`, so the padlock renders for players who hold no preview permission at all (§5.1).
- **The scale model has three multipliers, not one.** The global `previewScale` setting, the per-player sneak-and-scroll factor, and the distance factor multiply; a resulting factor below `0.30` hides the preview entirely (§1.5, §1.6).
- **Expression-only fields silently accept non-strings.** `label.text`, `card.title` and `card.accent` are plain `String` on the DTO, so Gson coerces a JSON number or boolean instead of rejecting it (§2.8).
- **A bare JSON number colour has alpha `0`.** `"color": 16711680` is fully transparent red; every string colour form forces alpha to `FF` (§2.8).
- **The flat catalog accepts cross-category names.** A cauldron document referencing `cookTime` compiles and fails at render (§2.9).
- **`bankedXp` is `-1`, not absent, when unavailable.** Guard with `bankedXp >= 0` (§4.3).
- **A statics dump always reports `slot: target has no inventory`.** That is the console and RCON path, not a document bug (§3.7).
- **Golden snapshots are never regenerated.** There is no generator; a golden failure is fixed in the document or the engine (§7).
