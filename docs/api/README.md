# HoloUi API

HoloUi draws holographic menus in the world in front of a player, out of packet-only display entities.
`art.arcane.holoui.api` is how another plugin drives that: describe a menu in code, open it for one
player, change what it says while it is on screen, receive clicks, and close it. The package is built
from Bukkit types, `java.*` types and its own types only — no VolmLib, no Adventure, no shaded types — so
it links against a plain Spigot or Paper compile classpath and needs nothing on your side but Bukkit.

| Document                             | Covers                                                              |
|--------------------------------------|---------------------------------------------------------------------|
| [menus.md](menus.md)                 | Building, opening, mutating and closing a menu. Start here          |
| [placeholders.md](placeholders.md)   | The `%holoui_…%` keys, and how placeholders behave inside a menu    |
| [previews.md](previews.md)           | Container-preview JSON documents: format, expression DSL, commands  |

---

## What the package contains

| Type                   | Kind                 | Role                                                                   |
|------------------------|----------------------|-------------------------------------------------------------------------|
| `HoloUiService`        | interface            | The entry point on the `ServicesManager`: open, close, query             |
| `HoloMenu`             | record               | An immutable menu definition                                            |
| `HoloMenuBuilder`      | final class          | Builds a `HoloMenu`; reached through `HoloMenu.builder()`                |
| `HoloComponent`        | sealed interface     | One element of a menu: `Decoration` or `Button`                          |
| `HoloIcon`             | sealed interface     | What a component draws: `Text`, `Item`, `Image` or `AnimatedImage`       |
| `HoloClickHandler`     | functional interface | Your callback for a button                                              |
| `HoloClick`            | record               | Who clicked what, and the handle for the menu they clicked it on         |
| `HoloMenuHandle`       | interface            | One open session: observe it, change it, close it                        |
| `HoloMenuState`        | enum                 | `PENDING`, `OPEN`, `CLOSED`, `FAILED`                                    |
| `HoloCloseReason`      | enum                 | Why a session ended — 13 constants                                       |
| `HoloUiMenuOpenEvent`  | Bukkit event         | Cancellable. Fires for every menu on the server, including HoloUi's own  |
| `HoloUiMenuClickEvent` | Bukkit event         | Cancellable. Fires once per component hit by a click                     |

`HoloComponent` and `HoloIcon` are sealed, so your code cannot add a case to either and a pattern-matching
`switch` sees the whole set. New permitted subtypes are still possible in a future release — see
[Forward compatibility](#forward-compatibility).

---

## Depending on HoloUi

HoloUi is not published to Maven Central. Either compile against the jar you are already running, which
is exact and always available:

```gradle
dependencies {
    compileOnly(files("libs/holoui-1.0.0-26.2.jar"))
}
```

or resolve it from source through JitPack, replacing `master-SNAPSHOT` with the tag you target:

```gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("com.github.VolmitSoftware:HoloUi:master-SNAPSHOT")
}
```

The scope must be `compileOnly` — `provided` in Maven. **Never shade `art.arcane.holoui.api` into your
own jar.** HoloUi's copy and yours would be different classes on different classloaders, and the cast of
the service you fetch from the `ServicesManager` would fail at runtime with a `ClassCastException` that
names the same type twice.

### Declaring the runtime dependency

HoloUi's declared plugin name is lowercase `holoui`. That exact spelling is what a dependency entry has
to match, and it is also the folder name: HoloUi's data directory is `plugins/holoui/`.

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

`join-classpath: true` is mandatory on Paper — modern plugin classloaders are isolated, and without it
you get `NoClassDefFoundError` on `art.arcane.holoui.api.*` even though the classes ship unrelocated.

`softdepend` / `required: false` is the right choice unless your plugin is useless without menus. The
service lookup below already handles HoloUi being absent.

---

## Acquiring the service

HoloUi registers exactly one provider in its `onEnable`, at `ServicePriority.Normal`, and unregisters it
on disable.

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

A provider held across a HoloUi reload goes inert rather than dangerous: `menuIds()` returns an empty
set, `isOpen` and `close` return `false`, and every `open` hands back a handle that is already `FAILED`
with `HoloCloseReason.OPEN_FAILED`. Re-resolve the service instead of caching it forever.

---

## Threading, in one table

HoloUi runs on Folia, where a player's world state belongs to the region thread that owns that player.
The full justification for every row is in [menus.md](menus.md#thread-affinity); this is the summary.

| Call                                          | Rule                                                                            |
|-----------------------------------------------|----------------------------------------------------------------------------------|
| `open(…)`, either overload                    | **Call from the player's owning thread.** Legal elsewhere, but the handle comes back `PENDING` and you must not have read world state on that thread yourself |
| `isOpen`, `menuIds`                           | Any thread. Concurrent-map reads only                                            |
| `close(Player)`                               | Any thread. One concurrent-map read, then the teardown is handed to the player's entity scheduler |
| `handle.setText/setItem/setIcon`              | Any thread. Staged atomically and applied on the player's region thread on the next tick |
| `handle.state/sessionId/playerId/menuId`      | Any thread. One atomic read, or a final-field read                              |
| `handle.close()`, `handle.onClosed(…)`        | Any thread. `close` hands the teardown to the player's entity scheduler; `onClosed` fires the callback inline on your thread if the handle is already terminal |
| Your `HoloClickHandler`                       | Invoked on the clicking player's region thread. **Never block it**               |
| Your `onClosed` callback                      | Invoked on a server thread that is not fixed. Do bookkeeping only; schedule world work onto the player's entity scheduler |

---

## What is not the contract

HoloUi ships as a minimized shadow jar. `art.arcane.holoui.api` is the only part of it you may name.

- **No third-party library is reachable under its own name.** PacketEvents, Adventure and MiniMessage,
  Apache Commons, bStats and slimjar are either relocated under `art.arcane.holoui.libs.*` or fetched at
  runtime into HoloUi's own classloader. VolmLib is the one exception: it ships unrelocated, under
  `art.arcane.volmlib.*`, and it is no more API than the rest. Nothing under `art.arcane.holoui.libs.*`
  or `art.arcane.volmlib.*` is API; both the package names and their contents move with the build.
- **Unreachable classes are stripped.** Classes in bundled libraries that HoloUi's own code does not
  statically reference are removed from the jar at build time. A reflective lookup by name into anything
  other than `art.arcane.holoui.api` can start returning `ClassNotFoundException` on any release, with no
  deprecation and no warning.
- **`art.arcane.holoui.api.internal` is not API.** The service implementation, the menu translator and
  the click guard live there and do ship in the jar, so `Class.forName` on them succeeds today. Treat
  them as absent. Everything you need is reachable from `HoloUiService` and `HoloMenuHandle`.
- **Do not add an Adventure dependency for HoloUi's sake.** `HoloIcon.text` takes MiniMessage markup as a
  plain `String` precisely so you never have to match HoloUi's Adventure version, and HoloUi's relocated
  copy is not loadable from your classloader anyway.

---

## Forward compatibility

`HoloMenuState` and `HoloCloseReason` may gain constants. A `switch` **expression** over an enum is
exhaustive, so it stops compiling — and throws `IncompatibleClassChangeError` on an already-compiled
jar — the moment a constant is added. Always write a `default` arm over either enum in third-party code.
`HoloMenuState.terminal()` answers "is this session over" without a switch at all.

`HoloComponent` and `HoloIcon` are sealed and may gain permitted subtypes for the same reason; give a
pattern-matching `switch` over them a `default` arm too.
