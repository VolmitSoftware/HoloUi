# API - Getting Started

`art.arcane.holoui.api` is HoloUi's public Java API. It lets another Bukkit plugin describe a
holographic display-entity menu in code, open it for one player, mutate it while it is on screen,
receive clicks, close it, observe every menu on the server through Bukkit events, and contribute
variables to container-preview documents. This document covers the dependency, the service lookup, the
compatibility contract and the full type index; the per-feature documents are listed at the end.

---

## What the package contains

| Type                                | Kind                 | Role                                                        |
|-------------------------------------|----------------------|-------------------------------------------------------------|
| `HoloUiService`                     | interface            | Entry point on the Bukkit `ServicesManager`: open, close, query |
| `HoloMenu`                          | record               | Immutable menu definition                                    |
| `HoloMenuBuilder`                   | final class          | Builds a `HoloMenu`; reached through `HoloMenu.builder()`     |
| `HoloComponent`                     | sealed interface     | One element of a menu: `Decoration` or `Button`               |
| `HoloIcon`                          | sealed interface     | What a component draws: `Text`, `Item`, `Image`, `AnimatedImage` |
| `HoloClickHandler`                  | functional interface | Button callback                                              |
| `HoloClick`                         | record               | Click payload passed to a `HoloClickHandler`                  |
| `HoloMenuHandle`                    | interface            | One open session: observe it, mutate it, close it             |
| `HoloMenuState`                     | enum                 | `PENDING`, `OPEN`, `CLOSED`, `FAILED`                         |
| `HoloCloseReason`                   | enum                 | Why a session ended; 13 constants                             |
| `HoloUiMenuOpenEvent`               | Bukkit event         | Cancellable; fires for every menu on the server               |
| `HoloUiMenuClickEvent`              | Bukkit event         | Cancellable; fires once per component hit by a click          |
| `HoloUiContainerPreviewAccessEvent` | Bukkit event         | Cancellable; last gate before a container preview is built    |
| `PreviewStateProvider`              | interface            | Contributes variables to container-preview documents           |
| `PreviewStateProviders`             | final class          | Static registry for `PreviewStateProvider`                     |

`HoloText`, in the same package, is package-private and is not API. It is the sanitiser behind every
id, markup string and image path.

Every type in the package is built from `java.*` types, `org.bukkit.*` types, and other types in
`art.arcane.holoui.api` only. `HoloUiApiContractTest#everyPublicApiTypeIsBuiltFromJavaBukkitAndItsOwnPackageOnly`
enforces that, so the package links against a plain Spigot or Paper compile classpath with no
Adventure, VolmLib or shaded types on your side.

### `art.arcane.holoui.api.internal` is not API

`ApiBackend`, `HoloUiBackend`, `HoloUiServiceImpl`, `ApiMenuHandle`, `ApiMenuTranslator`,
`ApiClickGuard`, `ApiClickOutcome`, `ApiEvents`, `ApiHandleState`, `ApiOwner` and `ApiPendingIcons`
live there. Several are `public` and therefore reachable by `Class.forName`. They carry no
compatibility promise and are named in these documents only to explain observable behaviour.
`HoloUiApiContractTest` asserts that no public API signature leaks a type from that package, so
everything a consumer needs is reachable from `HoloUiService` and `HoloMenuHandle`.

---

## Version and platform

| Fact                 | Value                                              |
|----------------------|----------------------------------------------------|
| Gradle coordinates   | `art.arcane:holoui:1.0.0-26.2`                     |
| Bukkit plugin name   | `holoui` (lowercase; `rootProject.name`)           |
| Data directory       | `plugins/holoui/`                                  |
| `plugin.yml`         | `api-version: 26.1`, `folia-supported: true`       |

HoloUi targets Paper and Folia at API version 26.1. Plain Spigot is not a supported runtime because HoloUi uses Paper APIs, including the entity/region schedulers and `BlockLockCheckEvent`; compatibility with later API versions must be verified per release. The lowercase plugin name is the exact spelling a dependency entry has to match.

---

## Depending on HoloUi

HoloUi is not published to Maven Central. Compile against the jar you run:

