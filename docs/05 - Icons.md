# Icons

An icon is the visual payload of a component: every drawing component owns exactly one `MenuIcon`, which owns one or more client-side entities sent as packets to the session player only. This document covers every icon type reachable from a menu JSON file, the keys each one accepts, how visuals are turned into packets, when placeholders resolve, and what happens when an icon fails to build.

## Icon types

`icon` is a discriminated union. The `type` member selects the record; the remaining members are deserialised into it.

| `type` | Runtime class | Client entities produced |
| --- | --- | --- |
| `text` | `TextMenuIcon` | one text display per line |
| `textImage` | `TextImageMenuIcon` | one text display per image row |
| `animatedTextImage` | `AnimatedTextImageMenuIcon` | one text display per row of frame 0 |
| `item` | `ItemMenuIcon` | one item display, plus one text display when the stack amount is above 1 |
| `block` | `BlockMenuIcon` | one block display using the material's default block state |
| `customItem` | `ItemMenuIcon` | same as `item` |
| `entity` | `EntityMenuIcon` | one raw living entity spawned only through packets |

One further enum constant exists but is not addressable from JSON — see [Unreachable types](#unreachable-types).

Parsing errors raised by the union adapter are `JsonParseException`:

| Condition | Message |
| --- | --- |
| No `type` member | `Missing type` |
| `type` is not a JSON primitive | `Type must be a string` |
| `type` is not a known discriminator | `Unknown type: <value>` |

Record component names are the JSON keys verbatim; no field-naming policy is applied. Unknown members are ignored silently. Any collection-typed key also accepts a single bare value in place of an array.

Dispatch to the runtime class is an `instanceof` chain over the parsed record. Data that matches nothing, including `null`, produces the missing-icon `TextImageMenuIcon`.

## Display style

Every JSON-authorable icon type except `entity` accepts the same optional `style` object. Omission, explicit `null`, and an empty object all resolve to the runtime defaults; an authored object is emitted with its resolved fields by the Java serializer. Entity icons use raw entity packets rather than display entities, so their schema rejects `style` and Gson ignores it if validation is bypassed.

```json
{
  "type": "text",
  "text": "<gold>Shop</gold>",
  "style": {
    "billboard": "center",
    "shadow": true,
    "seeThrough": false,
    "textAlignment": "center",
    "backgroundArgb": "#80000000",
    "textOpacity": 220,
    "lineWidth": 160,
    "blockLight": 15,
    "skyLight": 15,
    "viewRange": 2.0,
    "shadowRadius": 0.25,
    "shadowStrength": 0.6,
    "cullingWidth": 4.0,
    "cullingHeight": 2.0,
    "glowColor": "#FFFFAA00",
    "scaleX": 1.4,
    "scaleY": 0.8,
    "scaleZ": 1.0
  }
}
```

| Key | Type and accepted range | Default | Runtime effect |
| --- | --- | --- | --- |
| `billboard` | `fixed`, `vertical`, `horizontal`, or `center` | `fixed` | Display billboard metadata. Unknown and non-string values reject parsing. |
| `shadow` | boolean | `false` | Text glyph shadow flag. On item icons this applies only to the optional count text. |
| `seeThrough` | boolean | `false` | Text see-through flag. On item icons this applies only to the optional count text. |
| `textAlignment` | `center`, `left`, or `right` | `center` | Text alignment flag. Unknown and non-string values reject parsing. |
| `backgroundArgb` | `#AARRGGBB` | `#00000000` | Text background colour, including alpha. |
| `textOpacity` | integer `[0, 255]` | `255` | Text opacity metadata. |
| `lineWidth` | integer `[1, 16384]` | `2000` | Client text-wrap width. |
| `blockLight`, `skyLight` | paired integers `[0, 15]`, or both omitted | omitted | Packs the display brightness override; supplying only one rejects parsing. |
| `viewRange` | finite number `[0.01, 64]` | `1` | Display view-range metadata. |
| `shadowRadius` | finite number `[0, 64]` | `0` | Display entity shadow radius. |
| `shadowStrength` | finite number `[0, 1]` | `0` | Display entity shadow strength. |
| `cullingWidth`, `cullingHeight` | finite number `[0, 4096]` | `0` | Display render-culling dimensions; these do not define the click plane. |
| `glowColor` | `#AARRGGBB` or `null` | `null` | Enables the glowing entity flag and supplies its colour override. |
| `scaleX`, `scaleY`, `scaleZ` | finite number `[0.01, 64]` | `1` | Multiplies the session display scale independently on each axis. Automatic click geometry uses X and Y; Z is visual only. |

Text-specific fields apply to every display produced by `text`, `textImage`, and `animatedTextImage`. `item`, `block`, and `customItem` apply generic display fields to their display entities; text-specific fields affect only an item's optional count label. `itemStack` remains API-only and uses the default style.

Invalid ARGB strings, out-of-range values, unpaired brightness values, and invalid style enums fail menu deserialization. Unknown keys remain ignored by Gson, although the schema marks them invalid.

## `text`

```json
{ "type": "text", "text": "&6Balance:\n<gold>%vault_eco_balance%" }
```

| Key | Type | Required | Default when absent | Notes |
| --- | --- | --- | --- | --- |
| `text` | string | yes (schema) | `null` | `null` is treated as `""` and renders as a single empty line |
| `refreshTicks` | integer `0`–`1200` | no | `10` | Interval for live PlaceholderAPI expansion; `0` disables updates after the initial render |

`text` is split on the literal newline character `\n`. Each authored line becomes its own text display entity. The default `lineWidth` of `2000` effectively avoids wrapping; a smaller styled value lets the client wrap within that entity, but automatic hitbox geometry and the editor preview continue to use the authored line and do not model that internal client wrap.

### Text formatting

Each line is run through `TextUtils.parse`, which rewrites legacy codes into MiniMessage tags with an explicit mapping and then calls `MiniMessage.miniMessage().deserialize(text)`. The rewrite is one left-to-right scan: `&` and `§` are both accepted as the prefix, the code letter is case-insensitive, and a prefix not followed by a known code is copied through verbatim.

| Legacy | MiniMessage tag |
| --- | --- |
| `&0`–`&9`, `&a`–`&f` | `<black>`, `<dark_blue>`, `<dark_green>`, `<dark_aqua>`, `<dark_red>`, `<dark_purple>`, `<gold>`, `<gray>`, `<dark_gray>`, `<blue>`, `<green>`, `<aqua>`, `<red>`, `<light_purple>`, `<yellow>`, `<white>` |
| `&k` | `<obfuscated>` |
| `&l` | `<bold>` |
| `&m` | `<strikethrough>` |
| `&n` | `<underlined>` |
| `&o` | `<italic>` |
| `&r` | `<reset>` |

Every generated tag is one MiniMessage accepts, so the two notations mix freely in one string and no code leaks through as literal text. The scan runs after placeholder substitution, so section-sign codes emitted by a placeholder are rewritten as well. Legacy hex sequences (`&x&r&r&g&g&b&b`) are not a supported form; use MiniMessage's `<#rrggbb>`.

### Placeholder resolution timing

Substitution is `Placeholders.setPlaceholders(player, line)`, which reaches PlaceholderAPI reflectively. It is a no-op when the player is `null` or the string contains no `%`. Plugin presence is re-probed at most once per 1000 ms; a lookup or invocation failure logs once and returns the text unresolved. There is no expression engine on this path; see "07 - Expressions & Placeholders.md".

Substitution happens when the icon is constructed and then every `refreshTicks` while that text icon is visible. Omission uses `10` ticks (twice per second); `0` freezes the initially rendered value. Text without a paired `%name%` token does no periodic placeholder work.

The other re-render points are:

| Trigger | Mechanism |
| --- | --- |
| Component open | `MenuComponent.open` builds a new `TextMenuIcon` |
| `refreshVisuals` | `SessionHolder.refreshVisuals` closes and reopens every open component. Fired by `uiScale`/`previewScale` changes and by image-asset add/change/delete (folder watcher: change poll every 5 ticks, create/delete poll every 20 ticks) |
| Menu file change | The config watcher destroys and rebuilds sessions of that menu type |
| Toggle click | `ToggleComponent.onClick` swaps to the other pre-built icon |
| API | `MenuComponent.applyIcon(HoloIcon.Text)` calls `TextMenuIcon.updateText` |

`updateText` re-renders all lines and pushes only changed lines as metadata packets when the line count is stable. A changed line count respawns just that icon. Either change increments its geometry revision, so the owning clickable rebuilds its automatic plane before hover and click processing. It returns `false` only when its display-entity state is unavailable or inconsistent.

`ToggleComponent` builds `trueIcon` and `falseIcon` once in its constructor and ticks only the currently visible icon. A hidden text icon resumes its configured refresh after it becomes visible. The `condition` placeholder is still evaluated only once, in the constructor, after which the state flips only on click. See "04 - Components & Hitboxes.md".

## `item`

```json
{ "type": "item", "item": "minecraft:diamond_sword", "count": 1, "customModelValue": 3 }
```

| Key | Type | Required | Default when absent | Notes |
| --- | --- | --- | --- | --- |
| `item` | string (Bukkit `Material`) | yes (schema) | `null` | Record component is `materialType`, mapped by `@SerializedName("item")` |
| `count` | integer | no | `0` | `0` and negatives are coerced to `1` at icon construction |
| `customModelValue` | integer | no | `0` | Applied unconditionally, including `0` |

`item` is deserialised by a registry-backed adapter: `NamespacedKey.fromString(string)`, then a lookup that tries the Bukkit `Registry`, then public static `Keyed` fields on `Material`, then enum constant names lowercased into `minecraft:` keys. The adapter is lenient — an id it cannot resolve becomes `null` instead of an exception. Consequences:

- Ids must be namespaced-key legal, that is, lower case (`diamond_sword`, `minecraft:diamond_sword`). `DIAMOND_SWORD` is not a legal key string and resolves to nothing.
- A well-formed but unknown key also resolves to nothing.
- Either way parsing succeeds and the rest of the menu file is kept; the icon itself fails at construction with `MenuIconException` and falls back to the missing icon.

The stack is built as `new ItemUtils.Builder(material, count > 0 ? count : 1).modelData(customModelValue).get()`. `modelData` is a no-op when the material has no `ItemMeta`; otherwise it calls `ItemMeta#setCustomModelData(int)` unconditionally, so an icon authored without `customModelValue` still carries an explicit custom model data of `0`.

## `block`

```json
{ "type": "block", "block": "minecraft:stone" }
```

`block` must be a lowercase namespaced Bukkit material id whose resolved material reports `isBlock()`. The icon renders the material's default block state as one packet-only `BLOCK_DISPLAY`; it does not create or alter a world block, and the contract does not currently expose directional or other block-state properties.

The display is centred on its spawn origin with a `0.75` base scale and matching automatic click plane, both multiplied by `uiScale` and the style's X/Y scale. All generic display-style fields apply. Unknown ids and non-block materials raise `MenuIconException` during icon construction and use the normal missing-icon fallback.

## `customItem`

```json
{ "type": "customItem", "provider": "itemsadder", "item": "myitems:ruby", "count": 1 }
```

| Key | Type | Required | Default when absent | Notes |
| --- | --- | --- | --- | --- |
| `provider` | string | no | `null` | Provider registry id; `null`, blank, and `auto` all mean "try every ready provider in activation order" |
| `item` | string | yes in practice | `null` | Provider-specific id, passed through verbatim |
| `count` | integer | no | `0` | `0` and negatives are coerced to `1` after resolution |

`provider` is normalised with `trim().toLowerCase(Locale.ROOT)`. `item` is not trimmed or case-folded, so provider-native syntaxes such as `myitems:ruby` (ItemsAdder) or `SWORD:CUTLASS` (MMOItems) survive exactly as authored.

Resolution goes through `ItemProviderRegistry.resolve(provider, item)`, whose results are cached per `provider + " " + itemId` and handed out as clones. The registry returns `null` — which becomes a `MenuIconException` and the missing-icon fallback — when the item id is null or blank, when `customItems` is disabled in `settings.json`, when no provider matches, or when the provider is main-thread-only and the call is off-thread.

Provider ids, activation order, the `customItemProviders` allowlist, and the individual adapters are documented in "08 - Custom Items & Item Providers.md".

## Item layout

Both item flavours render through `ItemMenuIcon`, which differs only in how the `ItemStack` is obtained.

| Aspect | Behaviour |
| --- | --- |
| Block test | `item.getType().isBlock()` and the type is not blacklisted |
| Block blacklist | `BARRIER`, `LIGHT`, `HOPPER`, `TURTLE_EGG`, grass (`grass`/`short_grass`), `TALL_GRASS`, all 16 stained glass panes, `GLASS_PANE`, `POPPY`, `DANDELION` |
| Block placement | Transform-local `(0, BLOCK_OFFSET, 0.3)` from the icon position, where `BLOCK_OFFSET = -0.95`; scale and facing are applied by `MenuTransform` |
| Non-block placement | Transform-local `(0, -(ITEM_OFFSET + countOffset), 0)` from the baseline location, where `ITEM_OFFSET = 1.0` and `countOffset = 0.0` when amount is above 1 else `0.09` |
| Count text | Only when amount is above 1: `Component.text(amount)` in white bold at transform-local `(0, -NAMETAG_SIZE - 0.37, 0)` from the icon position, scale = `uiScale()` |
| `updateCount(int)` | Adds or removes the count entity and shifts the item display by `±0.09 * scale`, or renames the existing count entity |
| Rotation | Every fixed item display uses the session transform's `facingYaw + 180`. Block depth orbit, display yaw and hitbox yaw are reapplied together when the transform changes |
| `spawn()` | Creates entities at already oriented transform locations. Later follow or manual movement uses `applyTransform`, which teleports, explicitly reapplies display yaw, and restores the block orbit when applicable |

## `entity`

```json
{ "type": "entity", "entity": "minecraft:parrot", "width": 0.5, "height": 0.9 }
```

| Key | Type | Required | Default when absent | Notes |
| --- | --- | --- | --- | --- |
| `entity` | lowercase Bukkit entity id | yes | `null` | An omitted namespace defaults to `minecraft`; the type must report both `isSpawnable()` and `isAlive()` |
| `width` | finite number `(0, 64]` | no | `1` | Visual footprint and automatic click-plane width in blocks at `uiScale = 1` |
| `height` | finite number `(0, 64]` | no | `1` | Visual footprint and automatic click-plane height in blocks at `uiScale = 1` |

The component anchor is the entity's feet. HoloUi sends a spawn packet, base entity metadata with no gravity, movement and orientation packets, and a destroy packet only to the menu viewer; it never creates a Bukkit world entity. Body yaw, pitch, and head yaw follow the session transform, so fixed entity icons turn with a following menu when the player turns. The explicit dimensions do not rescale the client model: they center the automatic plane half the authored height above the feet and scale that plane by `uiScale`.

## `textImage`

```json
{ "type": "textImage", "path": "logo.png" }
```

| Key | Type | Required | Default when absent | Notes |
| --- | --- | --- | --- | --- |
| `path` | string | yes (schema) | `null` | Record component is `relativePath`, mapped by `@SerializedName("path")`. Must resolve to a regular file inside the `images` folder |

Sources are files only; there is no URL support anywhere in the icon path. `ConfigManager.getImage` resolves both the images root and requested file to canonical paths, requires the result to remain under `plugins/holoui/images/`, and requires a regular file. Blank, absolute, missing, directory, `..` escape, and symlink-escape paths fail with `FileNotFoundException`. Format detection and decoding use Apache Commons Imaging.

JSON paths and API paths both pass through the canonical containment check. Paths supplied through the API are also validated earlier by `HoloText.sanitizePath`, which rejects blank values, values over the length limit, control characters, absolute paths, values containing `:`, and any `..` segment, and normalises `\` to `/`.

### Pixels to characters

One text display is emitted per pixel row, walking `x` left to right:

| Pixel | Emitted |
| --- | --- |
| Format is not JPEG and `(argb >>> 24) & 0xFF < 255` | a bold `" "` followed by a plain `" "` (two characters, the transparent spacer) |
| Otherwise | one `█` glyph coloured with `argb & 0x00FFFFFF` |

Transparency is binary: any alpha below 255 is fully transparent, anything else fully opaque. The JPEG exemption exists because JPEG carries no alpha channel.

## `animatedTextImage`

```json
{ "type": "animatedTextImage", "source": ["frame0.png", "frame1.png", "frame2.png"], "speed": 4 }
```

| Key | Type | Required | Default when absent | Notes |
| --- | --- | --- | --- | --- |
| `source` | array of string, or a single string | yes (schema) | `null` | Each entry is a separate image file. There is no GIF frame extraction |
| `speed` | integer | no | `0` | Ticks between frame advances |

Pixel mapping is the same as `textImage` with two differences: the JPEG exemption is absent, so the alpha test runs for every format; and each frame shorter than the tallest frame is padded at the bottom with blank rows built from that frame's own width using the same bold-space/space pair, so every frame ends up with the same line count.

`tick()` increments a counter every server tick; when the counter reaches `speed` it resets, advances the current frame modulo the frame count, and pushes the new frame. `speed = 1` advances every tick, and `speed = 0` (the value when the key is omitted) also advances every tick, because the comparison runs after the increment. The tick source is the session manager's 1-tick scheduled task, and it reaches only components that are open.

Frame updates send one metadata packet per row. There is no respawn and no repositioning; the entity count is fixed at spawn from frame 0's line count, which the bottom-padding guarantees matches every other frame.

Each frame file is decoded once while an `AnimatedTextImageMenuIcon` is constructed. Icons are reconstructed on component open and on every `refreshVisuals`, so image hot reload rebuilds and re-reads the frame files for each affected icon.

## Rendering, scale and bounding boxes

All icons share `MenuIcon`:

| Constant / method | Value | Effect |
| --- | --- | --- |
| `NAMETAG_SIZE` | `1/16 * 3.5` = `0.21875` | Unscaled line height |
| `TEXT_DISPLAY_BASELINE` | `4.5 / 40` = `0.1125` | Baseline correction used by the bounding box |
| `uiScale()` | `session.getTransform().scale()` | Session scale sourced from `settings.json` key `uiScale`, default `1.00`, clamped to `[0.25, 4.00]`; a visual refresh updates the transform before respawning icons |
| `localLineHeight()` | `NAMETAG_SIZE * style.scaleY` | Transform-local line spacing |
| `scaledLineHeight()` | `NAMETAG_SIZE * uiScale() * style.scaleY` | Rendered line spacing and automatic hitbox height |
| `scaledCharacterWidth()` | `NAMETAG_SIZE * uiScale() * style.scaleX` | Automatic text and image hitbox width unit |
| `billboardMode()` | selected style billboard byte `0..3` | Display orientation mode |
| `textFlags()` | alignment plus shadow and see-through bits | Text display flags |
| `textBackgroundColor()` | style `backgroundArgb` bits | Text display background |

`fixed` displays use the session transform's absolute `displayYaw = facingYaw + 180` plus its pitch and roll. Following menus therefore rotate fixed icons when the player turns. The three non-fixed modes leave orientation to the client and skip fixed-orientation packets after spawn and transform changes.

For display-backed icons, `spawn()` places entities at transform-local `(0, -NAMETAG_SIZE * style.scaleY, 0)` from the icon position. Entity icons instead spawn directly at the component anchor because that anchor is their feet. Fixed spawn locations carry the transform orientation, and `applyTransform()` explicitly restores it after later movement.

Display entity metadata:

| Kind | Settings |
| --- | --- |
| Text display | no gravity; styled billboard, entity flags, brightness, view range, entity shadow, culling size, glow, text flags, background, opacity, line width, and scale `(uiScale * scaleX, uiScale * scaleY, uiScale * scaleZ)` |
| Item display | no gravity and `itemDisplayType 0`; the same generic billboard, entity flags, brightness, view range, entity shadow, culling, glow, and non-uniform scale metadata |
| Block display | no gravity; block-state metadata at index 23; the same generic display metadata, with a centred `0.75` base scale |
| Raw entity | no gravity through base entity metadata only; no display-only metadata indices are sent |

For text, text-image, and animated icons the first line is placed at `loc + ((lineCount - 1) / 2 * lineHeight) - lineHeight` on Y, and each subsequent line is one `lineHeight` lower.

Bounding boxes (`CollisionPlane`), used by clickable components for hit testing, resolve their centres through the same transform and use its layout yaw:

| Icon | Centre | Width | Height |
| --- | --- | --- | --- |
| `TextMenuIcon` | `anchor - (0, 0.325 * uiScale * scaleY, 0)` | `max(plainLength * NAMETAG_SIZE * uiScale * scaleX / 2)` over lines | `lineCount * NAMETAG_SIZE * uiScale * scaleY` |
| `TextImageMenuIcon` | same | same rule | `(lineCount - 1) * lineHeight` |
| `AnimatedTextImageMenuIcon` | same | same rule over frame 0 | `(frame0LineCount - 1) * lineHeight` |
| `ItemMenuIcon` | `anchor - (0, 0.05 * uiScale, 0)` | `0.75 * uiScale * scaleX` | `0.75 * uiScale * scaleY` |
| `BlockMenuIcon` | `anchor - transform-local (0, 0.05, 0)` | `0.75 * uiScale * scaleX` | `0.75 * uiScale * scaleY` |
| `EntityMenuIcon` | `anchor + transform-local (0, height / 2, 0)` | `width * uiScale` | `height * uiScale` |

The text centre offset is `((2 * NAMETAG_SIZE) - TEXT_DISPLAY_BASELINE) * uiScale * scaleY`. Width uses the recursively concatenated plain text of the component tree, so MiniMessage tags do not inflate the box, but transparent image pixels — two space characters each — do. Non-fixed clickable billboard planes are reoriented toward the viewer before every hover test and again at the click snapshot; fixed planes preserve the complete session transform. See "04 - Components & Hitboxes.md".

## Failure modes

Expected icon validation and loading failures are reported as `MenuIconException`; unchecked constructor failures use the same missing-icon fallback. The expected throw sites are:

| Site | Message | Cause |
| --- | --- | --- |
| `item` | `Item icon has an unknown or invalid item id` | `item` is absent, is not a legal namespaced key (upper case, for example), or names nothing in the registry |
| `item` | `Unable to build an item icon from "%s"` | the material resolved but no stack could be built from it; the original is attached via `initCause` |
| `block` | `Block icon has an unknown or invalid block id` | `block` is absent, malformed, or names nothing in the Bukkit material registry |
| `block` | `Block icon material "%s" is not a block` | the material resolves but cannot be represented as a block display |
| `block` | `Unable to create the default block state for "%s"` | Paper could not create the material's default block data; the original is attached via `initCause` |
| `customItem` | `Unable to resolve custom item "%s" from provider "%s"` | the provider registry returned `null` |
| `entity` | `Entity icon has an unknown or invalid entity id` | `entity` is absent, malformed, or not present in the Bukkit entity registry |
| `entity` | `Entity icon type "%s" is not a spawnable living entity` | the id resolves to a player, item, projectile, display, interaction, or another unsafe type |
| `entity` | `Entity type "%s" is unavailable on this server version` | Bukkit accepts the type but PacketEvents cannot map it for the running server version |
| `textImage` | `Image icon has no path` | `path` is absent or blank |
| `textImage` | `Failed to load relative image "%s"!` | the path is absolute, escapes the canonical images root, is missing, is a directory, or could not be decoded; the original is attached via `initCause` |
| `animatedTextImage` | `Animated icon has no source frames` | `source` is absent or empty |
| `animatedTextImage` | `Animated icon has a frame without a path` | one `source` entry is null or blank |
| `animatedTextImage` | `Failed to construct animated icon!` | a frame path is absolute, escapes the canonical images root, is missing, or could not be decoded; the original is attached via `initCause` |

`MenuIcon.createIcon` catches `MenuIconException` and any unchecked exception a constructor lets out. It logs the stack with the component id (`An error occurred while creating a Menu Icon for the component "%s":`), logs `Falling back to missing icon.`, and returns the missing icon. That fallback is an eight-row, eight-glyph checkerboard: rows 1–4 are four `#000000` glyphs then four `#f800f8` glyphs, rows 5–8 are the reverse — black and magenta. If constructing the fallback itself throws, `createIcon` throws an `IllegalStateException` carrying both failures; the transactional session-open path then removes any partial entities and rejects the open.

A broken icon never costs more than that icon. Unresolvable item ids are tolerated by the parser, so the menu file is still registered and every other component in it still builds, and no icon failure reaches `spawn()`.

## Unreachable types

`MenuIconType` declares one constant with a `null` data class. The union adapter filters null-typed constants out of its map, so it is not reachable from JSON in either direction.

| Constant | State |
| --- | --- |
| `itemStack` | API only. `ItemStackIconData` wraps a live `org.bukkit.inventory.ItemStack` and is reachable through `HoloIcon.item(ItemStack)`, which clones on the way in and again on `stack()`; `ItemMenuIcon` clones once more before rendering |

`{"type":"itemStack"}` fails with `Unknown type: itemStack`, and serialising an `ItemStackIconData` fails the same way. There is no NBT or serialised-stack JSON form of an icon. See "13 - API - Getting Started.md" and "14 - API - Menus.md".

## Runtime notes

- An `item` icon authored without `customModelValue` still receives an explicit custom model data of `0`, because `setCustomModelData` is called unconditionally.
- `speed` omitted (`0`) behaves the same as `speed: 1`, advancing the animation every tick.
- Image paths are confined by canonical path, so traversal and symlink escapes cannot read outside `plugins/holoui/images/`.
- The visible text icon of a toggle refreshes on its own interval; the toggle `condition` remains a one-time constructor evaluation.
- `ConfigManager.getImages` (multi-frame decode) exists but is called by nothing; animated icons take a list of separate files, not a multi-frame GIF.

## Schema divergences

`schema/holoui.schema.json` `$defs.icon` requires `type` and recognizes the current runtime key names: `customModelValue` on `itemIcon`, `source` on `animatedTextImageIcon`, `refreshTicks` on `textIcon`, and `block`, `customItem`, and `entity` in the `type` enum. It requires a namespaced block key, integer item counts, non-blank image/item paths, at least one non-blank animation source, optional integer animation speed, text refresh intervals from `0` through `1200`, an integer `customModelValue` with no minimum, and positive entity dimensions no larger than 64. `itemStack` is intentionally unlisted because it is API-only and has no JSON form.

`HoloUiSchemaContractTest` pins those structural constraints and `closeOnTeleport`. The schema remains advisory: it is not read at runtime, no general JSON Schema validator is run by the loader, and Gson remains the behavioral authority. Unknown icon keys are permitted by the schema and ignored by Gson.

See "12 - Web Editor & Schemas.md".
