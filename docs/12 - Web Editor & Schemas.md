# Web Editor & Schemas

HoloUi ships alongside a browser-based menu editor kept in a separate repository, and the two are two surfaces of one menu contract. This page describes what the editor is, how the repositories stay synchronized, which files actually cross the boundary, and what the shipped JSON Schema files are and are not.

## The editor

`HUI-Web-Editor/` (sibling of this repository, Dart package `holoui_editor`) is a Jaspr client-side application that authors HoloUi configurations on a calibrated canvas and exports the files the plugin loads:

- `menus/<id>.json` for `plugins/holoui/menus/`
- `images.zip`, pixel-art icon sources laid out for `plugins/holoui/images/`
- Container preview documents for `plugins/holoui/previews/`

The editor application runs in the browser and requires no editor account. Its ordinary workspace is local browser storage; an optional capability-scoped relay is contacted only when the user opens a `#/sync/…` link. The editor is deployed to `https://holoui.volmitsoftware.com`; the plugin neither ships nor hosts it. `/holoui builder` prints a link to `HuiSettings.BUILDER_URL`, while `/holoui edit <menu>` can create a constrained round-trip session or an explicit confirmation-first one-way import fallback. See `01 - Installation & Configuration.md` and `02 - Commands & Permissions.md`.

## Workspace library and flow boards

The editor stores its library in browser storage as one versioned workspace document. Workspace version 2 gives every document a stable UUID independent of its display title and canonical runtime id, supports nested folders, preserves migrated version 1 documents in a protected `Unfiled` folder without silently evicting older documents, and reports duplicate runtime ids instead of renaming them.

Menu and preview documents retain the existing runtime JSON import and export formats. Runtime ids preserve case and nested paths such as `shops/tools/Confirm`; each slash-separated segment starts with a letter or number, uses only letters, numbers, dots, underscores, or hyphens, and cannot be empty, `.` or `..`.

`holoui-workspace.json` is the editor's portable, confirmation-first workspace bundle. It retains the workspace UUID, stable document UUIDs, nested folders, menu and preview JSON, editor-only flow-board metadata, and the browser image library; importing it replaces the current local workspace only after the user reviews its document, folder, and image counts.

Flow boards are editor-only saved views containing a folder scope, canvas or list preference, and node positions; they are not HoloUi world boards, never enter a menu export, and do not change an exported menu's bytes.

A flow board derives routes from native `navigate` actions and resolves runtime ids exactly and case-sensitively. It distinguishes local, external, dangling, ambiguous, and invalid routes, displays Back, Home, and Close sinks, marks cycles, orphans, invalid documents, and duplicate ids, and can open each represented menu for editing.

The editor keeps the active document in `#/workspace/<workspace UUID>/document/<document UUID>`, and the Library can copy that local deep link. The one-way fallback uses `#/import/menu/<payload>`, where the payload is unpadded base64url of a gzip-compressed UTF-8 version 1 envelope containing `kind: "menu"`, `runtimeId`, and `json`; the fragment is decoded locally, validated, previewed, and added as a new document only after confirmation, never auto-applied or used to overwrite an existing document. `/holoui edit` builds this fallback from the exact source retained by `ConfigManager`; payloads over 48,000 URL characters are rejected with a file-export instruction.

## Round-trip editor sessions

A live link has this fragment form:

```
#/sync/<sessionId>/<editorToken>?relay=<unpadded-base64url-relay-endpoint>
```

The fragment prevents ordinary HTTP access logs and referrer requests from receiving the capabilities. The editor token authorizes browser reads and explicit publications; the distinct server token is stored only in `plugins/holoui/editor-sync-sessions.json` and is used by HoloUi for outbound polling, acknowledgement, and revocation. Treat a copied live link as a bearer capability. HoloUi never accepts an inbound web connection.

