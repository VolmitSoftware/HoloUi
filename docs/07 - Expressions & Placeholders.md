# Expressions & Placeholders

HoloUi contains two independent substitution systems. The expression language is used only by container preview documents in `plugins/holoui/previews/*.json`; PlaceholderAPI substitution happens in menu JSON in exactly two fields. They never meet: an expression cannot read a PlaceholderAPI placeholder, and no menu JSON field is parsed as an expression.

## Scope of each system

### Menu JSON: PlaceholderAPI, two fields

Menu definition files (`plugins/holoui/menus/*.json`) contain no expression language. Two fields receive PlaceholderAPI substitution:

| Menu JSON field | Component / icon | Substitution | When |
|---|---|---|---|
| text icon `text` | text icon (`TextIconData`) | `setPlaceholders(player, line)` on each `\n`-separated line | Icon construction, then every `refreshTicks` while visible; omission is `10`, `0` disables refresh |
| toggle `condition` | toggle component | `setPlaceholders(player, condition)`, then compared with `equalsIgnoreCase` against `expectedValue` | Toggle component construction only |

Nothing else is expanded. Actions, commands, item icons, image icons, and component ids are passed through verbatim. See [03 - Menu File Format.md](03%20-%20Menu%20File%20Format.md), [05 - Icons.md](05%20-%20Icons.md), and [06 - Actions.md](06%20-%20Actions.md).

Substitution returns the input unchanged when the player is null, when the text contains no `%`, or when PlaceholderAPI is absent.

### Preview documents: expressions, no PlaceholderAPI

