# Localization

HoloUI resolves every user-facing string through a typed message catalog declared in code and overlaid by YAML locale files. One locale is active per server, selected by the `locale` key in `plugins/holoui/language.yml`; seventeen translations ship with the plugin, and server owners override individual messages in the same file. This page covers the fallback chain, message formatting, placeholder rules, hot reload behavior, and the test gate that keeps locale data complete.

## Locale selection

Selection is server-wide. HoloUI does not read a player's Minecraft client locale, and no code path resolves a message for a specific `Player`. The active locale comes from the top-level `locale` key in `plugins/holoui/language.yml`:

```yaml
locale: "de_DE"
```

If the key is absent, blank, or the file was just generated, the locale is `en_US`. `HoloLocalization.activeLocale()` reports the value currently applied.

If `locale` names a locale in the shared manifest but the matching bundled file is not on the classpath, loading throws and the reload is rejected. If `locale` names a value outside the manifest, the missing bundled resource is tolerated: no bundled overlay is added, and messages come from the English catalog plus whatever the user overlay supplies.

## Fallback chain

`HoloLocalization.loadCandidate()` assembles up to two overlays: the `messages:` section of `language.yml`, then the bundled classpath resource `/languages/<locale>.yml` (loaded only when the locale is not `en_US`). Every catalog key is seeded with its English value from code, and the overlays are applied on top. The resulting precedence per key is:

```
language.yml  >  bundled /languages/<locale>.yml  >  English string in HoloMessages.java
```

Fallback is per key, not per file. A locale file that omits a key falls through to the next level; the omission is recorded as a `MISSING_KEY` warning and does not block loading.

The English strings live in code. Each `TextKey.of(id, english)` declaration in `HoloMessages.java` carries its own English source text, and the catalog is built with English as its base locale. There is deliberately no `en_US.yml`, and the test suite asserts one is never added. When `locale` is `en_US`, only the user overlay is applied over the code defaults.

The catalog also covers the Director command framework: `HoloMessages.createCatalog()` adds `DirectorMessages.keys()` before its own, which is the `director.*` key tree seen in the locale files. `HoloLocalization.directorResolver()` bridges the catalog into Director and falls back to `DirectorTextResolver.ENGLISH` for any Director key the catalog does not declare.

## Shipped locales

Seventeen non-English files live under `src/main/resources/languages/`:

```
de_DE.yml   es_ES.yml   fi_FI.yml   fr_FR.yml
he_IL.yml   it_IT.yml   ja-JP.yml   ko_KR.yml
lt_LT.yml   nl_NL.yml   pl_PL.yml   pt_PT.yml
ru_RU.yml   tr_TR.yml   vi_VI.yml   zh_CN.yml
zh_TW.yml
```

`ja-JP.yml` uses a hyphen where every other file uses an underscore. That is the literal identifier in `VolmitLocales.NON_ENGLISH`, so `locale: "ja-JP"` is the correct spelling in `language.yml`. `VolmitLocales.minecraftCode()` normalizes the hyphen to an underscore for Minecraft-facing use.

The set is not defined by HoloUI. It comes from `VolmitLocales.nonEnglish()` in VolmLib and is shared across the Volmit plugin fleet; the directory contents must match that list exactly.

## Key structure

Keys are dot-delimited ids that map directly onto YAML nesting. `TextKey.of("holoui.message.menu.list.entry", ...)` is written as:

```yaml
messages:
  holoui:
    message:
      menu:
        list:
          entry: "Click to open {menu}."
```

Top-level namespaces in the catalog:

| Prefix | Contents |
|---|---|
| `director.help.*`, `director.runtime.*` | Director command framework labels and errors |
| `holoui.command.*` | Command and subcommand descriptions shown in help |
| `holoui.parameter.*` | Command parameter descriptions |
| `holoui.error.*` | Validation errors |
| `holoui.message.*` | Chat feedback (menus, builder link, custom items, preview documents, config reload, preview scaling) |
| `holoui.preview.state.*`, `holoui.preview.stat.*` | Container preview status and statistic lines |
| `holoui.preview.theme.title.*` | Container preview window titles |