Session creation posts exactly `{ "protocol": 1, "expiresInSeconds": …, "snapshot": … }` to `<editorSyncEndpoint>/sessions`. `editorSyncCreateToken`, when configured, is sent only as that POST's bearer credential. The default official endpoint requires an operator-issued create token and no static secret is shipped in HoloUi, so the default empty setting safely produces the explicit one-way fallback unless a configured relay permits anonymous admission. Relay availability is not guaranteed by the plugin.

The immutable version-1 project shape is:

```json
{
  "format": "holoui-sync-project",
  "version": 1,
  "kind": "menu",
  "subjectId": "shops/main",
  "menus": [{"id": "shops/main", "json": "{...}"}],
  "images": [{"path": "icons/shop.png", "data": "data:image/png;base64,..."}],
  "constraints": {
    "subjectId": "shops/main",
    "menuIds": ["shops/main"],
    "imagePaths": ["icons/shop.png"],
    "newImagePrefix": "sync/menus/shops/main/",
    "allowDeletes": false
  },
  "warnings": [],
  "baseRevision": "sha256:<64 lowercase hexadecimal characters>"
}
```

`kind: "board"` additionally carries `board`, the exact strict `BoardDefinition` JSON, and `constraints.newMenuPrefix`. Its initial menu set is the root plus every loaded menu recursively reached by a typed `navigate` action in absent, `push`, or `replace` mode, or by a player-source canonical `holoui`, `holo`, `hui`, `holou`, or `hu open` command. Traversal is cycle-safe and does not interpret console commands. The board id and UUID, menu-session subject id, original constraints, schema version, and editor-visible board revision are immutable; the server owns the next persisted board revision.

For a menu session, only its original menu can change. Existing captured images can change and new images are confined to `sync/menus/<menuId>/`. A board session can change every captured menu or image and create menus only under the root menu's parent prefix and images only under `sync/<boardId>/`. Sync version 1 never deletes a menu, board, or image and never overwrites a pre-existing resource outside the captured base. Every resource in the current session base, including additions from an earlier publication, must remain in later publications. Captured resources may become unreferenced and remain; a new unreferenced resource is rejected.

The revision is SHA-256 of UTF-8 canonical JSON after removing only the root `baseRevision`. Object keys are lexicographically ordered, array order is retained, strings use ordinary JSON escaping with literal Unicode, and finite numbers are canonicalized by IEEE-754 numeric value to the shared ECMAScript-style representation. The incoming publication request names the old optimistic server base; the edited snapshot has its own self-hash and may differ. After apply or conflict, HoloUi rebuilds the actual current project and returns that fresh snapshot and revision.

Version 1 rejects projects beyond all applicable limits: 256 menus, 2 MiB UTF-8 per menu, 512 images, 512 KiB decoded bytes per image, 256 warnings of at most 512 characters, JSON depth 64, 200,000 nodes, 2,000,000 characters per string, and the configured 1–32 MiB canonical project cap. Images must be magic-validated PNG, JPEG, GIF, WebP, or BMP and decode to at most 64×64 and 4,096 pixels. Unique stored assets may total at most 262,144 decoded pixels; every repeated static-image use and every animated frame occurrence also counts toward independent runtime totals of 262,144 pixels and 4,096 rows. Missing image references are rejected. Board revisions must be integers no greater than JavaScript's safe maximum `9,007,199,254,740,991`.

The server stores at most 32 active sessions and 64 MiB of canonical base plus pending snapshots; its store file is capped at 80 MiB. Outbound relay exchanges, including stalled or trickling response bodies, have a 20-second total deadline and their response bytes are capped before parsing. A publication is applied only after the exact base revision, identity, constraints, paths, formats, and runtime parsers validate. Menus, images, and the optional board are staged as one journaled transaction under the same persistence coordinator used by in-game menu/board writers and file watchers. `applied` is acknowledged only after disk commit, menu publication, board reload, and rebuilding the actual server snapshot; acknowledgement failures are retried idempotently. A conflict returns the current server snapshot, while rejected data makes no server change.

