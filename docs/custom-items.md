# Custom items

A HoloUi menu icon can draw an item that belongs to another plugin — an ItemsAdder ruby, an MMOItems
sword, a HeadDatabase head — instead of a vanilla `Material`. The item is resolved on the server, at
the moment the icon is built, and the resulting `ItemStack` is sent to the client exactly as it is,
so whatever the host plugin puts on the stack (model, name, lore, data components) is what players
see.

Nothing here needs configuration. If the host plugin is installed, HoloUi picks it up; if it is not,
the icon falls back to the missing-icon checker and logs one warning naming the provider and the id.

---

## The `customItem` icon

```json
{
  "type": "customItem",
  "provider": "itemsadder",
  "item": "myitems:ruby",
  "count": 1
}
```

| Key        | Type   | Required | Default | Meaning                                                                       |
|------------|--------|----------|---------|-------------------------------------------------------------------------------|
| `type`     | string | yes      | —       | Literally `customItem`                                                        |
| `provider` | string | no       | `auto`  | A provider id from the table below, or `auto` to try every ready provider      |
| `item`     | string | yes      | —       | The provider's own id, verbatim and case preserved                            |
| `count`    | int    | no       | `1`     | Stack size. Above 1 the bold count label is drawn, same as the vanilla item icon |

`auto` tries every active provider in the registration order of the table below and takes the first
hit. It is convenient for a single-provider server and wasteful everywhere else — name the provider
when you know it.

`item` is deliberately the same key the vanilla `item` icon uses, so the two icon types read alike.

---

## Providers

| Provider id      | Plugin name       | Id format                        | Notes                                                              |
|------------------|-------------------|----------------------------------|--------------------------------------------------------------------|
| `craftengine`    | `CraftEngine`     | `namespace:id`, bare id also works | Case sensitive. A bare id resolves in the `minecraft` namespace     |
| `itemsadder`     | `ItemsAdder`      | `namespace:id`                   | Lowercase. Items load asynchronously well after startup, so the provider reports "still loading" until they are ready |
| `oraxen`         | `Oraxen`          | bare id (the yml key)            | Case sensitive, no namespace                                       |
| `nexo`           | `Nexo`            | bare id (the yml key)            | Case sensitive, no namespace                                       |
| `mmoitems`       | `MMOItems`        | `TYPE:ID`                        | Both halves conventionally UPPERCASE. Main-thread only, see below   |
| `executableitems`| `ExecutableItems` | bare id (the config file name)   | Case sensitive. API classes ship inside the SCore plugin            |
| `ecoitems`       | `EcoItems`        | bare id, or `ecoitems:my_item`   | Case insensitive, eco lowercases internally                        |
| `slimefun`       | `Slimefun`        | bare id, `UPPER_SNAKE_CASE`      | Exact match, case sensitive (`MAGIC_WORKBENCH`)                     |
| `mythicmobs`     | `MythicMobs`      | bare internal item name          |                                                                     |
| `headdatabase`   | `HeadDatabase`    | numeric head id, e.g. `7129`     | The head database loads after startup; ids resolve once it has      |

A provider only exists when its plugin is installed and enabled. HoloUi also picks up plugins that
enable *after* it, which is the normal case for ItemsAdder and HeadDatabase.

MMOItems must be called from the main thread. Icons are built on the main or region thread already,
so normal rendering is unaffected; anything that resolves off-thread simply skips MMOItems rather
than blocking a tick.

---

## Commands

| Command                 | Permission                   | What it does                                                    |
|-------------------------|------------------------------|------------------------------------------------------------------|
| `/holoui items status`  | `holoui.command.items`       | One line per known provider: installed, active, ready, id count |
| `/holoui items export`  | `holoui.command.items.export`| Writes the catalog the web editor reads                          |

`status` counts ids by asking each ready provider to enumerate, which is the one place that cost is
paid — it is never paid while a menu is open.

`export` runs off the main thread. Providers that require the main thread are collected on a tick and
only for as long as they enumerate; everything else stays asynchronous.

---

## The catalog

`/holoui items export` writes `plugins/holoui/custom-items.json`:

```json
{
  "version": 1,
  "generated": 1730000000000,
  "providers": ["itemsadder"],
  "items": [
    { "provider": "itemsadder", "id": "myitems:ruby", "name": "Ruby", "material": "diamond" }
  ]
}
```

- `material` is the resolved stack's vanilla key, lowercase, so the editor can draw an approximate
  sprite for an item it has never seen.
- `name` is the provider's display name when it offers one, stripped of legacy colour codes,
  otherwise the id.
- Every id is probed by actually resolving it, and an id that fails to resolve is dropped. The
  catalog therefore never advertises an id that would render as the missing-icon checker.
- Ids are sorted, so two exports of an unchanged server produce the same file.
- A provider that exposes more than 10000 ids is truncated to the first 10000 with a warning in the
  console. This is HeadDatabase in practice — it ships tens of thousands of heads. Truncated ids
  still work in a menu, they are just absent from the editor's autocomplete.

The builder server serves the file at `/custom-items.json`. If the file does not exist yet the route
answers 404 and the editor degrades to a plain text field. The catalog lives next to `settings.json`
and **not** inside `builder/`, which is deleted and re-extracted on every builder update.

`/holoui builder start` exports the catalog once if it is absent, so a freshly started builder has
autocomplete without anyone knowing this page exists.

**The editor cannot verify ids offline.** It has no connection to your server and no knowledge of
your packs. With a catalog loaded it can autocomplete and tell you an id is not in it; without one it
accepts anything you type. Either way the id is only ever really checked by the server, when the menu
opens. An unknown id is a warning in the console and a checkered icon in the world, never a broken
menu.

---

## Settings

`plugins/holoui/settings.json`:

| Key                   | Type    | Default | Meaning                                                                 |
|-----------------------|---------|---------|--------------------------------------------------------------------------|
| `customItems`         | boolean | `true`  | Turns the whole integration layer off, including the commands            |
| `customItemProviders` | string  | `""`    | Comma-separated allowlist of provider ids, empty meaning every provider  |

Both are hot-reloaded and rebuild the provider registry on change. A key that is new in this version
does not appear in an existing `settings.json` until the server shuts down cleanly, so add it by hand
if you need it before then.