Preview documents (`plugins/holoui/previews/*.json`, defaults extracted from the jar's `/previews/`) are the only consumer of the expression language. Preview documents never call PlaceholderAPI. The lexer has no `%…%` form — a bare `%` is the modulo operator, and a `%` inside a string literal is an ordinary character that reaches the text parser unexpanded. To surface external data in a preview, register a `PreviewStateProvider` and read `<namespace>.<key>`; see [16 - API - Previews.md](16%20-%20API%20-%20Previews.md).

## Expression-capable fields

Every preview document field falls into one of three forms.

| Form | Accepts | Fields |
|---|---|---|
| number-or-expression | JSON number, or a JSON string parsed as an expression | `elements[].x`, `.y`, `.z`, `.width`, `.height`, `.size`, `.index`, `.color`, `.wellColor`, `.background`, `elements[].repeat.count` |
| bool-or-expression | JSON boolean, or a JSON string parsed as an expression | `elements[].visible`, `card.framed` |
| expression-only string | always a string, always parsed as an expression | `elements[].text` (label), `card.title`, `card.accent` |

Fields that are never expressions: `match.*` and `variants[].*` (block and entity names, `special`, `priority`), `elements[].type`, `elements[].repeat.var`, and `card.minHalfWidth`.

`match.vars` and `variants[].vars` values are JSON primitives converted to `Double`, `Boolean`, or `String`, and are not expressions. The single exception is a string whose first character is `#`: it is parsed with the expression colour-literal grammar and stored as the resulting unsigned ARGB `Double`. A leading `#` that is not a valid colour literal is a compile error, not a string.

### No delimiter

An expression-capable field's JSON string value is the expression source in its entirety. No `${…}`, `%…%`, or other wrapper triggers evaluation, and no wrapper suppresses it.

- `"width": 64` is the literal number 64; `"width": "64"` is an expression that happens to be a literal.
- In the three expression-only fields, literal text must be quoted inside the JSON string: `"text": "'Idle'"`. `"text": "Idle"` parses as a reference to the variable `Idle` and fails to compile.
- A JSON number in an expression-only field binds to its text form, so `"text": 5` compiles as the number 5.

### Evaluation timing

Constant expressions — those containing no variable and no function call anywhere in the tree — are folded at document load. Everything else is evaluated once per preview build, except two fields wrapped in suppliers the renderer polls on every content refresh (every 4 ticks).

| Field | Evaluated |
|---|---|
| `elements[].color` on a `cell` | Live, per refresh |
| `elements[].text` on a `label` | Live, per refresh (unless folded constant, then parsed once) |
| Everything else, including `card.title`, `card.accent`, `card.framed`, `visible`, `repeat.count` | Once, per build |

## Grammar

### Runtime value set

`Double`, `String`, `Boolean`, and `List<Object>`. There is no null, no integer type, and no truthiness coercion: every operator that needs a number, boolean, or string rejects the other types with an error.

### Literals

| Literal | Syntax | Value |
|---|---|---|
| Number | `123`, `3.5` | `Double`. No exponent notation, no leading `.`, no sign (a leading `-` is the unary operator) |
| Colour | `#RGB`, `#RRGGBB`, `#AARRGGBB` | `Double` holding the unsigned 32-bit ARGB. `#RGB` doubles each nibble and prepends alpha `FF`; `#RRGGBB` prepends alpha `FF`; `#AARRGGBB` is taken as written. Any other hex-digit count is `bad hex length` |
| String | `'text'` or `"text"` | `String`. Escapes: `\\`, `\'`, `\"`, `\n`. Any other escape is `unrecognized escape sequence` at the backslash position; a missing closing quote is `unterminated string` |
| Boolean | `true`, `false` | `Boolean` |
| List | `[a, b, c]`, `[]` | `List<Object>`. Useful only as a function argument (`palette`); there is no indexing syntax and no list operators |

Identifiers match `[A-Za-z_][A-Za-z0-9_]*` and may be dotted (`inventory.size`); a `.` continues the identifier only when followed by a letter or `_`. Call names may not be dotted (`call names cannot be dotted`).

### Precedence

Lowest to highest. All binary levels are left-associative; the ternary is right-associative.

| # | Level | Operators | Operand types | Result |
|---|---|---|---|---|
| 1 | Ternary | `c ? a : b` | condition boolean; branches any | branch type |
| 2 | Or | `\|\|` | boolean | boolean |
| 3 | And | `&&` | boolean | boolean |
| 4 | Equality | `==` `!=` | number/number, string/string, boolean/boolean | boolean |
| 5 | Relational | `<` `<=` `>` `>=` | number | boolean |
| 6 | Additive | `+` `-` | number, or string for `+` | number or string |
| 7 | Multiplicative | `*` `/` `%` | number | number |
| 8 | Unary prefix | `-` `!` | number / boolean | number / boolean |
| 9 | Primary | literal, `( )`, `[ ]`, variable, call | — | — |

`&&` and `||` short-circuit: the right operand is not evaluated, and its variables and calls are not resolved, when the left operand decides the result. The ternary evaluates only the taken branch.

Recursion through the ternary and unary productions is capped at 256 (`expression too deeply nested`). Parenthesised, bracketed, and call-argument sub-expressions re-enter the ternary production and therefore count against the cap.

### Strings and `+`

`+` is string concatenation when either operand is a `String`, and numeric addition otherwise. Both sides are converted with the shared stringify rule:

| Value | Text |
|---|---|
| `String` | unchanged |
| `Double` that is finite and equal to `Math.rint(x)` | integer form — `54.0` renders `54`, `1e9` renders `1000000000` |
| Any other `Double` | `Double.toString` — `3.5` renders `3.5` |
| `Boolean` | `true` / `false` |
| `List` | error: `cannot convert list to string` |

`str(x)` applies the same rule explicitly. Equality on strings is exact and case-sensitive; `<`, `<=`, `>`, `>=` reject strings.

### `%` versus `mod`

`%` is the Java remainder operator, whose sign follows the left operand. `mod(a, b)` is floor-mod, `a - floor(a / b) * b`. `7 % 3` and `mod(7, 3)` both give `2`; `-1 % 3` gives `-1` while `mod(-1, 3)` gives `2`. A zero right operand throws `division by zero` in `%`, `mod`, and `/`.

Colour expressions are read back as `(int) (long) value`, which reinterprets the unsigned ARGB double as a signed int, so `#FFFFFFFF` becomes `-1` rather than saturating at `0x7FFFFFFF`.

## Functions

### Standard library

Every name the library dispatches. Arity is exact; a mismatch throws `<name> expects <n> argument(s), got <m>`. A wrong argument type throws `<name> argument <1-based index> must be a number|a string|a list`. Colours are the unsigned 32-bit ARGB value carried as a `Double`, channel order alpha, red, green, blue from most to least significant byte.

| Function | Arity | Argument types | Returns | Description |
|---|---|---|---|---|
| `clamp(x, lo, hi)` | 3 | number, number, number | number | `min(max(x, lo), hi)` |
| `lerp(a, b, t)` | 3 | number, number, number | number | `a + (b - a) * t`; `t` is not clamped |
| `min(a, b)` | 2 | number, number | number | Smaller of the two |
| `max(a, b)` | 2 | number, number | number | Larger of the two |
| `floor(x)` | 1 | number | number | Largest integer not greater than `x` |
| `ceil(x)` | 1 | number | number | Smallest integer not less than `x` |
| `round(x)` | 1 | number | number | `floor(x + 0.5)` as a double, so `round(-2.5)` is `-2` |
| `abs(x)` | 1 | number | number | Absolute value |
| `mod(a, b)` | 2 | number, number | number | Floor-mod `a - floor(a / b) * b`; `b == 0` throws `division by zero` |
| `sin(x)` | 1 | number (radians) | number | Sine |
| `cos(x)` | 1 | number (radians) | number | Cosine |
| `rgb(r, g, b)` | 3 | number, number, number | number (ARGB) | Opaque colour; each channel rounded and clamped to `[0, 255]`, alpha forced to `0xFF` |
| `argb(a, r, g, b)` | 4 | number x4 | number (ARGB) | As `rgb`, with an explicit alpha channel |
| `alpha(color, a)` | 2 | number (ARGB), number | number (ARGB) | Replaces only the alpha byte; `a` rounded and clamped to `[0, 255]` |
| `mix(c1, c2, t)` | 3 | number (ARGB), number (ARGB), number | number (ARGB) | Per-channel linear blend including alpha, `channel = round(a + (b - a) * t)`, `t` clamped to `[0, 1]` |
| `palette(list, index)` | 2 | list of numbers, number | number | `list[floorMod(floor(index), size)]`. An empty list throws `palette list must not be empty`; a non-number entry throws `palette list entries must be numbers` |
| `str(x)` | 1 | number, string, or boolean | string | Stringify rule above |
| `fixed(x, digits)` | 2 | number, whole number in `[0, 20]` | string | Root-locale `%.<digits>f`. Fractional, negative, or `> 20` digits throw |
| `plain(s)` | 1 | string | string | Removes legacy codes matching `&[0-9A-Fa-fK-Ok-oRr]`; every other `&` survives |
| `readable(s)` | 1 | string | string | Lowercases, splits on `_`, upper-cases the first character of each segment, joins with a space (`IRON_ORE` becomes `Iron Ore`). Trailing empty segments are dropped; leading and doubled separators produce spaces |

### Preview context functions

These four are resolved before the standard library, so a same-named library function would be shadowed. None collide today. All are available in every preview document.

| Function | Arity | Argument types | Returns | Description |
|---|---|---|---|---|
| `lang(key, ...)` | 1 or more | string key, then any values | string | Resolves `key` through the global localization table. Positional arguments bind onto the resolved English template's `{placeholder}` names in first-appearance order; extras are named `arg<n>` and go unused. Values are stringified with `str` and inserted as untrusted text. See [10 - Localization.md](10%20-%20Localization.md) |
| `count(slot)` | 1 | number | number | Stack size in the previewed inventory slot; `0` for an empty slot, an out-of-range index, or no inventory |
| `occupied(slot)` | 1 | number | boolean | Whether that slot holds a non-empty stack |
| `item(slot)` | 1 | number | string | Material id in that slot (`IRON_ORE`), or `""` when the slot is empty, out of range, or there is no inventory. Pair with `readable(item(0))` for display text |

Slot indexes are floored. Calling `lang` with no argument, or with a non-string first argument, throws.

## Variables

### Built-in catalog

The `universal` group is published in every context. The `inventory` group is published whenever the target has an inventory, including furnaces, brewing stands, and jukeboxes. Exactly one category group is published, chosen at context construction from the block state, entity, or inventory type.

The canonical built-in variable table, including types, fallback values, and category-selection rules, is in [09 - Container Previews.md](09%20-%20Container%20Previews.md#43-variable-catalog). There is no player name, player world, server, or time-of-day built-in. The viewing player is passed to `PreviewStateProvider#snapshot` but is not published as a variable.

### Resolution order

1. A repeat element wraps the context in a repeat scope, which answers the loop variable name exactly and delegates everything else to the parent.
2. Names starting with `vars.` read the document's injected variable map: document `match.vars` with the first matching variant's `vars` merged over them.
3. Everything else reads the sampled snapshot — the built-in variables above plus every registered provider namespace, merged flat as `<namespace>.<key>`.

The snapshot is sampled lazily and re-sampled whenever the world game time changes, so one refresh reads each server getter once. Contexts with no world, such as a bare ender-chest inventory or a static preview, use a wall-clock tick counter.

### Document variables and repeat variables

`vars.<name>` reads a constant declared in `match.vars` or in a variant's `vars`. Document variables are reachable only under the `vars.` prefix, so they can never shadow or be shadowed by a built-in name. Repeat variables are bare identifiers (default `i`, values `0 .. count-1` as numbers); `repeat.var` may not be `vars` or any cataloged name, which would make the loop variable unreachable.

### Provider namespaces

Provider values are merged as `<namespace>.<key>` and narrowed to number, string, or boolean; any other numeric type is widened to a number, and anything else is dropped. A provider whose namespace is reserved by a built-in group is dropped whole and warned about once. A provider that throws is skipped and warned about once per namespace.

### Compile-time variable checking

| Reference | Result |
|---|---|
| A cataloged name | Accepted |
| `vars.<declared>` | Accepted |
| `vars.<undeclared>` | Compile error `unknown variable: vars.<name>` |
| Bare name in the enclosing repeat scope | Accepted |
| Any other bare name | Compile error |
| Dotted name whose prefix is a reserved namespace | Compile error |
| Dotted name whose prefix is not reserved | Warning `references provider namespace '<name>', not verifiable at parse time`; resolved, or failed, at runtime |

## Errors

### Load time

Parse errors carry the 0-based source character index and are reported with the field path: `elements[3].color: unexpected token at 7`. Evaluation errors carry no position and are reported without the suffix.

Constant expressions are evaluated at load, so a constant that throws is a load error: `"x": "1 / 0"` rejects the document. Failures are per-document — the loader logs `previews/<name>.json: <detail>` at `WARNING` and does not register the document. Nothing renders for it, and the next matching document, or none, is used.

Other load-time rejections: unknown variable references, a required field absent or explicitly `null`, a wrong JSON type for the field form, a constant `repeat.count` above `1024`, and a total compiled template count above `4096`.

### Render time

Failures never take a frame down.

| Failure | Result |
|---|---|
| An element's build-time expressions throw | That element is skipped, message `<type>: <exception message>`; the rest of the document still renders |
| A `slot` element on a target with no inventory | Element skipped, `slot: target has no inventory` |
| `card.framed` throws | Treated as `false` (no chrome), `card framed: <message>` |
| `card.title` throws | Empty title, `card title: <message>` |
| `card.accent` throws | Default accent `0xFFCBD0D9`, `card accent: <message>` |
| Live `cell` colour throws | Cell renders `0x00000000` (transparent), `cell color: <message>` |
| Live `label` text throws | Renders an empty component, `label text: <message>` |
| Anything else during build throws | Empty element list, `build failed: <message>` |
| Live `repeat.count` above `1024` | Truncated to `1024`, `repeat count <n> exceeds 1024, truncated` |
| Expansion crosses the 4096 element budget | Repeat truncated and later elements skipped, with a message naming the cap |

Every render-time failure logs one `WARNING` line, throttled to one line per document name per 60 seconds — the alternative is a line every 4 ticks for as long as a player looks at the block. `/holoui previews dump` receives every message of that build unthrottled; see [09 - Container Previews.md](09%20-%20Container%20Previews.md) and [02 - Commands & Permissions.md](02%20-%20Commands%20&%20Permissions.md).

## Toggle conditions

A toggle component's state is `setPlaceholders(player, condition).equalsIgnoreCase(expectedValue)`.

- The comparison is case-insensitive, unlike expression string equality.
- A `condition` containing no `%` is never sent to PlaceholderAPI at all; it is compared as a literal. `"condition": "yes"`, `"expectedValue": "YES"` is a toggle that is always on.
- The condition is evaluated once, in the toggle component's constructor. Clicking the toggle flips the stored state and runs `trueActions` or `falseActions`; it does not re-evaluate the condition.
- The constructor runs inside the menu session constructor, after the session id is published. A `%holoui_menu.id%` in a toggle condition, `trueIcon`, or `falseIcon` expands to the menu id, and `%holoui_menu.open%` reads `true`, the same as for any other component.

See [04 - Components & Hitboxes.md](04%20-%20Components%20&%20Hitboxes.md) for the rest of the toggle component.

## Text icon expansion

A text icon splits its `text` on `\n` and expands each line separately during construction. Text containing a paired `%name%` token then expands again every `refreshTicks` while visible: omission is `10` ticks, the accepted range is `0` through `1200`, and `0` keeps the initial value. Static text is detected and incurs no periodic expansion. Changed text metadata is updated in place; a changed line count respawns that icon, and either path rebuilds its automatic click geometry. API `setText` / `setIcon` updates replace the source used by subsequent refreshes; see [14 - API - Menus.md](14%20-%20API%20-%20Menus.md).

## Runtime notes

- Expression fields have no delimiter. In `text`, `card.title`, and `card.accent`, unquoted words are variable references, so literal text needs inner quotes: `"text": "'Idle'"`.
- `round(x)` is `floor(x + 0.5)`, so `round(-2.5)` is `-2`, not `-3`.
- `%` keeps the sign of its left operand; use `mod` for a non-negative wrap.
- Whole-valued numbers stringify without a decimal point: `str(54.0)` is `54`, `str(3.5)` is `3.5`.
- `plain(s)` strips only legacy codes matching `&[0-9A-Fa-fK-Ok-oRr]`; any other `&` survives.
- `readable(s)` uses Java split semantics, so leading and doubled underscores produce leading and doubled spaces.
- Lists exist only as `palette` arguments — there is no indexing syntax, and stringifying a list is an error.
- The PlaceholderAPI lookup used by menu JSON is re-probed roughly every second, so definition-side expansion starts working shortly after PlaceholderAPI enables late. HoloUi's own expansion registration has no such retry.

## The `%holoui_%` expansion

HoloUi also publishes placeholders for other plugins to consume. That expansion, its keys, and its failure behaviour are documented in [15 - API - Placeholders.md](15%20-%20API%20-%20Placeholders.md).