Every key HoloUI declares is a `TextKey`. The underlying infrastructure also supports `LinesKey` and `PluralKey`, and `HoloLocalization.appendMessages` handles plural sub-keys, but HoloUI declares none.

## Formatting

Message templates use legacy `&` color codes, not MiniMessage. Two render paths exist:

| Method | Behavior | Used for |
|---|---|---|
| `legacy(key, args)` | Substitutes placeholders and runs `ChatColor.translateAlternateColorCodes('&', ...)` | Strings passed to `sender.sendMessage(...)` |
| `text(key, args)` | Substitutes placeholders with no color translation | Strings handed to `DirectorMiniMenu` |

Strings that reach `DirectorMiniMenu` are MiniMessage-escaped by that renderer before being embedded in its markup, so MiniMessage tags written into a locale file are never interpreted — they appear literally. Keys consumed through `text()` are correspondingly authored as plain text (`holoui.message.menu.list.header: "Menus"`), while keys consumed through `legacy()` carry `&` codes (`holoui.message.menu.closed: "&7[&bHoloUI&7]: &aMenu closed."`). Keep a key's existing style when translating it. `directorResolver()` additionally strips all color from its output, yielding plain text for Director help.

## Placeholders

Placeholders are named and brace-delimited: `{menu}`, `{percent}`, `{url}`, `{count}`. Names match `[A-Za-z][A-Za-z0-9_.-]*`. `{{` and `}}` are accepted by the parser as escapes and are not treated as placeholders, but the renderer does not collapse them — they render literally.

A translation must use exactly the same placeholder set as the English source. The set, not the order or the count: a placeholder may be reordered or repeated, but adding an unknown one or dropping a declared one is a hard error that rejects the reload.

Arguments are supplied at the call site through `MessageArgs` in two kinds:

| Kind | Behavior |
|---|---|
| `untrusted(name, value)` | The substituted value has all color stripped, including raw section characters. Used for player-supplied and dynamic values such as menu names. |
| `trusted(name, value)` | The substituted value is `&`-translated in legacy mode. |

Substitution is sentinel-based, so an inserted value that itself looks like a placeholder or a sentinel is never re-scanned as one.

## Overriding messages

`language.yml` lives in the Bukkit data folder at `plugins/holoui/language.yml`. If the file is missing at startup or at reload, HoloUI writes a minimal default containing only:

```yaml
locale: en_US
```

The generated file is sparse and contains no `messages:` block. Only keys the owner wants to change need to be added, in either nested or flattened dotted form; both are accepted.

```yaml
locale: "fr_FR"
messages:
  holoui.message.menu.unavailable: "&cMenu indisponible: {menu}"
  holoui:
    message:
      menu:
        closed: "&7[&bHoloUI&7]: &aMenu fermé."
```

These overrides win over the bundled `fr_FR.yml` for those keys; every other key still comes from the bundled file, then from the English defaults.

## Hot reload and validation

`ConfigManager` runs a repeating sync task every 5 ticks that calls `HoloLocalization.update()`. That method compares the file's last-modified timestamp and size through `FileWatcher` and calls `reload()` on any change. No command or server restart is required.

Reloads are atomic. `LocalizationManager` builds the new snapshot first and swaps it in only if validation passes; otherwise the previously loaded snapshot stays live. A rejected reload logs `Rejected language reload; continuing with <locale>.` at `SEVERE`, followed by up to 12 individual issues in the form `<source> [<key>]: <detail>`, then a count of any omitted remainder.

Conditions that reject a reload:

| Condition | Issue code |
|---|---|
| Key not declared by the catalog | `UNUSED_KEY` |
| Value shape differs from the declaration (for example a list where text is expected) | `SHAPE_MISMATCH` |
| Placeholder set differs from the English source | `PLACEHOLDER_MISMATCH` |
| Non-string value under `messages` | throws during load |
| File is not a regular file, or exceeds 2 MiB | throws during load |
| YAML fails to parse | throws during load |
| Bundled file whose internal `locale:` value does not match its filename | throws during load |