```gradle
dependencies {
    compileOnly(files("libs/holoui-1.0.0-26.2.jar"))
}
```

or resolve from source through JitPack, replacing `master-SNAPSHOT` with the tag you target:

```gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("com.github.VolmitSoftware:HoloUi:master-SNAPSHOT")
}
```

The scope must be `compileOnly` (`provided` in Maven). Never shade `art.arcane.holoui.api` into your
own jar: HoloUi's copy and yours would be distinct classes on distinct classloaders, and the cast of
the service fetched from the `ServicesManager` fails at runtime with a `ClassCastException` naming the
same type twice.

### Declaring the runtime dependency

Bukkit plugin (`plugin.yml`):

```yaml
softdepend: [holoui]
```

Paper plugin (`paper-plugin.yml`):

```yaml
dependencies:
  server:
    holoui:
      load: BEFORE
      required: false
      join-classpath: true
```

`join-classpath: true` is mandatory on Paper. Modern Paper plugin classloaders are isolated; without
it, `art.arcane.holoui.api.*` raises `NoClassDefFoundError` even though those classes ship
unrelocated.

`softdepend` / `required: false` is correct unless your plugin is useless without menus. The service
lookup below already handles HoloUi being absent.

### What is not on the compatibility contract

HoloUi ships as a minimized shadow jar.

- PacketEvents, Adventure and MiniMessage, Apache Commons, bStats and slimjar are relocated under
  `art.arcane.holoui.libs.*` or fetched at runtime into HoloUi's own classloader. VolmLib ships
  unrelocated under `art.arcane.volmlib.*` and is equally not API. Both package names and their
  contents move with the build.
- Classes in bundled libraries that HoloUi does not statically reference are stripped at build time. A
  reflective lookup into anything other than `art.arcane.holoui.api` can begin throwing
  `ClassNotFoundException` on any release, with no deprecation and no warning.
- Do not add an Adventure dependency for HoloUi's sake. `HoloIcon.text` takes MiniMessage markup as a
  plain `String`, so you never have to match HoloUi's Adventure version, and HoloUi's relocated copy is
  not loadable from your classloader.

### Forward compatibility

`HoloMenuState` and `HoloCloseReason` may gain constants. A `switch` *expression* over an enum is
exhaustive, so it stops compiling — and throws `IncompatibleClassChangeError` on an already-compiled
jar — the moment a constant is added. Always write a `default` arm over either enum.
`HoloMenuState.terminal()` answers "is this session over" without a switch.

`HoloComponent` and `HoloIcon` are sealed and may gain permitted subtypes; give a pattern-matching
`switch` over them a `default` arm too.

---

## Acquiring the service

HoloUi registers exactly one provider from `HoloUiServiceImpl#register()` during its `onEnable`:

```java
Bukkit.getServicesManager().register(HoloUiService.class, this, plugin, ServicePriority.Normal);
```

and unregisters it from `HoloUiServiceImpl#unregister()`, which runs from HoloUi's `onDisable` and
from its BileTools `onPreUnload` hook. There is no static accessor; the `ServicesManager` is the only
supported route.

```java
import art.arcane.holoui.api.HoloUiService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<HoloUiService> registration =
    Bukkit.getServicesManager().getRegistration(HoloUiService.class);

if (registration == null) {
    getLogger().warning("HoloUi is not installed; holographic menus are unavailable.");
    return;
}

HoloUiService holoUi = registration.getProvider();
```

A provider held across a HoloUi reload goes inert, not dangerous. `unregister()` sets an internal
`active` flag to `false` and terminates every live handle with `HoloCloseReason.HOLOUI_SHUTDOWN`. On
the stale instance:

| Call        | Result after HoloUi disables                                   |
|-------------|----------------------------------------------------------------|
| `menuIds()` | `Set.of()`                                                     |
| `isOpen`    | `false`                                                        |
| `close`     | `false`                                                        |
| `open`      | a handle already `FAILED` with `HoloCloseReason.OPEN_FAILED`   |

The stale provider returns only the inert results above. Re-resolve the service when you need it, or re-resolve from `PluginEnableEvent`.

