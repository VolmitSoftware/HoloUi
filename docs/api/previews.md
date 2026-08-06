# Container previews

A container preview is the holographic card HoloUi draws when a player looks at a chest, furnace, hive,
cauldron, jukebox, minecart or shelf. Previews are **not menus** — they never open a session, never set
`%holoui_menu_open%`, and the `art.arcane.holoui.api` menu types have nothing to do with them.

Every preview is a JSON document. The shipped defaults are extracted to `plugins/holoui/previews/` on
first start; editing one takes effect within a few ticks, no reload command and no restart.

This file is the complete normative reference for the format: every object, every field, every default,
every validation rule, the whole expression language, the whole variable catalog, and the chrome
arithmetic. Nothing else needs to be read to author a document.

| Path                                 | Contains                                             |
|--------------------------------------|------------------------------------------------------|
| `plugins/holoui/previews/*.json`     | Every preview document, shipped and user-authored    |
| `schema/holoui-preview.schema.json`  | JSON Schema for the format, documentation grade      |
| `src/main/resources/previews/*.json` | The thirteen shipped documents, in the jar           |
| `src/test/resources/preview-variables.json` | Machine-readable copy of the variable catalog |
| `src/test/resources/expr_test_vectors.json` | Pinned expression semantics (Java/Dart parity) |

The Java parser (`PreviewDocumentParser`) is the format's source of truth. The schema documents it; it
does not define it. Where the two disagree, the parser is right and the schema is a bug.

---

## 1. Document skeleton

One document per file. The file's base name (without `.json`) is the document name used by
`/holoui previews`, by log lines, and by tie-breaking.

Minimal document — no chrome, one static swatch:

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

| Key        | Type     | Required | Default | Notes                                                              |
|------------|----------|----------|---------|--------------------------------------------------------------------|
| `match`    | object   | no       | empty   | Absent match claims nothing; the document is never resolved         |
| `variants` | object[] | no       | `[]`    | Alternate `vars` sets; **also extend matching**                     |
| `card`     | object   | no       | none    | Absent means no chrome at all                                       |
| `elements` | object[] | no       | `[]`    | Drawn in list order. A document that emits nothing draws no preview |

A document whose build produces zero elements yields no preview session at all — the raycast falls
through as if nothing matched.

Unknown top-level keys are ignored (Gson binds by field name; anything else is dropped silently).

---

## 2. `match`

| Key        | Type     | Required | Default | Notes                                                                    |
|------------|----------|----------|---------|--------------------------------------------------------------------------|
| `blocks`   | string[] | no       | `[]`    | Block material names. Uppercased before matching. `*` is the only wildcard |
| `entities` | string[] | no       | `[]`    | Entity type names. Same rules                                            |
| `special`  | string   | no       | `null`  | `enderChest`, `locked` or `anyInventoryHolder`                           |
| `priority` | int      | no       | `0`     | Highest wins. Shipped documents use `10`                                 |
| `vars`     | object   | no       | `{}`    | Constants the document reads as `vars.<name>`                            |

### Names and globs

Entries are uppercased with `Locale.ROOT` before anything else. An entry containing `*` compiles to a
glob; every other entry is an exact name.

- Glob compilation: the entry is split on `*` (keeping trailing empties), each literal segment is
  `Pattern.quote`d, and `*` becomes `.*`, anchored `^…$`. So `*_SHULKER_BOX` matches
  `RED_SHULKER_BOX`; `*COPPER_CHEST` matches `COPPER_CHEST`, `EXPOSED_COPPER_CHEST`, and so on. `*`
  matches zero or more characters — there is no `?`, no character class, no alternation.
- An exact name that is not a known `Material` (for `blocks`) or `EntityType` (for `entities`) logs a
  warning and **still compiles**: `<doc>: unknown block material 'X' at match.blocks[0], still
  compiling`. This is deliberate — a name from a future game version or a modded server keeps working
  once it exists.
- Glob entries are never checked against the known name set.
- A `null` array entry rejects the document: `match.blocks[2]: must be a string, got null`.

### `special`

Three markers name documents the plugin looks up by role rather than by target. Only the top-level
match's `special` is read; a variant's is ignored.

- `enderChest` — the preview is built from the *viewer's own* ender chest inventory, not from a tile
  entity. The session takes this path when the document that wins the ordinary block resolution for
  the looked-at block carries the marker. A higher-priority user document naming `ENDER_CHEST`
  without the marker therefore gets the normal block path instead, and deleting `special` from
  `ender_chest.json` makes ender chests behave like any other container rather than stranding them.
- `locked` — the target-less card shown when a viewer may not open the container. Built against a
  **statics** scope: no inventory, no block, no entity. Only `time`, `blockType` (empty string),
  `customName` (empty string) and `vars.*` resolve; every `slot` element is skipped with
  `slot: target has no inventory`.
- `anyInventoryHolder` — the entity fallback. Matches any `Entity` that is an `InventoryHolder` and
  that no document names by type, at grade `fallback` (the weakest grade).

Any other value rejects the document:
`match.special: must be one of enderChest, locked, anyInventoryHolder, got 'foo'`.

A `special` document is looked up by marker alone. If two documents carry the same marker, the higher
`priority` wins and a tie warns. The vars handed to a `special` lookup are the document-level `vars`
**unmerged** — a special target has no material or entity type for a variant to key off.

---

## 3. `variants`

A variant reuses the match shape, but only `blocks`, `entities` and `vars` are read. `special` and
`priority` on a variant are silently ignored.

| Key        | Type     | Required | Default | Notes                                    |
|------------|----------|----------|---------|------------------------------------------|
| `blocks`   | string[] | no       | `[]`    | Same rules as `match.blocks`             |
| `entities` | string[] | no       | `[]`    | Same rules as `match.entities`           |
| `vars`     | object   | no       | `{}`    | Merged **over** the document's own `vars` |

Element templates are unchanged by a variant. One `furnace.json` draws a furnace, a blast furnace and
a smoker from identical geometry with three palettes.

**Variants extend matching as well as restyling it.** The registry grades a target against the base
match *and* every variant, so a material named only in a variant makes the whole document resolvable
for it — it does not also have to appear in `match.blocks`. `chest.json` is the shipped example: its
base match names only `CHEST`, `TRAPPED_CHEST` and `BARREL`, while every shulker box and the
`*COPPER_CHEST` glob live in variants and are matched through them.

Variants are tried in **declaration order** and the first match wins, so an earlier variant takes an
overlap. A target no variant claims gets the document's own vars unchanged.

Selection is by name only: `varsForBlock(material)` walks the variants testing
`exactBlocks.contains(name) || anyGlob(name)`; `varsForEntity(entity)` does the same against
`entities`. A `null` array entry in `variants` rejects the document:
`variants[1]: must be an object, got null`.

---

## 4. `vars`

Author-declared constants, read in expressions as `vars.<name>`. Declared in `match.vars` and/or any
variant's `vars`; the union of every declared name across the document and all its variants is what
`vars.<name>` may reference at compile time.

Values are JSON primitives only, converted to:

| JSON        | Runtime value |
|-------------|---------------|
| number      | `Double`      |
| boolean     | `Boolean`     |
| string      | `String` — *see the `#` rule* |

Anything else — an object, an array, an explicit `null` — rejects the document:
`match.vars.foo: must be a number, boolean, or string constant`.

**A string var is never parsed as an expression.** `vars.titleKey` is that exact literal. The one
exception is a string whose first character is `#`: it is parsed with the expression language's own
colour-literal grammar and arrives as the unsigned ARGB number JSON cannot express without losing the
alpha byte to a signed int.

```
"accent": "#FFB02E26"     ->  vars.accent is the number 0xFFB02E26
"stateColor": "<#F2A535>" ->  plain text; it does not lead with '#'
"note": "#ZZZ"            ->  compile error, document rejected
```

A leading `#` that is not a valid colour literal fails to compile rather than silently rendering as
text: `match.vars.note: invalid color literal '#ZZZ': bad hex length at 0`. A leading `#` that parses
to something other than a bare number (impossible with the current lexer, but checked) fails the same
way.

Vars have their own namespace. `vars.size` can never shadow `inventory.size`, and a state variable can
never shadow a var, because vars are only reachable under the `vars.` prefix.

---

## 5. `card`

Omit the whole `card` object for bare content with no chrome. Declaring one at all means asking for the
chrome, which is why `framed` defaults to `true` inside it.

| Key            | Type              | Required | Default        | Notes                                          |
|----------------|-------------------|----------|----------------|------------------------------------------------|
| `framed`       | bool / expression | no       | `true`         | Draw the chrome at all. Evaluated once at build |
| `title`        | expression string | no       | none (empty)   | Title text. Evaluated once at build            |
| `accent`       | expression string | no       | `0xFFCBD0D9`   | Chrome accent, low 24 bits only. Once at build  |
| `minHalfWidth` | int               | no       | `82`           | Minimum panel half-width in pixels             |

- `title` and `accent` are **always expression source strings**, never JSON constants. Write a literal
  as `"title": "'Chest'"`, not `"title": "Chest"` (which would parse as the variable `Chest`).
- `framed` accepts a JSON boolean **or** an expression string.
- `minHalfWidth` is a plain JSON integer — no expression form.
- `title` is parsed by `TextUtils.parse` (legacy `&` codes, then MiniMessage) into one Component.
- `accent`'s alpha byte is discarded; the framer rebuilds alpha from its own constants.
- When `framed` evaluates false, `title`, `accent` and `minHalfWidth` are not used at all.
- If `framed` throws at build, the card is treated as unframed and the error reported.
- If `title` throws, the title is empty and the error reported. If `accent` throws, the default grey is
  used and the error reported.

