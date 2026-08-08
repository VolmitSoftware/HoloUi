# Overview

HoloUi is a holographic UI framework for Paper and Folia servers. It renders in-world menus and container previews as packet-only display entities visible to a single viewer, driven by JSON files that server owners author by hand or with the companion web editor, and it exposes a public Java API for other plugins. This file maps the feature surface and indexes the documentation set.

## Feature Map

- **JSON menus** — files in `plugins/holoui/menus/` define floating menus of buttons, decorations, and toggles that open per player. See `03 - Menu File Format.md`.
- **Components and hitboxes** — three component types with viewer-relative click planes and hover highlighting. See `04 - Components & Hitboxes.md`.
- **Icons** — text, item, custom item, static image, and animated image icons rendered as text/item display entities. See `05 - Icons.md`.
- **Actions** — click-triggered command and sound actions; menu navigation runs through `/holoui` commands. See `06 - Actions.md`.
- **Expressions and placeholders** — PlaceholderAPI substitution in menu text and toggle conditions; a full expression language inside container preview documents. See `07 - Expressions & Placeholders.md`.
- **Custom items** — icons can reference items from ten third-party item plugins (ItemsAdder, Oraxen, Nexo, MMOItems, MythicMobs, EcoItems, ExecutableItems, HeadDatabase, Slimefun, CraftEngine), with a catalog export for the web editor. See `08 - Custom Items & Item Providers.md`.
- **Container previews** — looking at a chest, furnace, or other container shows a holographic content card, driven by JSON preview documents with live state variables. See `09 - Container Previews.md`.
- **Localization** — 17 bundled locales with per-key fallback and server-side overrides. See `10 - Localization.md`.
- **Public API** — other plugins build, open, and update menus through `HoloUiService`, obtained from the Bukkit `ServicesManager`. See `13 - API - Getting Started.md`.
- **Web editor** — a browser-based menu builder at `https://holoui.volmitsoftware.com`, linked in-game by `/holoui builder`. See `12 - Web Editor & Schemas.md`.

## Documentation Index

| File | Covers |
|------|--------|
| `00 - Overview.md` | This file: feature map, first steps, doc index, project layout, building |
| `01 - Installation & Configuration.md` | Install, plugin identity, data folder, every `settings.json` key, metrics |
| `02 - Commands & Permissions.md` | Every command and subcommand, argument syntax, permission nodes |
| `03 - Menu File Format.md` | Menu JSON top-level format, type discriminators, complete examples |
| `04 - Components & Hitboxes.md` | Button, decoration, toggle; hitbox geometry and click routing |
| `05 - Icons.md` | All icon types, rendering, text formatting, failure modes |
| `06 - Actions.md` | Command and sound actions, execution order and threading |
| `07 - Expressions & Placeholders.md` | Expression grammar, all functions and variables, PlaceholderAPI substitution points |
| `08 - Custom Items & Item Providers.md` | The ten item providers, `auto` resolution, and catalog export |
| `09 - Container Previews.md` | Preview trigger and scale model, preview document format, state variables, access control |
| `10 - Localization.md` | Locale selection, fallback chain, overrides, validation gates |
| `11 - Runtime Architecture.md` | Boot lifecycle, sessions, tick model, packet rendering, threading, Folia, VolmLib integration metrics |
| `12 - Web Editor & Schemas.md` | The HUI-Web-Editor tandem, schema files, cross-repo test fixtures |
| `13 - API - Getting Started.md` | Dependency setup, service acquisition, public type table, compatibility facts |
| `14 - API - Menus.md` | Menu builder, handles, click handling, Bukkit events |
| `15 - API - Placeholders.md` | The `%holoui_%` PlaceholderAPI expansion |
| `16 - API - Previews.md` | `PreviewStateProvider` registration and the preview access event |

Docs `01`-`10` cover operator and menu-author workflows, docs `11`-`12` cover runtime and maintainer concerns, and docs `13`-`16` cover the public API.

## First steps (operators)

1. Install the jar and start once so `plugins/holoui/` is created (`01 - Installation & Configuration.md`).
2. Put a menu JSON under `plugins/holoui/menus/` (`03 - Menu File Format.md`; complete examples are at the end of that file). Image assets go in `plugins/holoui/images/`.
3. Grant `holoui.command`, the subcommand node you need, and `holoui.open.<menuId>` for each menu id (`02 - Commands & Permissions.md`).
4. Open with `/holoui open <id>` or `/holoui open menu=<id>`.
5. Optional: enable container previews with `previewEnabled` and `holoui.preview` (`09 - Container Previews.md`). Optional: `/holoui builder` for the web editor (`12 - Web Editor & Schemas.md`).

Plugin developers start at `13 - API - Getting Started.md`.

## Project Layout

| Package (`art.arcane.holoui`) | Contents |
|-------------------------------|----------|
| root | `HoloUI` plugin main, the three command classes, bStats metrics |
| `api`, `api.internal` | Public API surface and its backend implementation |
| `config` | Menu definition data model, `ConfigManager`, `HuiSettings` |
| `config.components` / `config.icon` / `config.action` | JSON data classes per component, icon, and action type |
| `enums` | JSON type discriminators |
| `expr` | Expression parser, evaluator, and function library (used by preview documents) |
| `integration` | Item providers, custom-item catalog, container protection |
| `localization` | Message catalog and locale loading |
| `menu` | Sessions, runtime components, icons, actions, display-entity management |
| `menu.special.inventories` | Container previews and the preview document pipeline |
| `service` | Command help, PlaceholderAPI expansion, telemetry, integration service |
| `util.common` | Display entity, packet, particle, text, item, and settings utilities |

`schema/` holds the hand-maintained JSON schemas used by editor tooling; `src/main/resources/previews/` holds the bundled preview documents; `src/test/resources/golden/` holds non-regenerable golden snapshots.

## Building

Java 25 with `-parameters`. Independent Gradle build: `./gradlew build` runs the full gate, `./gradlew test` runs the test suite, `./gradlew shadowJar` produces the shaded plugin artifact. The `AGENTS.md` in the repo root carries the documentation-maintenance policy: any behavior, contract, or workflow change must update the matching doc in the same workstream.
