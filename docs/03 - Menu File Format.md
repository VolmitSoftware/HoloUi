# Menu File Format

A menu file is a JSON document describing one holographic menu: where it sits relative to the player, how it behaves while open, and which components it contains. This page covers file discovery, menu ids, every top-level key, the component wrapper, and the discriminator enums that select component, icon and action payloads. The payloads themselves are documented in [04 - Components & Hitboxes.md](04%20-%20Components%20%26%20Hitboxes.md), [05 - Icons.md](05%20-%20Icons.md) and [06 - Actions.md](06%20-%20Actions.md).

## Source of truth

| Concern | File |
|---|---|
| Menu object | `src/main/java/art/arcane/holoui/config/MenuDefinitionData.java` |
| Component wrapper | `src/main/java/art/arcane/holoui/config/MenuComponentData.java` |
| Discriminators | `src/main/java/art/arcane/holoui/enums/` |
| Discovery and parsing | `src/main/java/art/arcane/holoui/config/ConfigManager.java` |
| Positioning | `src/main/java/art/arcane/holoui/menu/MenuSession.java` |
| JSON Schema | `schema/holoui.schema.json` |

## File location and menu id

Menu files live in the plugin data folder. The plugin name is `holoui`, so `getDataFolder()` resolves to `plugins/holoui`:

```
plugins/holoui/menus/     menu definition files
plugins/holoui/images/    image assets referenced by textImage / animatedTextImage icons
```

Both directories are created on first enable if absent.

### Id derivation

The menu id comes from the file's case-preserving relative path under `menus/`, never from the file contents. `ConfigManager` converts path separators to `/`, removes the final `.json`, and assigns that value with `MenuDefinitionData.setId(...)`, overwriting anything the JSON supplied.

| File | Menu id |
|---|---|
| `menus/shop.json` | `shop` |
| `menus/main_menu.json` | `main_menu` |
| `menus/Shop.JSON` | `Shop` |
| `menus/shops/weapons/main.json` | `shops/weapons/main` |

Ids are case sensitive. They are the key used by `/holoui open <id>` or `/holoui open menu=<id>` ([02 - Commands & Permissions.md](02%20-%20Commands%20%26%20Permissions.md)) and by `HoloUiService.open(plugin, player, String menuId)` and `menuIds()` ([14 - API - Menus.md](14%20-%20API%20-%20Menus.md)). The bare form is rewritten to `menu=<id>` before Director runs.

`id` is therefore not a menu key. `MenuDefinitionData` carries an `id` field, so a JSON `"id"` deserializes into it, but the loader immediately replaces it with the file base name. Setting it has no effect.

### Extension and directory layout

Regular non-symbolic `*.json` files are discovered recursively without following symbolic links. The extension match is case insensitive, so `Shop.JSON` loads as `Shop`; other extensions, every symbolic-link file or directory, and files beneath any hidden path segment (a segment beginning with `.`) are ignored. Normalized-path checks also reject files outside `menus/`.

The boot scan and both watchers share the same recursive predicate, so accepted nested menus behave identically at startup and during hot reload.

### Reloading

Two watchers run against `menus/`. Session lifecycle is covered in [11 - Runtime Architecture.md](11%20-%20Runtime%20Architecture.md).

| Interval | Detects | Effect |
|---|---|---|
| 5 ticks | modified files | file is re-parsed, all open sessions of that id are destroyed, affected players receive a notice plus `ENTITY_EXPERIENCE_ORB_PICKUP`, the registry entry is replaced |
| 20 ticks | created / deleted files | created files are parsed and registered; deleted files unregister the id and destroy its open sessions |

Both watchers apply the same recursive filter. Creating a nested directory registers its accepted descendants; deleting one unregisters every menu id beneath that path prefix.

Changes under `images/` trigger a visual refresh of open sessions on the same cadence.

### Parse failures

A zero-byte file logs `Menu config "<id>.json" is empty, ignoring.` and is skipped. Any other failure — malformed JSON, an unknown `type` discriminator — logs a stack trace and leaves the previously registered definition, if any, in place. A failed file never partially registers.

## Parsing behavior

Menu files are parsed with `art.arcane.volmlib.util.bukkit.json.BukkitJson`, a Gson instance configured with `setLenient()`.