A framed card emits its chrome **first**, so a built card reads: frame, panel, tray (only when the
content has at least one cell or slot), title bar, title, then your elements. See [§12](#12-chrome-the-card-framer).

---

## 6. `elements`

Drawn in list order. `x` is pixels right of centre, `y` is pixels up from centre, `z` is depth (higher
draws in front). All three are rounded to `int` with `Math.round` after evaluation.

Every type accepts `x`, `y`, `z`, `visible` and `repeat`.

| `type`  | Required fields          | Also accepts                       | Default `z` |
|---------|--------------------------|------------------------------------|-------------|
| `panel` | `width` `height` `color` | —                                  | `1`         |
| `cell`  | `size` `color`           | —                                  | `4`         |
| `slot`  | `size` `index`           | `wellColor` (default `#FF15151B`)  | `4`         |
| `label` | `text`                   | `background` (default transparent) | `6`         |

### Common fields

| Key       | Type                 | Required | Default        | Notes                                              |
|-----------|----------------------|----------|----------------|----------------------------------------------------|
| `type`    | string               | **yes**  | —              | `panel`, `cell`, `slot`, `label`. Case-sensitive    |
| `x`       | number / expression  | no       | `0`            | Pixels right of card centre                         |
| `y`       | number / expression  | no       | `0`            | Pixels up from card centre                          |
| `z`       | number / expression  | no       | per type above | Depth; see [§13](#13-layout-and-rendering-model)    |
| `visible` | bool / expression    | no       | `true`         | Evaluated **once at build**; false skips the element |
| `repeat`  | object               | no       | none           | See [§7](#7-repeat)                                 |

A missing or unrecognised `type` rejects the document:
`elements[0].type: must be one of panel, cell, slot, label, got 'box'`. A `null` entry in the array
rejects too: `elements[3]: must be an object, got null`.

### `panel`

| Key      | Type                | Required | Default | Notes                    |
|----------|---------------------|----------|---------|--------------------------|
| `width`  | number / expression | **yes**  | —       | Pixels                   |
| `height` | number / expression | **yes**  | —       | Pixels                   |
| `color`  | colour / expression | **yes**  | —       | ARGB. Evaluated **once** |

A flat rectangle. `panel.color` is not live.

### `cell`

| Key     | Type                | Required | Default | Notes                                  |
|---------|---------------------|----------|---------|----------------------------------------|
| `size`  | number / expression | **yes**  | —       | Square edge in pixels                  |
| `color` | colour / expression | **yes**  | —       | ARGB. **LIVE** — re-evaluated every 4 ticks |

The unit every gauge, flame, bar segment and padlock is built from. `cell.color` is one of only two
live fields in the whole format.

### `slot`

| Key         | Type                | Required | Default      | Notes                                       |
|-------------|---------------------|----------|--------------|---------------------------------------------|
| `size`      | number / expression | **yes**  | —            | Square edge in pixels                       |
| `index`     | number / expression | **yes**  | —            | Inventory slot index. Evaluated **once**    |
| `wellColor` | colour / expression | no       | `#FF15151B`  | Well behind the item. Evaluated **once**    |

Renders the item in inventory slot `index`, plus a bold white stack-count label when the amount is
`> 1`. The item and its count *are* re-read every 4 ticks by the renderer even though `index` is
fixed — the well moves and re-indexes never, the item in it changes freely.

Nothing clamps `index`. Guard it against `inventory.size` yourself, as every shipped document does
(`min(vars.cols * vars.rows, inventory.size)`). An out-of-range index simply renders an empty well.

If the target has no inventory, the element is skipped with `slot: target has no inventory` and the
rest of the document still draws.

### `label`

| Key          | Type                | Required | Default       | Notes                                          |
|--------------|---------------------|----------|---------------|------------------------------------------------|
| `text`       | expression string   | **yes**  | —             | **LIVE** — re-evaluated every 4 ticks           |
| `background` | colour / expression | no       | `0` (transparent) | Text background. Evaluated **once**        |

`text` is always an expression source string, never a JSON constant — `"text": "Idle"` parses as the
variable `Idle` and rejects the document. Write `"text": "'Idle'"`.

The evaluated string goes through `TextUtils.parse`: legacy `&` codes are translated, each resulting
`§x` is rewritten to the equivalent MiniMessage tag, and the whole thing is deserialized with
MiniMessage. See [§15.4](#154-the-multi-styled-label-idiom).

A missing `text` rejects the document: `elements[1].text: required for type label`.

---

## 7. `repeat`

Emits the element once per index, with the index bound to a loop variable every field of that element
can read.

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

| Key     | Type                | Required | Default | Notes                              |
|---------|---------------------|----------|---------|------------------------------------|
| `count` | number / expression | **yes**  | —       | Evaluated **once at build**        |
| `var`   | string              | no       | `"i"`   | Loop variable name; 0-based index  |

Rules and limits:

- `count` may be an expression, so a grid sizes itself from `inventory.size`. It is evaluated once when
  the preview is built, **not** per frame. An empty or explicitly-null `count` rejects the document:
  `elements[0].repeat.count: required`.
- `count` is compiled with an **empty scope** — it cannot reference its own loop variable.
- A count of `raw` produces `floor(raw)` instances; anything `< 1` (including `NaN`) produces zero.
- **Constant** count above `1024` is a compile error and the document is rejected:
  `elements[0].repeat.count: constant repeat count 2000 exceeds 1024`.
- **Compiled total** above `4096` rejects the document. The total is the sum over elements of
  `floor(constantCount)` for constant repeats and `1` for everything else:
  `elements: total compiled template count 5000 exceeds 4096`.
- **Dynamic** count is unknowable at compile time, so it is truncated to `1024` at build with an error
  reported against the document: `repeat count 5000 exceeds 1024, truncated`.
- The 4096 cap is re-enforced across the whole build. A repeat that would cross it is truncated
  (`repeat of 900 truncated at the 4096 element cap`), every element after it is skipped
  (`element cap 4096 reached, remaining elements skipped`), and the build returns what it managed.
  The budget counts *attempts*, so invisible elements still cost.
- `var` must match `^[a-zA-Z_][a-zA-Z0-9_]*$`; otherwise
  `elements[0].repeat.var: must be a valid identifier, got '2x'`.
- `var` may not be `vars`, nor any name in the state-variable catalog (`time`, `blockType`,
  `customName`, `inventory.size`, `inventory.occupied`, `cookTime`, … — the full flat catalog). Such a
  name would resolve to the state namespace before the loop variable and be unreachable, so it fails
  to compile: `elements[0].repeat.var: 'time' collides with a reserved variable name and would be
  unreachable`.
- Each instance gets its own scope holding its own index. The live `cell.color` / `label.text`
  closures capture that scope, so instance 3 still reads `3` long after the loop finished.

---

## 8. Field value forms, folding, and validation

### 8.1 The three field shapes

| Shape                  | Accepts                                            | Fields                                                   |
|------------------------|----------------------------------------------------|----------------------------------------------------------|
| number-or-expression   | JSON number, or a string parsed as an expression   | `x` `y` `z` `width` `height` `size` `index` `color` `wellColor` `background` `repeat.count` |
| bool-or-expression     | JSON boolean, or a string parsed as an expression  | `visible` `card.framed`                                  |
| expression-only string | a string, always parsed as an expression           | `label.text` `card.title` `card.accent`                  |

Passing the wrong JSON type is a compile error with a specific message:

```
elements[0].width: must be a number or a string expression, got a boolean
elements[0].visible: must be a boolean or a string expression, got a number
elements[0].color: must be a number or a string expression        (object/array)
```

The three expression-only fields are the exception to that table: they are plain `String` on the DTO,
so Gson **coerces** a JSON number or boolean into its string form instead of rejecting it. `"text": 5`
compiles and renders `5`; `"title": true` parses as the keyword `true`; `"accent": 16711680` parses as
that number. Nothing warns. Write the quoted expression you mean — `"text": "'5'"` — rather than
relying on the coercion.

Gson binds an explicit JSON `null` to `JsonNull`, not to a Java `null`, and the parser treats both the
same. For a **required** field, absent and `null` both reject:
`elements[0].width: required for type panel`. For an **optional** field, absent and `null` both take
the default.

Colours as JSON numbers are the unsigned 32-bit ARGB value, so `"color": 16711680` is `0x00FF0000` —
**fully transparent red**. A bare JSON number is the only way to write a colour whose alpha byte is
zero; every string form (`#RGB`, `#RRGGBB`, `rgb()`) forces alpha to `FF`.

### 8.2 Constant folding

An expression containing no variable reference and no function call is *constant*. Constants are
evaluated at compile time against an empty scope and the value is stored alongside the tree. At build
and at render, the stored value is used without re-walking the tree.

Consequences:

- A constant expression that throws is a **compile error**: `"x": "1 / 0"` rejects the document with
  `elements[0].x: division by zero`.
- `str(1)` is a *call*, so it is **not** constant. Anything mentioning `lang(...)`, `readable(...)`,
  `occupied(...)` or any other function is non-constant even if its arguments are literals.
- A folded-constant `cell.color` or `label.text` is resolved once at build and the same value (or the
  same parsed Component) is handed back by every poll. A constant label never re-runs MiniMessage.
- Folding does **not** type-check. `"visible": "1"` folds to the number `1`, compiles fine, then fails
  at build with `expected boolean, got number` and the element is skipped. `"x": "'abc'"` behaves the
  same way with `expected number, got string`.

### 8.3 Variable-name validation at compile time

Every variable reference in every expression is checked when the document compiles, in this order:

1. The name is in the flat state catalog (`time`, `blockType`, `customName`, `inventory.size`,
   `inventory.occupied`, and every category's names — the union, regardless of which category the
   document will actually run against) → **accepted**.
2. The name starts with `vars.` → the suffix must be declared in `match.vars` or in some variant's
   `vars`, else **rejected**: `elements[0].color: unknown variable: vars.fil`.
3. The name has no dot → it must be the enclosing element's repeat variable, else **rejected**:
   `elements[0].x: unknown variable: j`.
4. The name is dotted and its prefix is a reserved namespace (`vars`, `inventory`, `surge`, or any
   full catalog name) → **rejected**: `elements[0].color: unknown variable: surge.rate`.
5. The name is dotted with any other prefix → assumed to be a provider namespace, **warned** and
   compiled: `<doc>: elements[0].color references provider namespace 'myplugin.charge', not verifiable
   at parse time`.

Note that step 1 accepts catalog names from *any* category. A cauldron document may reference
`cookTime` and compile; it will fail at render with `unknown variable: cookTime` because a cauldron
context never publishes it.

Function names are not validated at compile time. An unknown function fails at evaluation with
`unknown function: foo`.

### 8.4 Failure taxonomy

| Situation                                              | Outcome                                             |
|--------------------------------------------------------|-----------------------------------------------------|
| Malformed JSON                                          | Document rejected: `malformed JSON: …`             |
| Empty file / JSON `null`                                | Document rejected: `empty document`                |
| Bad `special`, bad `type`, null array entry             | Document rejected                                   |
| Bad var value, bad `#` colour var                       | Document rejected                                   |
| Missing required element field                          | Document rejected                                   |
| Wrong JSON type for a field                             | Document rejected                                   |
| Expression parse error                                  | Document rejected: `… at <charPosition>`           |
| Constant expression throws while folding                | Document rejected                                   |
| Unknown variable (per §8.3 rules 2–4)                   | Document rejected                                   |
| Unknown material / entity name (exact, non-glob)        | **Warning**, compiles                               |
| Unresolvable dotted name with non-reserved prefix       | **Warning**, compiles                               |
| Element's build-time expression throws                  | That element skipped, throttled log, build continues |
| `slot` on a target with no inventory                    | That element skipped, throttled log                  |
| Live `cell.color` throws at render                      | Cell renders `0x00000000` (transparent), throttled log |
| Live `label.text` throws at render                      | Label renders empty, throttled log                  |
| Repeat count over 1024 at build                         | Truncated to 1024, error reported                   |
| 4096 element budget exhausted                           | Repeat truncated, remaining elements skipped        |
| Anything else during build                              | Build returns an empty list → no preview            |

A rejected document logs `previews/<name>.json: <message>` and is skipped. On a **reload**, the
previously compiled version stays live, so a half-saved edit never blanks a preview.

Build-time and render-time errors log at most **one line per document per minute**
(`<name> <message>` at `WARNING`), because the alternative is a log line every four ticks for as long
as the player looks at the block. `/holoui previews dump` bypasses the throttle through its own
per-invocation error sink, so a dump always shows that dump's errors.

---

## 9. Expression language

One expression per field. Hand-written lexer, recursive-descent parser and tree-walking evaluator; no
scripting engine, no sandbox escape, no user-defined names, no assignment, no loops.

### 9.1 Lexical grammar

Whitespace (any `Character.isWhitespace`) separates tokens and is otherwise ignored. There are no
comments.

**Numbers** — `[0-9]+` optionally followed by `.` and `[0-9]+`. A digit is required on both sides of
the dot: `.5` and `1.` are not numbers. There is no exponent notation, no hex except the `#` form, no
underscores, no leading `+`/`-` (those are unary operators). All numbers are IEEE-754 doubles.

**Colour literals** — `#` followed by exactly 3, 6 or 8 hex digits (case-insensitive). Any other run
length is a parse error `bad hex length`. A colour literal *is a number token*:

| Written      | Value        | Rule                                     |
|--------------|--------------|------------------------------------------|
| `#RGB`       | `0xFFRRGGBB` | Each digit doubled, then prefixed `FF`   |
| `#RRGGBB`    | `0xFFRRGGBB` | Prefixed `FF`                            |
| `#AARRGGBB`  | as written   | Carries its own alpha                    |

So `#F00 == rgb(255,0,0)` is `true`, and `#FF0000FF == rgb(0,0,255)` is `true`. Only a bare JSON
number can express a colour whose alpha byte is zero.

**Strings** — single- or double-quoted; `'idle'` and `"idle"` are identical, and single quotes read
better inside JSON. The escape set is exactly:

| Escape | Produces   |
|--------|------------|
| `\\`   | backslash  |
| `\'`   | apostrophe |
| `\"`   | quote      |
| `\n`   | newline    |

Any other escape is a parse error (`unrecognized escape sequence`). An unterminated string is a parse
error (`unterminated string`). Remember JSON eats one level of backslash first: a document field
holding the expression `'a\\nb'` is written `"'a\\\\nb'"` in the file.

**Identifiers** — start with a letter or `_`, continue with letters, digits or `_`. A dotted name is
allowed: after a segment, a `.` is consumed only when the next character is a letter or `_`, and the
next segment is scanned the same way. `surge.active`, `inventory.size`, `vars.accent`,
`myplugin.charge` are one token each. `a.1` is not — the `.` is left behind and becomes an unexpected
token.

`true` and `false` are keywords, not identifiers.

**Operators and punctuation** — `( ) [ ] , ? : + - * / % ! != == < <= > >= && ||`. A lone `=` is a
parse error; `&` must be `&&`; `|` must be `||`. Every other character is `unexpected token`.

**Nesting cap** — live recursion through the ternary and unary productions (including through
parentheses, brackets and call arguments, which all re-enter the ternary production) is capped at
**256**. Deeper input is a parse error, `expression too deeply nested`, rather than a
`StackOverflowError` the loader could not catch.

An empty source string is a parse error (`empty source`). Trailing tokens after a complete expression
are a parse error (`unexpected token`).

### 9.2 Grammar

```
program      := ternary EOF
ternary      := or ( '?' ternary ':' ternary )?
or           := and ( '||' and )*
and          := equality ( '&&' equality )*
equality     := relational ( ('==' | '!=') relational )*
relational   := additive ( ('<' | '<=' | '>' | '>=') additive )*
additive     := multiplicative ( ('+' | '-') multiplicative )*
multiplicative := unary ( ('*' | '/' | '%') unary )*
unary        := ('-' | '!') unary | primary
primary      := NUMBER | STRING | 'true' | 'false'
              | '(' ternary ')'
              | '[' ( ternary (',' ternary)* )? ']'
              | IDENT '(' ( ternary (',' ternary)* )? ')'
              | IDENT
```

Precedence, lowest to highest:

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

All binary operators are left-associative. The ternary is right-associative in both branches.

**Array literals** `[a, b, c]` exist only to feed `palette`. There is no indexing operator, no `length`,
and passing a list anywhere else is a type error.

**Call names are never dotted.** `vars.f(1)` is a parse error, `call names cannot be dotted`. An
identifier is a call if and only if the very next token is `(`.

### 9.3 Runtime values and semantics

Exactly four runtime types: `Double`, `String`, `Boolean`, `List<Object>`. **All numbers are doubles.**
There is no integer type; `floor`, `ceil`, `round` return doubles too.

| Operator      | Semantics                                                                           |
|---------------|-------------------------------------------------------------------------------------|
| `+`           | Concatenation when **either** side is a string, otherwise numeric addition           |
| `-` `*`       | Numbers only                                                                        |
| `/`           | Numbers only. **Throws** `division by zero` on a zero right operand                  |
| `%`           | Java truncating remainder. **Throws** `division by zero` on a zero right operand     |
| `==` `!=`     | Same-type only: number/number, string/string, boolean/boolean. Mixed types **throw** |
| `<` `<=` `>` `>=` | Numbers only. `'a' < 'b'` throws                                                |
| `&&` `\|\|`   | Booleans only, **short-circuiting**                                                  |
| `!`           | Boolean only                                                                        |
| unary `-`     | Number only                                                                          |
| `? :`         | Condition must be a **boolean**; `1 ? 2 : 3` throws                                  |

There is no truthiness. `if`-like branching always needs a real boolean: write `burnTime > 0`, never
`burnTime`.

`&&` and `||` short-circuit properly — the right operand is not evaluated, so its variables are not
looked up and its functions are not called, when the left operand already decides the result. This is
the safe way to guard a variable that might be missing in some contexts, and the safe way to avoid a
division by zero:

```
cookTimeTotal > 0 && cookTime / cookTimeTotal > 0.5 ? vars.fill : vars.idle
```

**Stringification (the integral-string rule)** — a double `d` renders without a decimal point when
`Double.isFinite(d) && d == Math.rint(d)`, i.e. as a `long`; otherwise as `Double.toString(d)`.
Booleans render `"true"`/`"false"`; strings pass through. So `'x' + 5` is `"x5"`, `'n=' + 3.5` is
`"n=3.5"`, `str(54.0)` is `"54"`, `'flag:' + true` is `"flag:true"`. A list cannot be stringified and
throws.

**`%` and `mod` are not the same.** `%` is Java's truncating remainder, so it keeps the sign of the
left operand: `-1 % 3` is `-1`. `mod(a, b)` uses floor semantics and always returns a non-negative
result for a positive divisor: `mod(-1, 3)` is `2`. Use `mod` for wraparound on a value that can go
negative — a chase index, a palette cycle, a grid column — and `%` only when a negative result is what
you want. Both throw on a zero divisor. Both behaviours are pinned in
`src/test/resources/expr_test_vectors.json`.

**Colours are numbers.** A colour is the unsigned 32-bit ARGB value carried in a double; channel order
is alpha, red, green, blue from most to least significant byte. Colour fields narrow it with
`(int)(long) value` — going through `long` truncates the bits instead of saturating at `0x7FFFFFFF`,
which a plain `(int)` cast on the double would do, ruining every colour from alpha `0x80` up.

**Errors.** Every failure is an `ExprException`. Parse errors carry a character position in the source;
evaluation errors do not (there is no source text left to point at) and report position `-1`. An
unknown variable throws `unknown variable: <name>`; an unknown function throws
`unknown function: <name>`. An evaluation error is contained per element — see §8.4.

### 9.4 Standard function library

Fixed arity; the wrong count throws `<name> expects N argument(s), got M`. A wrong argument type
throws `<name> argument K must be a number` / `… must be a string` (K is 1-based).

| Function                     | Returns | Exact semantics                                                                 |
|------------------------------|---------|----------------------------------------------------------------------------------|
| `clamp(x, lo, hi)`           | number  | `min(max(x, lo), hi)`. No check that `lo <= hi`                                  |
| `lerp(a, b, t)`              | number  | `a + (b - a) * t`. **Not** clamped                                               |
| `min(a, b)`                  | number  | Exactly two arguments                                                            |
| `max(a, b)`                  | number  | Exactly two arguments                                                            |
| `floor(x)`                   | number  | `Math.floor`                                                                     |
| `ceil(x)`                    | number  | `Math.ceil`                                                                      |
| `round(x)`                   | number  | `Math.round` — **half-up toward positive infinity**: `round(2.5)` is `3`, `round(-2.5)` is `-2` |
| `abs(x)`                     | number  | `Math.abs`                                                                       |
| `mod(a, b)`                  | number  | `a - floor(a / b) * b`. Throws `division by zero` when `b == 0`                   |
| `sin(x)`                     | number  | Radians                                                                          |
| `cos(x)`                     | number  | Radians                                                                          |
| `rgb(r, g, b)`               | colour  | Opaque (`alpha = 0xFF`). Each channel `round`ed then clamped to `0..255`          |
| `argb(a, r, g, b)`           | colour  | Same channel handling, explicit alpha                                            |
| `alpha(color, a)`            | colour  | `(color & 0x00FFFFFF) \| (clampedA << 24)` — replaces the alpha byte. `a` is `0..255` (255 = opaque), clamped; it is NOT a `0..1` fraction — `alpha(c, 0.5)` rounds to alpha 1, near-invisible |
| `mix(c1, c2, t)`             | colour  | Per-channel **including alpha**: `round(a + (b - a) * t)`; `t` clamped to `0..1`  |
| `palette([c, …], index)`     | number  | `list[floorMod((int) floor(index), list.size())]`. List must be non-empty and all numbers |
| `str(x)`                     | string  | The integral-string rule; `42.0` → `"42"`                                        |
| `fixed(x, digits)`           | string  | `String.format(Locale.ROOT, "%.<digits>f", x)`. `digits` must be a **whole number in `0..20`** |
| `plain(text)`                | string  | Removes every match of `&[0-9A-Fa-fK-Ok-oRr]`, leaves everything else            |
| `readable(id)`               | string  | `IRON_ORE` → `Iron Ore`; see below                                               |

Channel clamping is `round` then clamp, so `rgb(300, -10, 128)` equals `rgb(255, 0, 128)`.

`palette` wraps in both directions: `palette([1,2,3], 4)` is `2`, `palette([10,20,30], -1)` is `30`.
An empty list throws `palette list must not be empty`; a non-number entry throws
`palette list entries must be numbers`; a non-list first argument throws
`palette argument 1 must be a list`.

`fixed` rejects a fractional or out-of-range precision:
`fixed argument 2 (digits) must be a whole number in [0, 20]`. The `0..20` window is the intersection
of Java's and Dart's accepted ranges, so both implementations of the contract agree.

`plain` strips *only* legacy colour/format codes — `&` followed by a hex digit, `k`–`o` or `r`, either
case. `plain('&6&lChest')` is `"Chest"`; `plain('Salt & Pepper')` is unchanged. It does not touch
MiniMessage tags.

`readable` lowercases with `Locale.ENGLISH`, splits on `_`, and title-cases each word joined by a
single space. Java's `split("_")` drops trailing empty strings, so the edges are:

| Input        | Output      |
|--------------|-------------|
| `IRON_ORE`   | `Iron Ore`  |
| `chest`      | `Chest`     |
| `""`         | `""`        |
| `IRON_`      | `Iron`      |
| `_IRON`      | `" Iron"` (leading space) |
| `IRON__ORE`  | `"Iron  Ore"` (two spaces) |

### 9.5 Context functions

Resolved by the live scope before the standard library, so they cannot be shadowed and are always
available (they simply return neutral values when the target has no inventory).

| Function          | Returns | Semantics                                                                    |
|-------------------|---------|------------------------------------------------------------------------------|
| `lang(key, …)`    | string  | Localized message; see §9.6                                                  |
| `count(slot)`     | number  | Stack size in that slot; `0` when empty, out of range, or no inventory        |
| `occupied(slot)`  | boolean | `true` when the slot holds a non-empty stack                                  |
| `item(slot)`      | string  | Material **id** (`"IRON_ORE"`); `""` when empty, out of range, or no inventory |

`count`, `occupied` and `item` take exactly one numeric argument, floored to an int
(`<name> expects 1 argument(s), got N` / `<name> argument 1 must be a number`). A stack is "empty" when
it is null, `AIR`, or has amount `< 1`. `item` returns ids rather than display text, matching
`blockType`; for text a document writes `readable(item(0))`.

### 9.6 `lang` and the message catalog

`lang(key, …)` resolves `key` through HoloUi's localization chain, so a running server renders the
active locale and a headless build renders the English default.

An id the catalog does not know behaves differently in the two cases, and only the second is forgiving:

- **On a running server** the lookup is strict. The catalog throws and `lang` fails with
  `lang: Unknown message key: <id>`, so the element is skipped and the error reported like any other
  render-time throw (§8.4). A typo'd key is a blank label, not a visible key name.
- **Headless** (no plugin instance — `/holoui previews dump` on a build server, the test suite) there
  is no catalog to consult and the id renders **as itself**.

Use keys from the table below, or ones your own plugin registers. Do not rely on an unknown key
rendering as its own name.

Placeholder binding: positional arguments after the key are bound onto the **English template's** own
placeholder names, scanned left to right, in **first-appearance order**, **deduplicated**. Argument 1
fills the first `{name}`, argument 2 the second, and so on. Arguments past the last placeholder are
named `arg0`, `arg1`, … by position and simply go unused. `{{` is an escape and is skipped by the
scanner. An unterminated `{` ends the scan.

Values are stringified with the expression language's own rule (`42.0` inserts as `"42"`) and bound as
**untrusted** arguments. Untrusted only means the section-sign form is removed: the value is passed
through `ChatColor.stripColor` and any remaining `§` is deleted. It does **not** neutralize legacy `&`
codes or MiniMessage tags, and the label/title pipeline translates `&` to `§` and runs MiniMessage
*after* `lang` returns — so a value carrying `&c` or `<red>` **does** recolour the line.

Sanitize hostile values in the document. `plain()` strips the `&` codes; nothing in the format strips
MiniMessage tags, so a value that may contain `<` is only safe if you control it:

```
'&7' + plain(lang(vars.titleKey, plain(customName)))
```

The shipped `chest.json` title goes further and does not route a player-set name through `lang` at all
(`customName != '' ? customName : …`), which means that name reaches `TextUtils.parse` raw — a chest
renamed `&cLoot` draws a red title. Treat `customName` as untrusted input in any document you write.

This is why the furnace state line passes the percentage twice:

```
lang(occupied(0) ? vars.activeItemKey : vars.activeKey,
     occupied(0) ? readable(item(0)) : round(cookTime * 100 / cookTimeTotal),
     round(cookTime * 100 / cookTimeTotal))
```

`holoui.preview.state.smelting_item` is `"Smelting {item} {percent}%"` (two placeholders, both used);
`holoui.preview.state.smelting` is `"Smelting {percent}%"` (one placeholder — argument 1 fills
`{percent}` and argument 2 becomes the unused `arg1`).

`lang` with no arguments throws `lang expects at least 1 argument (the message key), got 0`; a
non-string key throws `lang argument 1 (key) must be a string`.

**Every `holoui.preview.*` key, with its English template.** All seventeen shipped locales carry the
same keys with the same placeholder names, so a document that calls `lang` correctly is translated for
free.

| Key                                          | English template                                       |
|----------------------------------------------|--------------------------------------------------------|
| `holoui.preview.state.idle`                  | `Idle`                                                 |
| `holoui.preview.state.brewing`               | `Brewing {percent}%`                                   |
| `holoui.preview.state.needs_blaze_powder`    | `Needs blaze powder`                                   |
| `holoui.preview.state.waiting`               | `Waiting`                                              |
| `holoui.preview.state.no_ingredient`         | `No ingredient`                                        |
| `holoui.preview.state.empty`                 | `Empty`                                                |
| `holoui.preview.state.smelting_item`         | `Smelting {item} {percent}%`                           |
| `holoui.preview.state.smelting`              | `Smelting {percent}%`                                  |
| `holoui.preview.state.blasting_item`         | `Blasting {item} {percent}%`                           |
| `holoui.preview.state.blasting`              | `Blasting {percent}%`                                  |
| `holoui.preview.state.smoking_item`          | `Smoking {item} {percent}%`                            |
| `holoui.preview.state.smoking`               | `Smoking {percent}%`                                   |
| `holoui.preview.state.heating`               | `Heating`                                              |
| `holoui.preview.state.needs_fuel`            | `Needs fuel`                                           |
| `holoui.preview.state.no_input`              | `No input`                                             |
| `holoui.preview.state.surge_suffix`          | `␣␣+{seconds}s` (two leading spaces)                   |
| `holoui.preview.state.disc_playing`          | `Playing {disc}`                                       |
| `holoui.preview.state.disc_loaded`           | `Loaded {disc}`                                        |
| `holoui.preview.state.no_disc`               | `No disc`                                              |
| `holoui.preview.stat.fuel_level`             | `Fuel {fuel}/{maximum}`                                |
| `holoui.preview.stat.no_fuel`                | `No fuel`                                              |
| `holoui.preview.stat.bottles`                | `Bottles {bottles}/{maximum}`                          |
| `holoui.preview.stat.fuel_seconds`           | `Fuel {seconds}s`                                      |
| `holoui.preview.stat.fuel_ready`             | `Fuel ready`                                           |
| `holoui.preview.stat.xp_gain`                | `XP +{experience}`                                     |
| `holoui.preview.stat.xp_zero`                | `XP 0`                                                 |
| `holoui.preview.stat.bees_and_honey`         | `Bees {bees}/{maximumBees}␣␣␣Honey {honey}/{maximumHoney}` |
| `holoui.preview.stat.cauldron_empty`         | `Empty {level}/{maximum}`                              |
| `holoui.preview.stat.cauldron_level`         | `Level {level}/{maximum}`                              |
| `holoui.preview.theme.title.chest`           | `&6&lChest`                                            |
| `holoui.preview.theme.title.trapped_chest`   | `&c&lTrapped Chest`                                    |
| `holoui.preview.theme.title.ender_chest`     | `&5&lEnder Chest`                                      |
| `holoui.preview.theme.title.barrel`          | `&e&lBarrel`                                           |
| `holoui.preview.theme.title.dispenser`       | `&7&lDispenser`                                        |
| `holoui.preview.theme.title.dropper`         | `&8&lDropper`                                          |
| `holoui.preview.theme.title.hopper`          | `&8&lHopper`                                           |
| `holoui.preview.theme.title.furnace`         | `&6&lFurnace`                                          |
| `holoui.preview.theme.title.smoker`          | `&e&lSmoker`                                           |
| `holoui.preview.theme.title.blast_furnace`   | `&b&lBlast Furnace`                                    |
| `holoui.preview.theme.title.beehive`         | `&e&lBeehive`                                          |
| `holoui.preview.theme.title.bee_nest`        | `&e&lBee Nest`                                         |
| `holoui.preview.theme.title.cauldron`        | `&7&lCauldron`                                         |
| `holoui.preview.theme.title.water_cauldron`  | `&9&lWater Cauldron`                                   |
| `holoui.preview.theme.title.lava_cauldron`   | `&6&lLava Cauldron`                                    |
| `holoui.preview.theme.title.powder_snow_cauldron` | `&f&lPowder Snow Cauldron`                        |
| `holoui.preview.theme.title.jukebox`         | `&d&lJukebox`                                          |
| `holoui.preview.theme.title.brewing_stand`   | `&d&lBrewing Stand`                                    |
| `holoui.preview.theme.title.chiseled_bookshelf` | `&6&lChiseled Bookshelf`                            |
| `holoui.preview.theme.title.hopper_minecart` | `&8&lHopper Minecart`                                  |
| `holoui.preview.theme.title.chest_minecart`  | `&6&lChest Minecart`                                   |
| `holoui.preview.theme.title.mobile`          | `&7&l{name}`                                           |
| `holoui.preview.theme.title.shulker`         | `&l{color} Shulker`                                    |
| `holoui.preview.theme.title.copper_chest`    | `&6&l{name}`                                           |
| `holoui.preview.theme.title.shelf`           | `&6&l{name}`                                           |
| `holoui.preview.theme.title.container`       | `&6&lContainer`                                        |

The theme titles carry their own `&` codes, which is why the shipped title idiom strips them first and
applies the document's own: `'&f&l' + plain(lang(vars.titleKey))`.

---

## 10. Variable catalog

Variables are sampled once per game tick per preview from the live Bukkit target and handed to
expressions as plain values; an expression never touches a Bukkit object. The snapshot is cached and
re-sampled only when the world game time changes, so one refresh reads each getter once no matter how
many expressions reference it.

The canonical machine-readable copy of this catalog is `src/test/resources/preview-variables.json`; a
test fails when it drifts from the code.

### 10.1 Always available (`universal`)

| Name         | Type   | Meaning                                                                       |
|--------------|--------|-------------------------------------------------------------------------------|
| `time`       | number | World game time in ticks. With no world (bare inventory, statics) it is `System.currentTimeMillis() / 50` |
| `blockType`  | string | `Material.name()` of the previewed block; for an entity, the material its type name maps to (falling back to `MINECART`); `""` when neither |
| `customName` | string | Player-given name of the container or entity; a null or whitespace-only name collapses to `""` |

`blockType` and `customName` are always published, so `customName != ''` and `blockType != ''` are safe
branches in every context.

### 10.2 Whenever the target has an inventory (`inventory`)

Published for every context with a non-null inventory — including furnaces, brewing stands and
jukeboxes, which also publish their own group.

| Name                 | Type   | Meaning                            |
|----------------------|--------|------------------------------------|
| `inventory.size`     | number | `Inventory.getSize()`              |
| `inventory.occupied` | number | Count of non-empty slots           |

### 10.3 One category, chosen from the target

The category is picked once, at construction, by this dispatch order. The first branch that matches
wins:

1. `ENDER_CHEST` → `inventory`, backed by the **viewer's own** ender chest (null when there is no viewer)
2. `BrewingStand` state → `brewing`
3. `Furnace` state → `furnace`
4. `Container` state → `inventory`
5. `Jukebox` state → `jukebox`
6. any other `InventoryHolder` state → `inventory` (this is how chiseled bookshelves and shelves work; it must stay below the jukebox branch)
7. `BEEHIVE` / `BEE_NEST` → `beehive`
8. `CAULDRON` / `WATER_CAULDRON` / `LAVA_CAULDRON` / `POWDER_SNOW_CAULDRON` → `cauldron`
9. otherwise → `static` (universal group only)

Entities: an `InventoryHolder` entity → `inventory`; anything else → `static`.

**furnace**

| Name            | Type    | Meaning                                                                    |
|-----------------|---------|----------------------------------------------------------------------------|
| `cookTime`      | number  | Ticks the current smelt has been cooking (counts **up**)                    |
| `cookTimeTotal` | number  | Ticks required to finish the current smelt                                  |
| `burnTime`      | number  | Ticks of fuel remaining                                                     |
| `fuelSeconds`   | number  | `burnTime / 20` as an **integer division** — whole seconds                  |
| `bankedXp`      | number  | Sum of `recipe.getExperience() * timesUsed` over `getRecipesUsed()`; **`-1`** on a server whose `Furnace` has no `getRecipesUsed` |
| `lit`           | boolean | `burnTime > 0`                                                              |
| `surge.active`  | boolean | See §10.4                                                                   |
| `surge.gain`    | number  | See §10.4                                                                   |

`bankedXp` uses `-1` as a "not available" sentinel, which is why `furnace.json` guards with
`bankedXp >= 0` before drawing the XP fragment at all and `bankedXp > 0` for the non-zero styling.

**brewing**

| Name           | Type    | Meaning                                                    |
|----------------|---------|------------------------------------------------------------|
| `brewTime`     | number  | Ticks remaining (counts **down** to zero)                   |
| `brewTotal`    | number  | Fixed `400`; Bukkit exposes no per-brew total               |
| `fuelLevel`    | number  | Blaze powder charges remaining                              |
| `maxFuel`      | number  | Fixed `20`                                                  |
| `surge.active` | boolean | See §10.4                                                   |
| `surge.gain`   | number  | See §10.4                                                   |

Because `brewTime` counts down, progress is `clamp(1 - brewTime / brewTotal, 0, 1)`.

**beehive**

| Name       | Type   | Meaning                                                          |
|------------|--------|------------------------------------------------------------------|
| `bees`     | number | `Beehive.getEntityCount()`, floored at 0; `0` if the state is missing |
| `maxBees`  | number | `Beehive.getMaxEntities()`, floored at 1; default `3`            |
| `honey`    | number | Block data honey level; `0` if unavailable                       |
| `maxHoney` | number | Block data maximum honey level, floored at 1; default `5`        |

**cauldron**

| Name       | Type   | Meaning                                                                       |
|------------|--------|-------------------------------------------------------------------------------|
| `level`    | number | `0` for an empty `CAULDRON`; otherwise `Levelled.getLevel()` floored at 0; `3` when the data is not `Levelled` |
| `maxLevel` | number | `3` for `CAULDRON`; otherwise `Levelled.getMaximumLevel()` floored at 1        |
| `fluid`    | string | `empty` (`CAULDRON`), `lava`, `powder_snow`, or `water` for anything else      |

**jukebox**

| Name      | Type    | Meaning                                                               |
|-----------|---------|-----------------------------------------------------------------------|
| `playing` | boolean | Has a record **and** is playing                                        |
| `record`  | string  | `readable()` of the record's material name, or `""` when none is loaded |

### 10.4 `surge.*`

Published by the `furnace` and `brewing` categories only. A `TimeFlowTracker` watches the category's
tick counter against the world clock and flags a surge when the counter advances faster than real time
— a hopper-fed boost, a plugin fast-forwarding a brew.

Exact rule, per sample (samples at the same `gameTime` are ignored, so it measures progress per game
tick, not per read):

```
gained  = countsDown ? lastValue - value : value - lastValue     // brewing counts down
elapsed = gameTime - lastGameTime
if (value > 0 && elapsed > 0 && elapsed <= 100 && gained > elapsed + 1) {
    surgeSeconds = (gained - elapsed) / 20.0
    surgeUntil   = gameTime + 60
}
```

- `surge.active` is `lastGameTime <= surgeUntil` — true for a **60-tick hold** after the last detected
  surge.
- `surge.gain` is the seconds of progress gained beyond real elapsed time in the window that triggered
  it. It is **not** reset when the hold expires; read it only while `surge.active`.
- The first sample of a preview can never trigger a surge (there is no previous reading).

### 10.5 Provider namespaces

Any variable of the form `<namespace>.<key>` that is not a built-in comes from a registered
`PreviewStateProvider` (see §15). Such names compile with a warning and resolve at render if the
provider is present.

### 10.6 Reserved names

A provider namespace, and a `repeat.var`, may not be any of: `vars`, every full catalog name above, or
the first segment of a dotted catalog name — i.e. `inventory` and `surge`. A provider claiming a
reserved namespace is dropped whole and warned about once.

---

## 11. Build-time versus live

### 11.1 The liveness table

**Exactly two fields are re-evaluated while a preview is on screen, every four ticks:**

| Field         | Live? | Notes                                                             |
|---------------|-------|-------------------------------------------------------------------|
| `cell.color`  | YES   | Unless it folds to a constant, in which case it is resolved once   |
| `label.text`  | YES   | Unless it folds to a constant, in which case it is parsed once     |
| *everything else* | NO | Evaluated once, when the preview is built                       |

Once-only, spelled out: `x`, `y`, `z`, `width`, `height`, `size`, `index`, `panel.color`,
`slot.wellColor`, `label.background`, `visible`, `repeat.count`, `card.framed`, `card.title`,
`card.accent`, and every chrome number the framer derives.

That is a hard constraint on how a document animates: **anything that must change on screen has to be
expressed as a cell colour or a label string.** An element hidden by `visible` at build time stays
hidden for the life of that preview, and a grid does not resize itself as items move.

Two things change on screen without being expressions: a `slot`'s item and its stack-count badge are
re-read from the inventory on the same four-tick beat.

### 11.2 What triggers a rebuild

A preview is built once when the session opens, and rebuilt from scratch only when the session is
recreated. That happens when:

- the document on disk is edited, created or deleted (every open preview is closed and the raycast
  loop rebuilds), or
- the viewer's access decision changes (re-checked every 10 ticks; a change closes the session).

Per-player scale changes (sneak + scroll) respawn the display entities from the **same** element list —
they do not re-run the build, so once-only expressions are not recomputed.

### 11.3 Build order

1. For each element template in list order:
   - if the budget (4096) is exhausted, report and stop;
   - if there is no `repeat`, take 1 from the budget and emit once against the document scope;
   - otherwise evaluate `count` once, truncate to the budget if needed, and emit `count` instances,
     each against a scope carrying its own index.
2. Emitting evaluates `visible` first and returns immediately if false (the budget was already taken).
3. Then `x`, `y`, `z`, then the type's own fields.
4. After all elements, if a `card` is declared and `framed` is true, the chrome is measured from the
   emitted content and **prepended**.

An exception anywhere inside one template's expansion aborts that whole template — including the
remaining instances of a repeat — reports `<type>: <message>`, and the build continues with the next
element.

---

## 12. Chrome: the card framer

`CardFramer.frame(content, title, accentColor, minHalfWidth)`. Every constant, every integer division
and the emitted order are frozen — golden snapshots pin all of it. A document with `"framed": false`
can rebuild identical chrome by hand from this section.

### 12.1 Constants

| Name               | Value        | Role                                        |
|--------------------|--------------|---------------------------------------------|
| `WELL`             | `18`         | Assumed height of any non-label element     |
| `LINE`             | `12`         | Assumed height of a label                   |
| `TRAY_PAD`         | `4`          | Tray padding around the grid bounds         |
| `PANEL_PAD`        | `7`          | Panel padding                               |
| `TITLE_BAR_HEIGHT` | `17`         |                                             |
| `FRAME_BORDER`     | `3`          | Frame extends this far past the panel       |
| `GAP`              | `6`          | Gap between content top and title bar       |
| `PANEL_COLOR`      | `0xF21B1B22` | Fixed; not derived from the accent          |
| `TRAY_COLOR`       | `0xFF33333E` | Fixed                                       |
| `FRAME_ALPHA`      | `0xCC`       | Alpha applied to the accent for the frame   |
| `TITLE_BAR_ALPHA`  | `0xE6`       | Alpha applied to the accent for the title bar |
| z: frame / panel / tray / title bar / title | `0` / `1` / `2` / `3` / `6` | |

### 12.2 Measuring

The framer measures the content it was handed — **not** each element's declared size. Every label
counts as `LINE/2 = 6` half-height; every other element counts as `WELL/2 = 9`, whatever its actual
`size`.

```
for each content element e:
    halfHeight   = (e is Label) ? 6 : 9
    contentTop    = max(contentTop,    e.y + halfHeight)
    contentBottom = min(contentBottom, e.y - halfHeight)
    if e is Slot or Cell:                       // the "grid"
        gridLeft   = min(gridLeft,   e.x - 9)
        gridRight  = max(gridRight,  e.x + 9)
        gridBottom = min(gridBottom, e.y - 9)
        gridTop    = max(gridTop,    e.y + 9)

if no content at all:
    contentTop = 9, contentBottom = -9

panelHalfWidth = max(minHalfWidth, (hasGrid ? (gridRight - gridLeft) / 2 : 9) + 7)   // integer /
titleBarBottom = contentTop + 6
panelTop       = titleBarBottom + 17
panelBottom    = contentBottom - 7
panelCenterY   = (panelTop + panelBottom) / 2        // Java integer division, truncates toward zero
panelWidth     = panelHalfWidth * 2
panelHeight    = panelTop - panelBottom
titleBarCenterY = (panelTop + titleBarBottom) / 2    // same truncation

frameColor    = (0xCC << 24) | (accent & 0xFFFFFF)
titleBarColor = (0xE6 << 24) | (accent & 0xFFFFFF)
```

`hasGrid` is true when the content contains at least one `slot` or `cell`.

### 12.3 Emitted elements, in order

| # | Element | x | y | z | width | height | color |
|---|---------|---|---|---|-------|--------|-------|
| 1 | panel (frame)     | `0` | `panelCenterY` | `0` | `panelWidth + 6` | `panelHeight + 6` | `frameColor` |
| 2 | panel (backdrop)  | `0` | `panelCenterY` | `1` | `panelWidth` | `panelHeight` | `0xF21B1B22` |
| 3 | panel (tray) — only when `hasGrid` | `(gridRight + gridLeft) / 2` | `(gridTop + gridBottom) / 2` | `2` | `(gridRight - gridLeft) + 8` | `(gridTop - gridBottom) + 8` | `0xFF33333E` |
| 4 | panel (title bar) | `0` | `titleBarCenterY` | `3` | `panelWidth` | `17` | `titleBarColor` |
| 5 | label (title)     | `0` | `titleBarCenterY` | `6` | — | — | background `0` |
| 6+ | your content, unchanged, in build order | | | | | | |

The default accent when `card.accent` is omitted is `0xFFCBD0D9`, so the frame is `0xCCCBD0D9` and the
title bar `0xE6CBD0D9`.

---

## 13. Layout and rendering model

Positions are in **layout pixels**. At scale `1.0`, `160` layout pixels span one block
(`LAYOUT_PIXELS_PER_BLOCK = 160`). The card is billboarded flat in front of the target, `x` right,
`y` up, origin at the card centre.

`z` is not a paint index — it is a pull toward the viewer's eye:

```
shrink = max(0.5, 1.0 - z * 0.04)
```

Each element is placed on the card plane, then moved along the eye-to-element vector by `shrink`, and
its rendered scale is multiplied by `shrink` too. So a higher `z` draws in front but slightly smaller,
and everything at `z >= 12.5` is clamped to the same half-depth. Keep `z` small — the shipped documents
use `0`–`8`.

A `slot` spawns up to three things: the well background at `z`, the item display at `z + 1.5`, and the
stack-count text at `z + 3.0` (only when the amount is `> 1`), offset to the well's bottom-right
corner.

Label text is rendered as a text display; `background` is its background colour, `0` meaning fully
transparent.

Per-player preview scale (sneak + scroll, `previewScale` in settings) multiplies everything uniformly
and never changes the layout pixel numbers a document writes. `previewEnabled` and
`previewLookDistance` gate previews globally.

---

## 14. Matching, resolution and lifecycle

### 14.1 Files

- Folder: `plugins/holoui/previews/`, created at startup if absent.
- One document per file. The document name is the file's base name; log lines and errors use
  `<name>.json`.
- Only non-directory files whose name ends in `.json` (case-insensitive) are loaded.
- On startup, every shipped document **missing** from the folder is extracted from the jar. Existing
  files are never overwritten at startup.
- The thirteen shipped documents: `beehive`, `brewing_stand`, `cauldron`, `chest`,
  `chiseled_bookshelf`, `dispenser`, `ender_chest`, `furnace`, `hopper`, `jukebox`, `locked`,
  `minecart`, `shelf`.
- `resetToDefault` (`/holoui previews reset`) rewrites shipped documents over whatever is on disk,
  discarding local edits, then reloads and closes open previews. It does **not** delete extra user
  documents that may shadow them.

### 14.2 Hot reload

A folder watcher runs every **5 ticks** (250 ms) and handles modifications, creations and deletions in
one pass. Any change republishes the resolution snapshot and closes every open preview, because a
preview holds the element list it was built from and a priority change can move a target between
documents.

Detection compares only a file's **last-modified time and its length** — no hash, no content compare.
An edit that changes neither (a same-length replacement landing inside the filesystem's mtime
resolution) is not seen, and stays unseen until some later edit moves one of the two. Touch the file
or change its length if a preview does not pick up an edit.

A file that fails to compile logs `previews/<name>.json: <message>` and is skipped. On reload the
previously compiled version **stays live**, so a half-saved edit never blanks a preview. A deleted
file drops its document.

### 14.3 Resolution

For a block, the registry grades every loaded document (iterated in document-name order) and keeps the
best:

| Grade      | Value | Meaning                                        |
|------------|-------|------------------------------------------------|
| `none`     | 0     | Does not match; skipped                        |
| `fallback` | 1     | `anyInventoryHolder`, entities only            |
| `glob`     | 2     | Matched by a `*` pattern                       |
| `exact`    | 3     | Named exactly                                  |

Comparison is **priority first, then grade**. A document only replaces the incumbent when it is
strictly better; an exact draw on both keys keeps the incumbent — which, because the iteration order is
by name, is the lexicographically smaller document name. That tie is stable across restarts and warned
about once per pair per reload:

```
previews: a.json and b.json match the same targets at priority 10, using a.json.
```

A document's grade is the maximum over its own match and every variant (short-circuiting on `exact`).
Entities additionally fall back to `anyInventoryHolder` at grade `fallback` when nothing named them.

To override a shipped preview, drop a file next to it with a higher `priority`. To preview a block
nothing claims, name its material in a document — in `match.blocks` or in any variant's `blocks`, which
count the same for resolution.

### 14.4 Raycast eligibility

A block is only raycast-eligible when some loaded document names its material, exactly or through a
glob, in the base match or in a variant. The eligibility set is an `EnumSet<Material>` rebuilt on every
publish by walking every material once (`AIR` excluded), so the hot path is one volatile read and an
ordinal test.

`anyInventoryHolder` contributes **no** materials — it exists for inventory-holding carts and boats,
and as the document users copy to extend the set.

An entity is eligible when it is an `InventoryHolder`, is a `Minecart` or a `ChestBoat`, **and** some
document resolves for it. Deleting `minecart.json` stops carts being previewed.

---

## 15. Worked examples

### 15.1 A complete document, annotated

`previews/jukebox.json`, in full:

```json
{
  "match": {
    "blocks": ["JUKEBOX"],
    "priority": 10,
    "vars": {
      "titleKey": "holoui.preview.theme.title.jukebox",
      "accent": "#EC88EC"
    }
  },
  "card": {
    "title": "'&f&l' + plain(lang(vars.titleKey))",
    "accent": "vars.accent"
  },
  "elements": [
    { "type": "slot", "x": 0, "y": 0, "size": 18, "index": 0 },
    {
      "type": "label",
      "x": 0,
      "y": -21,
      "text": "record != '' ? (playing ? '&a' + lang('holoui.preview.state.disc_playing', record) : '&7' + lang('holoui.preview.state.disc_loaded', record)) : '&8' + lang('holoui.preview.state.no_disc')"
    }
  ]
}
```

Line by line:

- `match.blocks` — one exact material. That single entry is also what makes `JUKEBOX` raycast-eligible.
- `priority: 10` — the shipped baseline. A user document at `20` overrides this one wholesale.
- `vars.titleKey` — a plain string. Never parsed as an expression; `lang(vars.titleKey)` resolves it at
  render.
- `vars.accent` — leads with `#`, so it arrives as the number `0xFFEC88EC`, alpha intact.
- `card.title` — `lang` fetches `&d&lJukebox`, `plain` strips its `&d&l`, and the document's own
  `&f&l` (white + bold) is prepended. Evaluated **once** at build.
- `card.accent` — the chrome takes the low 24 bits, `EC88EC`, and applies its own alphas.
- The `slot` — `JUKEBOX` selects the `jukebox` category, whose `Jukebox` state is an `InventoryHolder`,
  so `inventory` is published too and slot `0` is the disc. `z` defaults to `4`, `wellColor` to
  `#FF15151B`.
- The `label` — `y: -21` puts it 21 px below centre. `z` defaults to `6`. This is a **live** field: it
  is re-evaluated every four ticks, so the line flips between "Playing" and "Loaded" as the disc
  starts and stops. `record != ''` is the shipped idiom for "no disc", because `record` is published as
  an empty string rather than being absent.
- No `variants`, so `varsForBlock` always returns the document's own vars.
- No `visible` and no `repeat`, so both take their defaults.

Built output, in order: frame panel, backdrop panel, tray panel (there is a slot, so `hasGrid`), title
bar panel, title label, the slot, the state label. Seven elements.

### 15.2 The repeat progress bar (`furnace.json`)

Eight cells, filled left to right by cook progress, with a pulsing leading cell, an alternating
surge shimmer on the filled section, and an idle chase when the furnace is burning but not cooking:

```json
{
  "type": "cell",
  "repeat": { "count": "vars.segments", "var": "i" },
  "x": "-24 + i * 7",
  "y": 10,
  "size": 5,
  "color": "cookTime > 0 && cookTimeTotal > 0 ? (i < floor(cookTime / cookTimeTotal * vars.segments) ? (surge.active ? (mod(floor(cookTime / 2) + i, 2) == 0 ? vars.pulseBright : vars.fill) : vars.fill) : (i == floor(cookTime / cookTimeTotal * vars.segments) ? (mod(floor(cookTime / 4), 2) == 0 ? vars.pulseBright : vars.pulseDim) : vars.wellColor)) : (burnTime > 0 && i == mod(floor(burnTime / 4), vars.segments) ? vars.chase : vars.wellColor)"
}
```

Points worth copying:

- `count` and `x` are evaluated once; only `color` is live. The bar animates entirely through colour.
- The `cookTimeTotal > 0` guard is load-bearing: `&&` short-circuits, so the division on the right is
  never reached with a zero divisor.
- `mod(floor(burnTime / 4), vars.segments)` wraps a monotonically falling counter into `0..segments-1`
  without ever going negative — that is exactly what `mod` is for, and `%` would be wrong here.
- Every colour comes from a var, which is what lets the variants restyle identical geometry.

### 15.3 Per-variant palettes

`furnace.json` matches three materials in its base match, then repaints two of them:

```json
"variants": [
  { "blocks": ["BLAST_FURNACE"], "vars": { "style": "blast", "fill": "#FF6FB8E8", "accent": "#6FEAEA", "activeKey": "holoui.preview.state.blasting", "…": "…" } },
  { "blocks": ["SMOKER"],        "vars": { "style": "smoker", "fill": "#FFC8893A", "accent": "#F2D451", "activeKey": "holoui.preview.state.smoking",  "…": "…" } }
]
```

Variant vars are merged over the base, so a variant only lists what it changes. A `style` string var
then drives geometry through `visible`, which is evaluated once per build:

```json
{ "type": "cell", "visible": "vars.style != 'blast'", "x": -20, "y": -10, "size": 12, "color": "…" },
{ "type": "cell", "visible": "vars.style == 'blast'", "repeat": { "count": 3, "var": "vent" }, "x": "-20 + vent * 8", "…": "…" },
{ "type": "cell", "visible": "vars.style == 'smoker'", "repeat": { "count": 2, "var": "wisp" }, "x": "wisp == 0 ? -8 : 2", "size": "wisp == 0 ? 8 : 6", "…": "…" }
```

Three mutually exclusive flame treatments in one document. `chest.json` uses the same mechanism at
scale: twenty variants, one per shulker colour, differing only in `titleKey`, `titleArg` and `accent`.

### 15.4 The multi-styled label idiom

A label is one expression producing one string, and that string is then parsed for legacy `&` codes and
MiniMessage tags. So a label with several differently coloured runs is **one concatenation**, not
several elements:

```
(burnTime > 0 ? '&e' + lang('holoui.preview.stat.fuel_seconds', fuelSeconds)
              : (occupied(1) ? '&7' + lang('holoui.preview.stat.fuel_ready')
                             : '&8' + lang('holoui.preview.stat.no_fuel')))
+ (bankedXp >= 0
     ? '<dark_gray>  •  </dark_gray>'
       + (bankedXp > 0 ? '<green>' + lang('holoui.preview.stat.xp_gain', str(bankedXp)) + '</green>'
                       : '<dark_gray>' + lang('holoui.preview.stat.xp_zero') + '</dark_gray>')
     : '')
```

**Why the shapes differ.** `TextUtils.parse` translates `&x` to `§x`, rewrites each `§x` into the
equivalent MiniMessage tag (`&e` → `<yellow>`, `&l` → `<bold>`, …), and then deserializes the whole
string with MiniMessage. A rewritten legacy code is an **unclosed** tag, so it styles everything that
follows it until something overrides it. An explicitly closed tag styles only what it wraps and then
reverts.

That gives the idiom its rule: **open the outer style with a legacy code, close every inner run with a
MiniMessage tag pair.** The leading `&e` colours the whole line; `<dark_gray>…</dark_gray>` and
`<green>…</green>` colour their fragments and hand the line back to yellow. Leaving an inner tag open
would leak its colour over everything after it.

The same trick styles a title: `'&f&l' + plain(lang(vars.titleKey))` strips the catalog entry's own
codes and applies the document's white-bold to the whole title.

Because the final string is deserialized by MiniMessage, a literal `<` in it can start a tag — and
nothing in the pipeline escapes one, not even a value `lang` bound as untrusted (§9.6). Wrap anything a
player can set in `plain()` and keep it out of tag position.

---

## 16. Recipes

### 16.1 Add a preview for a new block type

Create `plugins/holoui/previews/lectern.json`:

```json
{
  "match": {
    "blocks": ["LECTERN"],
    "priority": 10,
    "vars": { "accent": "#C8A165" }
  },
  "card": { "title": "'&f&lLectern'", "accent": "vars.accent" },
  "elements": [
    { "type": "slot", "x": 0, "y": 0, "size": 18, "index": 0 },
    { "type": "label", "x": 0, "y": -21, "text": "occupied(0) ? '&f' + readable(item(0)) : '&8Empty'" }
  ]
}
```

Naming the material is what makes the block raycast-eligible; nothing else is needed. Save the file and
it compiles on the next watcher pass, within a few ticks. Check with `/holoui previews list` and
`/holoui previews dump lectern`.

A lectern's block state is an `InventoryHolder` but not a `Container`, so it lands in the `inventory`
category by dispatch branch 6. Either way `inventory.size`, `inventory.occupied`, `count`, `occupied`
and `item` all work, and furnace/brewing variables do not. When adding a block whose category you are unsure of, dump
it while looking at one: an expression referencing the wrong group fails with
`unknown variable: <name>`.

### 16.2 Restyle one shulker colour

`chest.json` already has a variant per colour. Edit the one you want:

```json
{ "blocks": ["LIME_SHULKER_BOX"],
  "vars": { "titleKey": "holoui.preview.theme.title.shulker", "titleArg": "Lime", "accent": "#00FF66" } }
```

Nothing else changes — the element templates and the title expression are shared. If you would rather
not edit a shipped file (`/holoui previews reset` will overwrite it), put a whole new document at a
higher priority instead:

```json
{
  "match": { "blocks": ["LIME_SHULKER_BOX"], "priority": 20, "vars": { "accent": "#00FF66", "cols": 9, "maxRows": 6 } },
  "card": { "title": "'&a&lLime Shulker'", "accent": "vars.accent" },
  "elements": [
    {
      "type": "slot",
      "repeat": {
        "count": "min(vars.cols * clamp(ceil(inventory.size / vars.cols), 1, vars.maxRows), inventory.size)",
        "var": "i"
      },
      "x": "round((mod(i, vars.cols) - (vars.cols - 1) / 2) * 20)",
      "y": "round(((clamp(ceil(inventory.size / vars.cols), 1, vars.maxRows) - 1) / 2 - floor(i / vars.cols)) * 20)",
      "size": 18,
      "index": "i"
    }
  ]
}
```

That element block is `chest.json`'s slot grid verbatim — the canonical nine-wide, up-to-six-row
inventory layout, safe to copy into any `inventory`-category document. JSON has no comments; there is
no `//` or `/* */` form.

### 16.3 Add a live stat line

Append a `label` to `elements`. Only `label.text` is live, so a stat line is the correct tool for
anything that must update on screen:

```json
{ "type": "label", "x": 0, "y": -46,
  "text": "'&7' + str(inventory.occupied) + '&8/&7' + str(inventory.size) + ' &8slots'" }
```

`str` is a call, so the expression is non-constant and is re-evaluated every four ticks. Note that
`inventory.occupied` is re-sampled per tick, but the *grid* built from `inventory.size` is not — the
count moves, the wells do not.

Give the new label a `y` below your lowest existing element; the chrome measures from what you emit and
will grow the panel to fit.

### 16.4 Disable chrome

Remove the `card` object entirely for bare content:

```json
{ "match": { "blocks": ["DIRT"] }, "elements": [ … ] }
```

Or keep the object and turn the chrome off, which is what `locked.json` does so the padlock floats
alone:

```json
"card": { "framed": false }
```

With `framed: false`, `title`, `accent` and `minHalfWidth` are ignored and only your elements are
emitted. To reproduce the chrome by hand, emit the panels from §12.3 as ordinary `panel` elements.

---

## 17. Contributing variables from another plugin

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

- Every entry is published as `namespace() + "." + key`.
- `snapshot` is called on the region thread owning the target, at most once per game tick per preview.
  Bukkit access is safe; expensive work is not.
- Values are coerced to the runtime's types — any `Number` becomes a double, `Boolean` and `String`
  pass through, anything else is dropped. A null key or a null coerced value is dropped.
- Returning `null` or an empty map contributes nothing.
- A namespace that is null, blank, or would shadow a built-in variable is rejected whole, and warned
  about once.
- A provider that throws is skipped for that sample and warned about once. It cannot take a preview
  down.
- A document referencing `myplugin.charge` compiles with a warning even when the provider is absent,
  and fails at render (`unknown variable: myplugin.charge`) with the element skipped.

---

## 18. Commands

| Command                              | Permission                      | Does                                                                      |
|--------------------------------------|---------------------------------|---------------------------------------------------------------------------|
| `/holoui previews list`              | `holoui.command.previews`       | Every loaded document with its block/entity counts, `special` and priority |
| `/holoui previews reset [name=<n>]`  | `holoui.command.previews.reset` | Rewrites shipped defaults over the files on disk. `name` defaults to `*`   |
| `/holoui previews dump <name>`       | `holoui.command.previews.dump`  | Builds the document once and prints element counts plus any build errors   |

All three default to operator-only. The root command aliases are `holo`, `hui`, `holou`, `hu`.

`list` counts every matcher — exact names and globs, from the base match **and** every variant — so
`chest.json` reports a large block count. Output line:
`blocks={blocks} entities={entities} special={special} priority={priority}`, with `-` for no `special`.

`reset` runs asynchronously (up to thirteen file writes plus a full reparse). It restores the shipped
files and does **not** delete extra user documents that may shadow them. A name that is not a shipped
document writes nothing and reports so. A trailing `.json` on the name is tolerated.

`dump` builds the named document once against a real context and reports
`{total} elements (panels=…, cells=…, slots=…, labels=…)` followed by up to **three** build-error lines
and a `+N more (see console log)` tail. The error sink is per invocation and unthrottled, so repeated
dumps of the same broken document always show its errors even though the shared log is rate-limited.

Dump context differs by sender:

- **Player** — runs on the region thread owning the player. If the player is looking at a block the
  document matches, the dump builds against that live block with that block's merged variant vars.
  Otherwise it falls back to statics.
- **Console / RCON** — never touches world state; always a statics-only scope, and dispatched inline so
  RCON reads a populated response buffer.

A statics dump has no inventory, so every `slot` element reports `slot: target has no inventory`. That
is expected, not a document bug.

---

## 19. Access

Reading container contents requires `holoui.preview` (operator-only by default; previews can also be
turned off globally with `previewEnabled`). A viewer who lacks the permission, cannot physically open
the container, cannot satisfy a held-item lock, or is denied by an access provider gets the `locked`
document — one padlock marker, nothing from the inventory. Access is decided before any document is
built and before any inventory slot is read, and re-checked every ten ticks; a change closes the
session so it rebuilds down the other path.

See [menus.md](menus.md#configuration) for `HoloUiContainerPreviewAccessEvent` and the WorldGuard
integration.

---

## 20. Schema

`schema/holoui-preview.schema.json` is a JSON Schema 2020-12 description of this format, suitable for
editor completion and validation. It is **documentation grade**: it cannot express the parser's
cross-field rules (required-per-type fields, variable-name resolution, repeat caps, the `#` var rule,
constant folding errors), and where it disagrees with `PreviewDocumentParser`, the parser is right.

Point an editor at it with:

```json
{ "$schema": "../../schema/holoui-preview.schema.json", "match": { … } }
```

An unknown `$schema` key is ignored by the parser.