---

## Threading

HoloUi supports Folia, where a player's world state belongs to the region thread that owns that
player. The rule: call `open` from the thread that owns the player — that player's region thread on
Folia or the main thread on Paper. Everything else on the service and on a handle is safe
from any thread.

| Call                                     | Legal from         | What runs on your thread                                                                      |
|------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------|
| `open(Plugin, Player, HoloMenu)`         | any; prefer owning | `Plugin#getName()`, `Plugin#isEnabled()`, `Player#getUniqueId()`, one `clone()` per item icon  |
| `open(Plugin, Player, String)`           | any; prefer owning | the same, minus icon translation                                                              |
| `close(Player)`                          | any                | one concurrent-map read, then a scheduler hand-off                                            |
| `isOpen(Player)`                         | any                | one concurrent-map read                                                                       |
| `menuIds()`                              | any                | `Set.copyOf` of a concurrent map's key set                                                    |
| `handle.sessionId/playerId/menuId`       | any                | final-field reads                                                                             |
| `handle.state()`                         | any                | one `AtomicReference` read                                                                    |
| `handle.setText/setItem/setIcon`         | any                | validation and one `ConcurrentHashMap` put                                                    |
| `handle.close()`                         | any                | one atomic read, then a scheduler hand-off                                                    |
| `handle.onClosed(Consumer)`              | any                | one atomic set, and the callback inline if the handle is already terminal                     |
| Your `HoloClickHandler`                  | invoked by HoloUi  | the clicking player's region thread. Never block it                                           |
| Your `onClosed` callback                 | invoked by HoloUi  | a server thread that is not fixed. Bookkeeping only                                           |
| Your `PreviewStateProvider#snapshot`     | invoked by HoloUi  | the region thread owning the preview target                                                   |

Called from the owning thread, an open resolves before `open` returns and the handle is already
`OPEN`, `CLOSED` or `FAILED`. Called from anywhere else, the handle comes back `PENDING` and the menu
appears a tick or more later. The mechanism and the explicit region hop are in
"14 - API - Menus.md".

---

## Ownership and cleanup

Each handle stores an `ApiOwner`: the owning plugin's name and a `BooleanSupplier` bound to its
`isEnabled()`. HoloUi never retains the `Plugin` instance itself beyond that supplier, and matches
ownership by plugin name, so a reload that produces a new `Plugin` instance under the same name still
matches.

`HoloUiServiceImpl#register()` installs a `PluginDisableEvent` listener. When any plugin other than
HoloUi disables:

1. `ApiClickGuard#forget(name)` clears that plugin's quarantine flag, fault counter and slow-warning
   timestamp.
2. Every registered handle whose owner name equals the disabling plugin's name is closed with
   `HoloCloseReason.OWNER_DISABLED`, through the same scheduler hand-off `handle.close()` uses. If the
   player is offline, the handle is terminated directly.

The owner is re-checked at two more points: in `open`, before the handle is registered and again
inside the scheduled open task, where a disabled owner terminates the handle with `OWNER_DISABLED`;
and in `ApiClickGuard#dispatch`, before every handler call, where a disabled owner yields
`SKIPPED_OWNER_DISABLED` and the handler is not called.

When HoloUi itself disables or reloads, `unregister()` unregisters the service and the disable
listener, flips the service inert, and terminates every remaining handle with `HOLOUI_SHUTDOWN`.

There is nothing to unregister for menus. The one registration a consumer owns is a
`PreviewStateProvider`: that registry is static, process-wide and JVM-lifetime, so it must be
unregistered on disable. See "16 - API - Previews.md".

---

## Where to go next

| Document                     | Covers                                                                                     |
|------------------------------|--------------------------------------------------------------------------------------------|
| "14 - API - Menus.md"        | Building, opening, mutating and closing a menu; clicks; the two menu events                 |
| "15 - API - Placeholders.md" | The `%holoui_…%` PlaceholderAPI keys and how placeholders behave inside a menu               |
| "16 - API - Previews.md"     | `PreviewStateProvider`, `PreviewStateProviders` and `HoloUiContainerPreviewAccessEvent`      |
