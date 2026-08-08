# Web Editor & Schemas

HoloUi ships alongside a browser-based menu editor kept in a separate repository, and the two are two surfaces of one menu contract. This page describes what the editor is, how the repositories stay synchronized, which files actually cross the boundary, and what the shipped JSON Schema files are and are not.

## The editor

`HUI-Web-Editor/` (sibling of this repository, Dart package `holoui_editor`) is a Jaspr client-side application that authors HoloUi configurations on a calibrated canvas and exports the files the plugin loads:

- `menus/<id>.json` for `plugins/holoui/menus/`
- `images.zip`, pixel-art icon sources laid out for `plugins/holoui/images/`
- Container preview documents for `plugins/holoui/previews/`

It runs entirely in the browser: no server, no accounts, no network calls beyond its own static assets. It is deployed to `https://holoui.volmitsoftware.com`. The plugin neither ships nor hosts it. `/holoui builder` prints a link to `HuiSettings.BUILDER_URL` (default `https://holoui.volmitsoftware.com`, sanitized to a plain `http`/`https` URL before it reaches a MiniMessage `click:open_url` event); see `02 - Commands & Permissions.md`.

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
| `animatedTextImage.source` | Was documented as `path`; the `AnimatedImageData` record names it `source`. `speed` also carried a copy-pasted description. |
| toggle `trueActions` / `falseActions` / `trueIcon` / `falseIcon` | Descriptions were inverted. `ToggleComponent.onClick()` runs `falseActions` when the state is true and `trueActions` when it is false: the lists name the state being entered. `falseIcon` also carried a copy-pasted description reading "in the true state". |
| `commandAction.source` | No longer required; an omitted source runs the command as the clicking player. |
| `soundAction.source` / `volume` / `pitch` | Descriptions now name the defaults an omitted key takes: `master`, `1.0` and `1.0`. |
| `soundAction.sound` | Description now states that an unknown key is logged when the component resolves its actions and the action is dropped, rather than failing the file. |

The contract test currently pins `closeOnTeleport`, integer item and custom-item counts, non-blank image/item/custom-item strings, non-empty animation sources, optional integer animation speed with no minimum, and integer `customModelValue` with no minimum. Broader parser behavior still differs where VolmLib's Gson accepts a single object for collection-typed fields that the schema describes as arrays.

Two icon spellings the schema still does not list are deliberate. `itemStack` is API-only — `MenuIconType` declares it with a null payload class, so it is unreachable from JSON. `fontImage` was removed from `MenuIconType` entirely.

Because the structural test covers selected fields rather than the full document, unpinned schema details can still drift. `03 - Menu File Format.md`, `04 - Components & Hitboxes.md`, `05 - Icons.md`, and `09 - Container Previews.md` describe runtime behavior and take precedence over the schema files.

## Artifacts that cross the boundary

The repositories share selected byte-identical test fixtures, each replayed by a named test on both sides.

| Artifact | Plugin path | Editor path | Enforced by |
|---|---|---|---|
| Expression vectors | `src/test/resources/expr_test_vectors.json` | `test/fixtures/expr_test_vectors.json` | `ExprVectorsTest` (Java) and `preview_expr_vectors_test.dart` (Dart); both also enforce a minimum vector count. |
| Preview variable catalog | `src/test/resources/preview-variables.json` | `web/assets/catalog/preview-variables.json` | `VariableCatalogSyncTest` pins the plugin copy against `PreviewStateAdapters.catalog()`. |
| Preview goldens | `src/test/resources/golden/*.json`, emitted by `GoldenSerializer` | `test/fixtures/golden/` (`chest_27`, `furnace_smelting`, `locked`) | `preview_card_scene_test.dart` replays them against the editor's renderer. |
| Selected shipped preview documents | `src/main/resources/previews/` (`chest`, `furnace`, `locked`) | `test/fixtures/previews/` (`chest`, `furnace`, `locked`) | The three listed documents are copied byte for byte. |

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