## Synchronization contract

- Any change to menu JSON, schema, parsing, component or icon data, defaults, validation, geometry, rendering offsets, hitboxes, highlighting, or movement updates both projects in the same workstream, wherever both represent that behavior.
- The plugin runtime is the behavioral authority. The editor exports runtime-compatible JSON and previews the runtime result. Editor-only simulation is not a substitute for runtime support.
- Tests are updated and run on both sides. Visual and interactive behavior is additionally verified in a browser (editor) and on an isolated Minecraft server (plugin).
- Neither side is released with a known contract mismatch. Unavoidable one-sided limitations are stated explicitly.

The Java parser defines the format. `art.arcane.holoui.config.*` is the authority for menus and `PreviewDocumentParser` for previews; a key that does not exist in Java does not exist.

## Schema files are documentation only

`schema/holoui.schema.json` and `schema/holoui-preview.schema.json` are hand-maintained JSON Schema 2020-12 documents. No Gradle task generates them, and nothing on either side reads them at runtime. `HoloUiSchemaContractTest` structurally pins selected menu-schema fields, but it is not a general JSON Schema validation pass and does not prove full parser equivalence. The preview schema remains documentation-only.

`holoui.schema.json` recognizes the current menu and icon keys, but it is not an exact executable mirror of Gson binding or runtime coercion. A recent synchronization pass corrected these key-level differences:

| Key | Correction |
|---|---|
| `customModelValue` | Was documented as `customModelData`; the runtime key comes from the `ItemIconData` record component. |
| `closeOnTeleport` | Added as a menu-level boolean, default `false`, which `MenuDefinitionData` parses alongside `closeOnDeath`. |
| `customItem` | Added to the icon `type` enum with its `provider`, `item` and `count` keys from `CustomItemIconData`. |
| `blockIcon` | Added the required namespaced Bukkit block material rendered as a packet-only block display with optional display style. |
| `animatedTextImage.source` | Was documented as `path`; the `AnimatedImageData` record names it `source`. `speed` also carried a copy-pasted description. |
| toggle `trueActions` / `falseActions` / `trueIcon` / `falseIcon` | Descriptions were inverted. `ToggleComponent.onClick()` runs `falseActions` when the state is true and `trueActions` when it is false: the lists name the state being entered. `falseIcon` also carried a copy-pasted description reading "in the true state". |
| `commandAction.source` | No longer required; an omitted source runs the command as the clicking player. |
| `soundAction.source` / `volume` / `pitch` | Descriptions now name the defaults an omitted key takes: `master`, `1.0` and `1.0`. |
| `soundAction.sound` | Description now states that an unknown key is logged when the component resolves its actions and the action is dropped, rather than failing the file. |
| `messageAction` | Added as typed MiniMessage sent to the clicking player; the editor warns that click and insertion events are stripped by the runtime. |
| `teleportAction` | Added the required namespaced world key and finite `x`, `y`, `z`, `yaw`, and `pitch` fields used by the Folia-safe asynchronous teleport action. |
| `connectAction` | Added the validated proxy server name used by the fixed BungeeCord `Connect` plugin message. |
| `navigationAction` | Added the native `push`, `replace`, `back`, `home`, and `close` page-stack modes and target requirement. |
| action `trigger` | Added the optional nullable `any`, `left_click`, `right_click`, `shift_left_click`, and `shift_right_click` binding shared by every action type. Omission and null resolve to `any`. |
| icon `style` | Added the validated display-style object for display-backed icons: billboard, text metadata, brightness, render range and culling, glow, entity shadow, and non-uniform scale. It is optional and also accepts explicit `null`; raw entity icons reject it. |
| `entityIcon` | Added a validated packet-only spawnable living entity id with positive `width` and `height` no larger than 64; entity icons deliberately reject display `style`. |

