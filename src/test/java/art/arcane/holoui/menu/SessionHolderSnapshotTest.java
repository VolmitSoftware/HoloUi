/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui.menu;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloMenuState;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.api.internal.ApiOwner;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.enums.MenuComponentType;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.icon.MenuIcon;
import art.arcane.holoui.service.HoloUiPlaceholderExpansion;
import art.arcane.holoui.service.HoloUiTelemetry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SessionHolderSnapshotTest {
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final UUID IMPOSTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c4");

  @Test
  public void holderCapturesThePlayerIdOnceAndPublishesUnderThatCapturedId() {
    AtomicReference<UUID> identity = new AtomicReference<>(PLAYER);
    AtomicInteger idReads = new AtomicInteger();
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = new SessionHolder(player(identity, idReads, new AtomicBoolean(true)), openMenus);

    assertEquals("the holder must read the player id exactly once, at construction", 1, idReads.get());
    assertNull("constructing a holder must not publish a menu", openMenus.get(PLAYER));

    identity.set(IMPOSTOR);
    holder.openSession(menu("alpha"), null);

    assertEquals("the publish must key on the id captured at construction", "alpha", openMenus.get(PLAYER));
    assertNull("a lazily re-read player id would leak the snapshot onto another key", openMenus.get(IMPOSTOR));
    assertEquals("opening a session must not re-read the player id", 1, idReads.get());
  }

  @Test
  public void openingASessionPublishesTheOpenMenuIdIntoTheStore() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);

    holder.openSession(menu("alpha"), null);

    assertTrue(holder.hasSession());
    assertEquals("alpha", openMenus.get(PLAYER));
  }

  @Test
  public void openingOverAnOpenSessionRepublishesTheReplacingMenuId() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);

    holder.openSession(menu("alpha"), null);
    holder.openSession(menu("beta"), null);

    assertEquals("beta", openMenus.get(PLAYER));
  }

  @Test
  public void closingTheSessionClearsTheStore() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);

    holder.openSession(menu("alpha"), null);
    assertEquals("alpha", openMenus.get(PLAYER));

    assertTrue(holder.closeSession(true, HoloCloseReason.CLOSED_BY_COMMAND));

    assertFalse(holder.hasSession());
    assertEquals("alpha", holder.lastSessionId());
    assertNull("a closed menu must not stay published", openMenus.get(PLAYER));
  }

  @Test
  public void quitTeardownClearsTheStore() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);

    holder.openSession(menu("alpha"), null);
    holder.close(HoloCloseReason.QUIT);

    assertNull("tearing a holder down must not leave a stale entry behind", openMenus.get(PLAYER));
  }

  @Test
  public void offlinePlayersNeverPublishAMenu() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    AtomicBoolean online = new AtomicBoolean(false);
    SessionHolder holder = new SessionHolder(player(new AtomicReference<>(PLAYER), new AtomicInteger(), online), openMenus);

    holder.openSession(menu("alpha"), null);

    assertFalse(holder.hasSession());
    assertNull(openMenus.get(PLAYER));
  }

  @Test
  public void theExpansionAnswersFromTheSameStoreTheHolderWritesTo() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);
    HoloUiPlaceholderExpansion expansion = new HoloUiPlaceholderExpansion(openMenus, Logger.getAnonymousLogger());

    assertEquals(PlaceholderValues.FALSE, expansion.onRequest(offlinePlayer(), "menu.open"));
    assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(offlinePlayer(), "menu.id"));

    holder.openSession(menu("alpha"), null);

    assertEquals(PlaceholderValues.TRUE, expansion.onRequest(offlinePlayer(), "menu.open"));
    assertEquals("alpha", expansion.onRequest(offlinePlayer(), "menu.id"));

    holder.openSession(menu("beta"), null);

    assertEquals("beta", expansion.onRequest(offlinePlayer(), "menu.id"));

    holder.close(HoloCloseReason.CLOSED_BY_COMMAND);

    assertEquals(PlaceholderValues.FALSE, expansion.onRequest(offlinePlayer(), "menu.open"));
    assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(offlinePlayer(), "menu.id"));
  }

  @Test
  public void componentsBuiltDuringConstructionAlreadySeeTheMenuIdPublished() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);
    HoloUiPlaceholderExpansion expansion = new HoloUiPlaceholderExpansion(openMenus, Logger.getAnonymousLogger());
    AtomicReference<String> seenId = new AtomicReference<>();
    AtomicReference<String> seenOpen = new AtomicReference<>();

    holder.openSession(menu("alpha", () -> {
      seenId.set(expansion.onRequest(offlinePlayer(), "menu.id"));
      seenOpen.set(expansion.onRequest(offlinePlayer(), "menu.open"));
      return null;
    }), null);

    assertEquals("a toggle condition or icon built in the session constructor must resolve the menu id",
        "alpha", seenId.get());
    assertEquals("a toggle built in the session constructor must see the menu as open",
        PlaceholderValues.TRUE, seenOpen.get());
  }

  @Test
  public void aComponentThatThrowsDuringConstructionLeavesNoMenuIdPublished() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);

    try {
      holder.openSession(menu("alpha", () -> {
        throw new IllegalStateException("component blew up");
      }), null);
      fail("a component that throws must not be swallowed by the holder");
    } catch (IllegalStateException expected) {
      assertEquals("component blew up", expected.getMessage());
    }

    assertFalse(holder.hasSession());
    assertNull("a failed open must not leak a published menu id", openMenus.get(PLAYER));
  }

  @Test
  public void aComponentThatThrowsDuringOpenRollsBackTheReplacement() {
    HoloUiTelemetry.clear();
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    SessionHolder holder = holder(openMenus);
    AtomicInteger closeCalls = new AtomicInteger();
    AtomicReference<HoloCloseReason> replacedReason = new AtomicReference<>();
    AtomicReference<HoloCloseReason> failedReason = new AtomicReference<>();
    ApiMenuHandle replaced = handle("alpha", replacedReason);
    ApiMenuHandle failed = handle("beta", failedReason);

    try {
      holder.openSession(menu("alpha"), replaced);
      assertEquals(1, HoloUiTelemetry.menusOpen());

      try {
        holder.openSession(failingOpenMenu("beta", closeCalls), failed);
        fail("an open failure must escape the holder");
      } catch (IllegalStateException expected) {
        assertEquals("component open failed", expected.getMessage());
      }

      assertFalse(holder.hasSession());
      assertEquals("alpha", holder.lastSessionId());
      assertNull(openMenus.get(PLAYER));
      assertEquals(0, HoloUiTelemetry.menusOpen());
      assertEquals(1, closeCalls.get());
      assertEquals(HoloMenuState.CLOSED, replaced.state());
      assertEquals(HoloCloseReason.REPLACED, replacedReason.get());
      assertEquals(HoloMenuState.FAILED, failed.state());
      assertEquals(HoloCloseReason.OPEN_FAILED, failedReason.get());
    } finally {
      HoloUiTelemetry.clear();
    }
  }

  private static SessionHolder holder(PlayerSnapshotStore<String> openMenus) {
    return new SessionHolder(player(new AtomicReference<>(PLAYER), new AtomicInteger(), new AtomicBoolean(true)), openMenus);
  }

  private static MenuDefinitionData menu(String id) {
    MenuDefinitionData data = new MenuDefinitionData(new Vector(0, 0, 0), false, false, 8.0D, false, false,
        List.<MenuComponentData>of());
    data.setId(id);
    return data;
  }

  private static MenuDefinitionData menu(String id, Supplier<MenuComponent<?>> onCreate) {
    MenuComponentData component = new MenuComponentData("probe", new Vector(0, 0, 0), new ProbeComponentData(onCreate));
    MenuDefinitionData data = new MenuDefinitionData(new Vector(0, 0, 0), false, false, 8.0D, false, false,
        List.of(component));
    data.setId(id);
    return data;
  }

  private static MenuDefinitionData failingOpenMenu(String id, AtomicInteger closeCalls) {
    MenuComponentData component = new MenuComponentData("probe", new Vector(), new OpeningFailureData(closeCalls));
    MenuDefinitionData data = new MenuDefinitionData(new Vector(), false, false, 8.0D, false, false, List.of(component));
    data.setId(id);
    return data;
  }

  private static ApiMenuHandle handle(String menuId, AtomicReference<HoloCloseReason> reason) {
    ApiMenuHandle handle = new ApiMenuHandle(PLAYER, menuId, new ApiOwner("test", () -> true), Map.of(), Set.of(),
        Logger.getAnonymousLogger(), (ignored, closeReason) -> {
        }, ignored -> {
        });
    handle.onClosed(reason::set);
    return handle;
  }

  private record ProbeComponentData(Supplier<MenuComponent<?>> onCreate) implements ComponentData {
    @Override
    public MenuComponentType getType() {
      return MenuComponentType.TOGGLE;
    }

    @Override
    public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
      return onCreate.get();
    }
  }

  private record OpeningFailureData(AtomicInteger closeCalls) implements ComponentData {
    @Override
    public MenuComponentType getType() {
      return MenuComponentType.DECO;
    }

    @Override
    public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
      return new OpeningFailureComponent(session, data);
    }
  }

  private static final class OpeningFailureComponent extends MenuComponent<OpeningFailureData> {
    private OpeningFailureComponent(MenuSession session, MenuComponentData data) {
      super(session, data);
    }

    @Override
    public void open() {
      throw new IllegalStateException("component open failed");
    }

    @Override
    public void close() {
      data.closeCalls().incrementAndGet();
    }

    @Override
    protected void onTick() {
    }

    @Override
    protected MenuIcon<?> createIcon() {
      return null;
    }

    @Override
    protected void onOpen() {
    }

    @Override
    protected void onClose() {
    }
  }

  private static Player player(AtomicReference<UUID> identity, AtomicInteger idReads, AtomicBoolean online) {
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> {
            idReads.incrementAndGet();
            yield identity.get();
          }
          case "isOnline" -> online.get();
          case "getName" -> "tester";
          case "getLocation" -> new Location(null, 0, 64, 0);
          case "getEyeLocation" -> new Location(null, 0, 65.62D, 0);
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Player[" + identity.get() + "]";
          default -> throw new UnsupportedOperationException("session holder touched " + method.getName());
        });
  }

  private static OfflinePlayer offlinePlayer() {
    return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
        new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> PLAYER;
          case "hashCode" -> PLAYER.hashCode();
          case "equals" -> proxy == args[0];
          case "toString" -> "OfflinePlayer[" + PLAYER + "]";
          default -> throw new UnsupportedOperationException("placeholder resolution touched " + method.getName());
        });
  }
}
