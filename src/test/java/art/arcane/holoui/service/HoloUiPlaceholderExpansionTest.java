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
package art.arcane.holoui.service;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HoloUiPlaceholderExpansionTest {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID BYSTANDER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Test
  public void menuOpen_followsThePublishedSessionForThatPlayerOnly() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    PlaceholderKeyRegistry registry = HoloUiPlaceholderExpansion.registry(openMenus);

    assertEquals(PlaceholderValues.FALSE, registry.resolve(VIEWER, "menu.open"));

    openMenus.publish(VIEWER, "shop");

    assertEquals(PlaceholderValues.TRUE, registry.resolve(VIEWER, "menu.open"));
    assertEquals(PlaceholderValues.FALSE, registry.resolve(BYSTANDER, "menu.open"));

    openMenus.publish(VIEWER, null);

    assertEquals(PlaceholderValues.FALSE, registry.resolve(VIEWER, "menu.open"));
  }

  @Test
  public void menuId_reportsTheOpenMenuAndFallsBackToUnavailable() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    PlaceholderKeyRegistry registry = HoloUiPlaceholderExpansion.registry(openMenus);

    assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(VIEWER, "menu.id"));

    openMenus.publish(VIEWER, "warps");

    assertEquals("warps", registry.resolve(VIEWER, "menu.id"));
    assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(BYSTANDER, "menu.id"));

    openMenus.publish(VIEWER, "shop");

    assertEquals("shop", registry.resolve(VIEWER, "menu.id"));
  }

  @Test
  public void menuId_neverEmitsAPercentThatWouldOpenANewPlaceholder() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    PlaceholderKeyRegistry registry = HoloUiPlaceholderExpansion.registry(openMenus);

    openMenus.publish(VIEWER, "menu%vault_eco_balance%");

    assertEquals("menuvault_eco_balance", registry.resolve(VIEWER, "menu.id"));
  }

  @Test
  public void anUnknownPlayerAndAnUnknownPathAreDifferentAnswers() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    PlaceholderKeyRegistry registry = HoloUiPlaceholderExpansion.registry(openMenus);

    assertNull(registry.resolve(VIEWER, "menu.identifier"));
    assertNull(registry.resolve(VIEWER, "menu_open"));
    assertNull(registry.resolve(VIEWER, "session.open"));
    assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(null, "menu.id"));
    assertEquals(PlaceholderValues.FALSE, registry.resolve(null, "menu.open"));
  }

  @Test
  public void expansionPublishesExactlyThreeKeysAndPapiSafeMetadata() {
    PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();
    VolmitPlaceholderExpansion expansion = new HoloUiPlaceholderExpansion(openMenus, Logger.getAnonymousLogger());

    assertEquals("holoui", expansion.getIdentifier());
    assertEquals("holoui", expansion.getRequiredPlugin());
    assertTrue(expansion.persist());
    assertEquals(List.of("available", "menu.id", "menu.open"), expansion.getPlaceholders());
    assertEquals(PlaceholderValues.TRUE, expansion.onRequest(player(), "available"));

    openMenus.publish(VIEWER, "shop");

    assertEquals("shop", expansion.onRequest(player(), "MENU.ID"));
    assertEquals(PlaceholderValues.TRUE, expansion.onRequest(player(), "menu.open"));
  }

  private static OfflinePlayer player() {
    return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
        new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> VIEWER;
          case "hashCode" -> VIEWER.hashCode();
          case "equals" -> proxy == args[0];
          case "toString" -> "OfflinePlayer[" + VIEWER + "]";
          default -> throw new UnsupportedOperationException("placeholder resolution touched " + method.getName());
        });
  }
}