The contract test currently pins `closeOnTeleport`, integer item and custom-item counts, non-blank image/item/custom-item strings, non-empty animation sources, optional integer animation speed with no minimum, integer `customModelValue` with no minimum, the block icon discriminator and namespaced material key, the entity icon discriminator and dimension bounds, the action-trigger enum/default/nullable shape, and the icon-style reference, enum values, bounds, paired brightness fields, and nullable/optional shape. Broader parser behavior still differs where VolmLib's Gson accepts a single object for collection-typed fields that the schema describes as arrays.

Two icon spellings the schema still does not list are deliberate. `itemStack` is API-only — `MenuIconType` declares it with a null payload class, so it is unreachable from JSON. `fontImage` was removed from `MenuIconType` entirely.

Because the structural test covers selected fields rather than the full document, unpinned schema details can still drift. `03 - Menu File Format.md`, `04 - Components & Hitboxes.md`, `05 - Icons.md`, and `09 - Container Previews.md` describe runtime behavior and take precedence over the schema files.

The editor exposes the complete icon-style object in the icon inspector and validates the same enums, ARGB format, paired light values, and numeric ranges as the runtime. Its flat canvas applies non-uniform X/Y geometry, background, opacity, alignment, and approximate shadows and glow. Billboard rotation, see-through depth, brightness, view range, culling, Z scale, exact entity shadows, and client text wrapping are display-entity or 3D client behaviours and are not simulated by the flat preview.

The entity-icon inspector provides a searchable picker limited to the current spawnable living registry ids plus explicit width and height fields. The canvas and 3D preview draw a labelled silhouette with the exact authored footprint and feet anchor; this represents click geometry and orientation, not the Minecraft client's model, animations, variants, equipment, or age metadata.

The block-icon inspector filters the material catalog to block-like ids and retains manual entry for server versions newer than the shipped catalog. The canvas and 3D preview use the catalog sprite when available and the runtime's `0.75`-block automatic geometry; they preview the default block state only.

Every action row exposes its own click-trigger selector, and action type conversion preserves that binding. Defaults and presets start at `any`; imports preserve exact bindings and validation rejects unknown values. The 3D preview maps left/right pointer buttons and Shift to the four exact interactions, picks the nearest event-time hitbox, filters the ordered action chain with runtime matching rules, applies matching-navigation termination to toggles, and records the exact interaction in the action log. Block obstruction and a board competing with a personal menu are not simulated in the editor-only stage.

## Artifacts that cross the boundary

The repositories share selected byte-identical test fixtures, each replayed by a named test on both sides.

| Artifact | Plugin path | Editor path | Enforced by |
|---|---|---|---|
| Expression vectors | `src/test/resources/expr_test_vectors.json` | `test/fixtures/expr_test_vectors.json` | `ExprVectorsTest` (Java) and `preview_expr_vectors_test.dart` (Dart); both also enforce a minimum vector count. |
| Preview variable catalog | `src/test/resources/preview-variables.json` | `web/assets/catalog/preview-variables.json` | `VariableCatalogSyncTest` pins the plugin copy against `PreviewStateAdapters.catalog()`. |
| Preview goldens | `src/test/resources/golden/*.json`, emitted by `GoldenSerializer` | `test/fixtures/golden/` (`chest_27`, `furnace_smelting`, `locked`) | `preview_card_scene_test.dart` replays them against the editor's renderer. |
| Selected shipped preview documents | `src/main/resources/previews/` (`chest`, `furnace`, `locked`) | `test/fixtures/previews/` (`chest`, `furnace`, `locked`) | The three listed documents are copied byte for byte. |
| Editor-sync canonical project | `src/test/resources/editor-sync-canonical-v1.json` | `test/fixtures/editor-sync-canonical-v1.json` | Java and Dart canonicalizers both require revision `sha256:7e322c580c650ebd93ab4e19dc4b550bbd3f11436d694b38b96143996f8727d0`, including numeric-value and Unicode edge cases. |