A key present in the catalog but absent from an overlay is a `MISSING_KEY` warning only and does not reject the reload; it falls through to the next precedence level.

## Adding a locale

The locale list is shared fleet-wide, so a new locale is added in VolmLib and mirrored into every plugin.

1. Add the identifier to `VolmitLocales.NON_ENGLISH` in VolmLib (`shared/src/main/java/art/arcane/volmlib/util/localization/VolmitLocales.java`).
2. Create `src/main/resources/languages/<locale>.yml` in HoloUI. Its `locale` declaration must match the filename; a mismatch is rejected at load.
3. Translate every key in the catalog. Bundled files are held to full coverage by test, unlike user overrides.
4. Preserve each key's placeholder set exactly, and preserve its formatting style (`&` codes versus plain text).
5. Keep the `{url}` placeholder in `holoui.message.builder.open` rather than hardcoding the web editor URL. See [12 - Web Editor & Schemas.md](12%20-%20Web%20Editor%20%26%20Schemas.md).
6. Repeat steps 2 through 5 for the other fleet plugins, since the manifest is shared.

Adding a locale file without updating `VolmitLocales` fails the manifest test; updating `VolmitLocales` without adding the file fails both the manifest and coverage tests.

## Test gate

All locale gates live in `src/test/java/art/arcane/holoui/localization/HoloLocalizationTest.java`.

| Test | Locked behavior |
|---|---|
| `everyBundledLocaleFullyCoversTheTypedCatalog` | For each locale in `VolmitLocales.nonEnglish()`, every catalog key resolves with that locale as its source. Any key added to `HoloMessages` must be translated in all 17 files. |
| `bundledResourceSetExactlyMatchesSharedManifest` | The contents of `src/main/resources/languages/` equal `VolmitLocales.nonEnglish()` mapped to `<locale>.yml` — no extra files, no missing files, no `en_US.yml`. |
| `generatesSparseLocaleSelectorWithEnglishInTheTypedCatalog` | The generated `language.yml` declares `locale: en_US` and contains no `messages` block. |
| `builderLinkTakesItsUrlFromSettingsAndNoLocaleHardcodesOne` | Every locale file contains `{url}` and no hardcoded editor hostname. |
| `appliesExternalOverrideWithNamedArguments` | A `language.yml` override beats the bundled locale file, and `activeLocale()` reports the declared locale. |
| `rejectsInvalidReloadAndRetainsLastGoodSnapshot` | A reload that drops a required placeholder returns `false` and leaves the previous snapshot serving. |
| `resolvesDirectorLabelsAndDoesNotRenderUntrustedFormatting` | Director keys resolve through the catalog and strip color; untrusted arguments cannot inject formatting. |
| `insertedArgumentsAreNeverReprocessedAsLaterSentinels` | Substituted values are not rescanned for placeholders or sentinels. |

## Source files

| Path | Role |
|---|---|
| `src/main/java/art/arcane/holoui/localization/HoloMessages.java` | Typed catalog: key declarations and English source strings |
| `src/main/java/art/arcane/holoui/localization/HoloLocalization.java` | Loading, reload, fallback, rendering |
| `src/main/resources/languages/` | Bundled translations, one file per non-English locale |

The primitives (`MessageCatalog`, `TextKey`, `LocaleOverlay`, `LocalizationManager`, `LocalizationSnapshot`, `LocalizationValidator`, `VolmitLocales`) live in VolmLib under `art.arcane.volmlib.util.localization` and are shared across the Volmit plugin fleet.

## Related pages

- [01 - Installation & Configuration.md](01%20-%20Installation%20%26%20Configuration.md) — the plugin data folder and the rest of the configuration files.
- [02 - Commands & Permissions.md](02%20-%20Commands%20%26%20Permissions.md) — the commands whose help text and errors resolve through this catalog.
- [09 - Container Previews.md](09%20-%20Container%20Previews.md) — the preview status, statistic, and title keys.