- Lenient mode accepts unquoted keys, single-quoted strings and trailing commas.
- Unknown JSON keys are silently ignored. A `"$schema"` key added for editor tooling is harmless.
- Collection-typed keys accept a single object in place of a one-element array, via `SingleCollectionTypeFactory`. `"components": {…}` parses the same as `"components": [{…}]`. This applies to `components`, `actions`, `trueActions` and `falseActions`.

There is no load-time validation. The plugin does not check menu files against `holoui.schema.json` or against anything else. A file that parses as JSON registers. Structural problems — a missing `offset`, a missing `components`, a component with no `data` — surface as an exception when a player opens the menu, not when the file is loaded.

## Top-level keys

The `vector3` type is a JSON array of exactly three numbers, `[x, y, z]`.

| Key | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `offset` | `vector3` | yes | none | Position of the menu center relative to the opening player. See [Offset semantics](#offset-semantics). |
| `components` | array of component | yes | none | The components that make up the menu. May also be given as a single component object. |
| `lockPosition` | boolean | no | `false` | When true the player cannot move while the menu is open. `PlayerMoveEvent` has its destination X/Y/Z rewritten back to the origin and any non-zero velocity is zeroed. Rotation is unaffected. This freeze branch runs before the movement range check. |
| `followPlayer` | boolean | no | `false` | When true the menu re-anchors and tracks the player's current yaw on every accepted move, including rotation-only moves. With `lockPosition`, translation is frozen but the menu still adopts the allowed look yaw. Respawn and allowed teleport handling re-anchor either mode; follow menus also adopt the destination yaw, while non-follow menus retain their current facing. |
| `maxDistance` | number | no | `6.0E7` | Maximum distance in blocks between the player and the menu center before the menu closes with reason `MOVED_OUT_OF_RANGE`. Clamped to `[0, 6.0E7]`; `null` or absent yields `6.0E7`. |
| `closeOnDeath` | boolean | no | `false` | Close the menu on `PlayerDeathEvent`, with reason `DEATH`. Independent of the respawn check, which closes with reason `RESPAWN` whenever the respawn location is out of range or in another world. |
| `closeOnTeleport` | boolean | no | `false` | Close the menu on `PlayerTeleportEvent`, with reason `TELEPORT`. When false, a teleport that lands in range moves the menu with the player; a teleport that lands out of range or in another world closes it regardless of this flag. |

`lockPosition` and `followPlayer` are separate axes: `lockPosition` constrains the player, `followPlayer` constrains the menu. A menu with neither set stays fixed in the world while the player is free to walk away from it, up to `maxDistance`. `/holoui move` can still re-anchor any open menu manually; it does not change either setting or write the new anchor into this file.

Any world change invalidates the session regardless of `maxDistance`, because the range check requires the player's location and the menu center to share a world.

### Offset semantics

`offset` is interpreted in the menu's current player-relative frame:

| Axis | Direction |
|---|---|
| `+X` | to the player's right |
| `+Y` | up |
| `+Z` | forward, away from the player |

`MenuTransform` owns the anchor, facing yaw, pitch, roll, and scale used by the entire menu. A personal menu anchors at the player's **feet** `Location` with zero pitch and roll; a persistent world board supplies its complete stored transform. The transform mirrors the configured X coordinate, applies roll around Z, pitch around X, then the negative facing yaw around Y. It does not scale the menu-level offset:

```java
Vector worldOffset = new Vector(-offset.getX(), offset.getY(), offset.getZ())
    .rotateAroundZ(Math.toRadians(roll))
    .rotateAroundX(Math.toRadians(pitch))
    .rotateAroundY(Math.toRadians(-facingYaw));
Location menuOrigin = anchor.clone().add(worldOffset);
```

At open, the transform captures the player's eye yaw. A following menu replaces both its anchor and facing yaw on accepted move events, so turning in place rotates the menu around the player. Respawn and allowed teleport handling also adopt the destination yaw for following menus. Static menus and `/holoui move` replace only the anchor, preserving their current facing. The command is therefore translation-only.

The range check is `menuOrigin.distanceSquared(loc) <= maxDistance * maxDistance + offsetDistance`, so the effective allowance includes the unscaled offset length.

The menu `offset` is **not** multiplied by the `uiScale` setting. Component offsets are.

## The component wrapper

Each entry of `components` is an object with exactly three meaningful keys. It is the Java record `MenuComponentData(String id, Vector offset, ComponentData data)`.

| Key | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `id` | string | yes | none | Identifier for this component within the menu. Used as the map key for API icon updates (`HoloMenuHandle.setText` / `setItem` / `setIcon`). Duplicates resolve first-wins; later components with that id are ignored. |
| `offset` | `vector3` | yes | none | Position relative to the menu center. Same axis convention as the menu `offset`. |
| `data` | object | yes | none | The component payload. Its `type` key is the discriminator. |

### Component offset scaling

The raw component offset is retained by the component and resolved through the session's `MenuTransform` whenever the transform changes:

```java
Vector worldOffset = new Vector(-offset.getX() * uiScale, offset.getY() * uiScale,
    offset.getZ() * uiScale)
    .rotateAroundZ(Math.toRadians(roll))
    .rotateAroundX(Math.toRadians(pitch))
    .rotateAroundY(Math.toRadians(-facingYaw));
Location componentPosition = menuOrigin.clone().add(worldOffset);
```

`uiScale` is the `UI_SCALE` setting in `plugins/holoui/settings.json`, default `1.00`, clamped to `[0.25, 4.00]` — see [01 - Installation & Configuration.md](01%20-%20Installation%20%26%20Configuration.md). Menu offsets are not scaled, so raising `uiScale` spreads components apart around a center that does not move. A settings refresh updates the session transform before components and their icon and hitbox geometry are rebuilt.

### The `data` discriminator

`data.type` selects the payload class. The adapter (`EnumType`) removes `type` from the object and hands the remainder to the type-specific adapter, so `type` never collides with a payload key. A missing `type` raises `Missing type`; an unrecognized value raises `Unknown type: <value>`. Either failure aborts the whole file.

The same mechanism applies at two further levels, both nested inside `data`:

| Level | Key | Enum | Where it appears | Documented in |
|---|---|---|---|---|
| component payload | `data.type` | `MenuComponentType` | every entry of `components` | [04 - Components & Hitboxes.md](04%20-%20Components%20%26%20Hitboxes.md) |
| icon payload | `type` | `MenuIconType` | `data.icon` on `button` and `decoration`; `data.trueIcon` and `data.falseIcon` on `toggle` | [05 - Icons.md](05%20-%20Icons.md) |
| action payload | `type` | `MenuActionType` | every entry of `data.actions` on `button`; `data.trueActions` and `data.falseActions` on `toggle` | [06 - Actions.md](06%20-%20Actions.md) |

## Enum values

The tables give the canonical JSON spellings. The three `type` discriminators accept only those spellings. Gson-backed command and sound `source` fields also accept the Java enum constant names shown in the next column, including `GLOBAL`, `PLAYER`, and `MUSIC`; exports and the schema use the canonical lowercase forms.

### `MenuComponentType` — `data.type`

| JSON | Java constant | Payload class |
|---|---|---|
| `button` | `BUTTON` | `ButtonComponentData` |
| `decoration` | `DECO` | `DecoComponentData` |
| `toggle` | `TOGGLE` | `ToggleComponentData` |

The JSON spelling is `decoration`; the constant is `DECO`.

### `MenuIconType` — icon `type`

| JSON | Java constant | Payload class | Usable in menu JSON |
|---|---|---|---|
| `item` | `ITEM` | `ItemIconData` | yes |
| `block` | `BLOCK` | `BlockIconData` | yes |
| `customItem` | `CUSTOM_ITEM` | `CustomItemIconData` | yes |
| `animatedTextImage` | `ANIMATED_TEXT_IMAGE` | `AnimatedImageData` | yes |
| `textImage` | `TEXT_IMAGE` | `TextImageIconData` | yes |
| `text` | `TEXT` | `TextIconData` | yes |
| `entity` | `ENTITY` | `EntityIconData` | yes |
| `itemStack` | `ITEM_STACK` | none | no |

`itemStack` declares a `null` payload class in the enum. `EnumType` filters null-typed constants out of its adapter table, so the spelling fails deserialization with `Unknown type: …`. It exists to carry a live `ItemStack` handed in through the Java API and has no JSON form.

`customItem` is functional and declared in `holoui.schema.json`. See [08 - Custom Items & Item Providers.md](08%20-%20Custom%20Items%20%26%20Item%20Providers.md).

Every JSON-authorable display icon except `entity` accepts an optional `style` object. It controls display-entity billboard, text flags and background, brightness, render range and culling, glow, shadows, opacity, and per-axis scale; absent or `null` uses the fixed, opaque, uniform runtime defaults. `block` uses a packet-only block display with the material's default state. Entity icons instead use raw packet entities and reject `style`; their explicit `width` and `height` define the editor footprint and automatic click plane. The complete field table and geometry rules are in "05 - Icons.md".

Text icons also accept `refreshTicks`, an integer from `0` through `1200`. Omission refreshes PlaceholderAPI text every `10` ticks, while `0` keeps the value rendered at icon creation.

### `MenuActionType` — action `type`

| JSON | Java constant | Payload class |
|---|---|---|
| `command` | `COMMAND` | `CommandActionData` |
| `sound` | `SOUND` | `SoundActionData` |
| `message` | `MESSAGE` | `MessageActionData` |
| `teleport` | `TELEPORT` | `TeleportActionData` |
| `connect` | `CONNECT` | `ConnectActionData` |
| `navigate` | `NAVIGATE` | `NavigationActionData` |

### `MenuActionCommandSource` — `source` on a `command` action

Canonical spellings come from Gson `@SerializedName`, and one does not match its constant name.

| JSON | Java constant | Meaning |
|---|---|---|
| `player` | `PLAYER` | dispatched as the clicking player |
| `server` | `GLOBAL` | dispatched from the console |

### `SoundSource` — `source` on a `sound` action

| JSON | Java constant | Bukkit `SoundCategory` |
|---|---|---|
| `master` | `MASTER` | `MASTER` |
| `music` | `MUSIC` | `MUSIC` |
| `record` | `RECORD` | `RECORDS` |
| `weather` | `WEATHER` | `WEATHER` |
| `block` | `BLOCK` | `BLOCKS` |
| `hostile` | `HOSTILE` | `HOSTILE` |
| `neutral` | `NEUTRAL` | `NEUTRAL` |
| `player` | `PLAYER` | `PLAYERS` |
| `ambient` | `AMBIENT` | `AMBIENT` |
| `voice` | `VOICE` | `VOICE` |

Three spellings differ from their `SoundCategory` counterpart: `record`/`RECORDS`, `block`/`BLOCKS`, `player`/`PLAYERS`.

An unrecognized command source becomes `null` and therefore uses the `player` default. An unrecognized sound source becomes `null` and therefore uses the `master` default.

## Examples

### Minimal

`plugins/holoui/menus/hello.json`, menu id `hello`. One decorative text label, 1.5 blocks above the player's feet and 2 blocks in front.

```json
{
  "offset": [0, 1.5, 2],
  "components": [
    {
      "id": "greeting",
      "offset": [0, 0, 0],
      "data": {
        "type": "decoration",
        "icon": {
          "type": "text",
          "text": "&aHello, %player_name%"
        }
      }
    }
  ]
}
```

### Full

`plugins/holoui/menus/shop.json`, menu id `shop`. One of each component type, representative actions, a custom hitbox, and every optional top-level key set explicitly.

```json
{
  "offset": [0, 1.6, 2.25],
  "lockPosition": true,
  "followPlayer": false,
  "maxDistance": 8,
  "closeOnDeath": true,
  "closeOnTeleport": true,
  "components": [
    {
      "id": "title",
      "offset": [0, 0.75, 0],
      "data": {
        "type": "decoration",
        "icon": {
          "type": "text",
          "text": "&6&lServer Shop"
        }
      }
    },
    {
      "id": "open-market",
      "offset": [-0.6, 0, 0],
      "data": {
        "type": "button",
        "highlightModifier": 0.1,
        "icon": {
          "type": "item",
          "item": "minecraft:emerald",
          "count": 1
        },
        "hitbox": {
          "width": 0.5,
          "height": 0.5,
          "offset": [0, 0, 0],
          "anchor": "button"
        },
        "actions": [
          {
            "type": "command",
            "command": "market open",
            "source": "player"
          },
          {
            "type": "sound",
            "sound": "minecraft:ui.button.click",
            "source": "master",
            "volume": 1.0,
            "pitch": 1.0
          }
        ]
      }
    },
    {
      "id": "toggle-notifications",
      "offset": [0.6, 0, 0],
      "data": {
        "type": "toggle",
        "highlightModifier": 0.1,
        "condition": "%shop_notifications%",
        "expectedValue": "true",
        "trueIcon": {
          "type": "textImage",
          "path": "shop/bell_on.png"
        },
        "falseIcon": {
          "type": "textImage",
          "path": "shop/bell_off.png"
        },
        "trueActions": [
          {
            "type": "command",
            "command": "shop notify off",
            "source": "player"
          }
        ],
        "falseActions": [
          {
            "type": "command",
            "command": "shop notify on",
            "source": "player"
          }
        ]
      }
    }
  ]
}
```

Both examples use only keys and value shapes declared by `schema/holoui.schema.json`, including `closeOnTeleport`.

Placeholder syntax used in `text` and `condition` is covered in [07 - Expressions & Placeholders.md](07%20-%20Expressions%20%26%20Placeholders.md).

## Relationship to `holoui.schema.json`

`schema/holoui.schema.json` is a JSON Schema 2020-12 document with `$id` `https://volmit.com/holoui/schema.json`. It is checked into the repository under `schema/`, is not bundled into the plugin jar, is not read at runtime, and is not applied by the loader. It exists for editor tooling: point an IDE's JSON schema mapping at it to get completion and inline validation on files under `menus/`, or add a `"$schema"` key to a menu file, which Gson ignores. See [12 - Web Editor & Schemas.md](12%20-%20Web%20Editor%20%26%20Schemas.md).

Its top-level `required` is `["offset", "components"]`, and its `properties` are `offset`, `lockPosition`, `followPlayer`, `maxDistance`, `closeOnDeath`, `closeOnTeleport`, and `components`. The icon `type` enum includes `text`, `textImage`, `animatedTextImage`, `item`, `block`, `customItem`, and `entity`.

Because the schema is advisory rather than enforced:

- Some files the schema rejects still load because runtime coercion and collection handling are broader than the schema.
- A file the schema accepts can still fail to load, because the schema does not enforce every runtime nullability and open-time rule, and it does not model the API-only `itemStack` icon type.

### Schema and implementation differences

| Item | Schema | Implementation |
|---|---|---|
| `itemStack` icon type | absent (intentional; API-only) | present in `MenuIconType`, not deserializable from JSON |
| `components` cardinality | `type: array` | array, or a single object via `SingleCollectionTypeFactory` |
| single-object collections | not modelled | accepted for `components`, `actions`, `trueActions`, `falseActions` |
| unknown keys | permitted; `additionalProperties` is unconstrained everywhere except `hitbox` | ignored |

`$defs/hitbox` is the only object in the schema that sets `additionalProperties: false`.

`HoloUiSchemaContractTest` structurally pins selected high-risk fields: `closeOnTeleport`, integer item counts, non-blank image/item paths, non-empty animation sources, optional integer animation speed, unrestricted integer `customModelValue`, the block icon discriminator and namespaced material key, and the entity icon discriminator and dimension bounds. It does not run a general JSON Schema validator or prove full parser equivalence.

`schema/holoui-preview.schema.json` sits in the same directory but is unrelated to menu files. It describes the container-preview document format under `src/main/resources/previews/`, covered in [09 - Container Previews.md](09%20-%20Container%20Previews.md).

## Runtime notes

- The file name always wins over a JSON `"id"`.
- The menu `offset` anchors to the player's feet. The opening eye yaw supplies the initial facing; following menus replace it with the current yaw when the player moves or turns.
- Menu and component offsets mirror X and rotate through the same session transform. Component offsets are scaled by `uiScale`; the menu offset is not.
- `lockPosition` bypasses ordinary movement range validation and freezes destination X/Y/Z. A following locked menu still adopts the destination yaw, while respawn and teleport retain their own range and close checks.
- `maxDistance` allowance includes the offset length, so the effective walk-away radius is slightly larger than the configured value.
- `decoration` maps to the `DECO` constant, `server` maps to `GLOBAL`, and `record` / `block` / `player` map to `RECORDS` / `BLOCKS` / `PLAYERS`. Gson also accepts Java enum names for action-source values, while the schema and editor export the canonical lowercase spellings.
- `itemStack` appears in `MenuIconType` but cannot be written in JSON.
- Duplicate component ids do not reject the file. The first component with an id is used and later duplicates are ignored with a warning when a session opens.
- Regular `*.json` files load recursively; relative paths become slash-separated ids. Hidden path segments, other extensions, and paths resolving outside `menus/` are ignored.