The listed editor copies are compared byte for byte. The other ten shipped preview documents do not currently have editor-side byte-for-byte copies. Do not hand-edit a shared fixture; re-copy it from the plugin.

The editor also generates `preview-lang-en.json` from this repository's `HoloMessages.java` via its `tool/extract_preview_lang.dart`, which must be re-run when `holoui.preview.*` keys move. See `10 - Localization.md`.

## Item catalog

`/holoui items export` writes `plugins/holoui/custom-items.json` through `CustomItemCatalogWriter`. The document is version 1 and each entry carries `provider`, `id`, `name`, and `material`; ids are probed by actually resolving them, discarded on failure, sorted for deterministic output, and capped at 10000 per provider. The user imports the file into the editor through its settings dialog.

The catalog is optional. The editor uses it for id autocomplete, an approximate sprite, and a hint when a referenced id is not in the imported export. Without it, custom-item icons still export correctly. The editor also boots with a small bundled sample at `web/assets/catalog/custom-items.json`, loaded from a relative URL. See `08 - Custom Items & Item Providers.md`.

## Deployment

The editor deploys to Firebase Hosting from `.github/workflows/firebase-hosting.yml` on push to `master`. The job runs `dart analyze` and `dart test`, builds with `HUI_VERSION` and `HUI_COMMIT` defines, deploys, then re-fetches `main.client.dart.js` from the live host and compares its SHA-256 against the locally built bundle, failing the run on a mismatch so a stale deploy cannot pass silently.

`builder_static.zip` is a local packaging output in the editor working tree. It is gitignored and referenced by nothing tracked. It is not a release path; distribution is Firebase Hosting plus the `/holoui builder` link.

## Builds

- Plugin, from this repository: `./gradlew build` (`./gradlew shadowJar` for the shaded artifact).
- Editor, from `../HUI-Web-Editor`: `dart run jaspr_cli:jaspr build` after `dart pub get`, static output in `build/jaspr/`. It requires an `arcane_jaspr` checkout or symlink at `.deps/arcane_jaspr`.

## Checklist for changing the menu format

1. Change the plugin first. The parser is the authority.
2. Update `schema/holoui.schema.json` and `schema/holoui-preview.schema.json` by hand.
3. Update the affected pages: `03 - Menu File Format.md`, `04 - Components & Hitboxes.md`, `05 - Icons.md`, `06 - Actions.md`, `09 - Container Previews.md`, `08 - Custom Items & Item Providers.md`.
4. Update plugin tests and run `./gradlew test`. Golden snapshots under `src/test/resources/golden/` are not re-derivable — never invent a regenerator or hand-edit them to "make tests pass"; a golden failure means the engine or document changed and must be fixed or the intentional change reviewed carefully. Update `src/test/resources/preview-variables.json` if the variable catalog changed, and extend `src/test/resources/expr_test_vectors.json` if expression semantics changed.
5. Mirror the model and codec in the editor: `lib/model/` (`hui_menu.dart`, `hui_component.dart`, `hui_icons.dart`, `hui_actions.dart`, `json_codec.dart`), plus `lib/logic/validation.dart`, `lib/config/defaults.dart`, and `lib/config/field_docs.dart`.
6. Mirror geometry and rendering if affected: `lib/logic/hui_geometry.dart`, `viewport_math.dart`, `canvas_scene.dart`, `lib/components/render/`.
7. Re-copy every changed shared fixture into the editor.
8. Re-run `tool/extract_preview_lang.dart` if `holoui.preview.*` keys moved.
9. Run `dart analyze` and `dart test` in the editor.
10. Verify for real: the editor in a browser, the plugin on an isolated server. Confirm a menu exported by the editor loads and renders identically in game.
11. Do not release or deploy either side while a mismatch is known.
