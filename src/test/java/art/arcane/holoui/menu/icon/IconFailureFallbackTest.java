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
package art.arcane.holoui.menu.icon;

import art.arcane.holoui.config.icon.AnimatedImageData;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.config.icon.TextImageIconData;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuSessionOptions;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IconFailureFallbackTest {

  private static Location anchor() {
    return new Location(null, 0D, 0D, 0D);
  }

  @Test
  public void textImageIconWithoutAUsablePathFailsAsAMenuIconException() {
    MenuSession session = session();

    assertThrows(MenuIconException.class, () -> new TextImageMenuIcon(session, anchor(), new TextImageIconData(null, null)));
    assertThrows(MenuIconException.class, () -> new TextImageMenuIcon(session, anchor(), new TextImageIconData("   ", null)));
  }

  @Test
  public void animatedIconWithoutUsableFramesFailsAsAMenuIconException() {
    MenuSession session = session();

    assertThrows(MenuIconException.class, () -> new AnimatedTextImageMenuIcon(session, anchor(), new AnimatedImageData(null, 2, null)));
    assertThrows(MenuIconException.class, () -> new AnimatedTextImageMenuIcon(session, anchor(), new AnimatedImageData(List.of(), 2, null)));
    assertThrows(MenuIconException.class, () -> new AnimatedTextImageMenuIcon(session, anchor(), new AnimatedImageData(Arrays.asList("frame0.png", null), 2, null)));
  }

  @Test
  public void itemIconWithoutAResolvedMaterialFailsAsAMenuIconException() {
    assertThrows(MenuIconException.class, () -> new ItemIconData(null, 1, 0, null).requireMaterial());
  }

  @Test
  public void unknownAndBadlyCasedItemIdsStillParseSoTheMenuFileSurvives() {
    MenuIconData unknown = BukkitJson.GSON.fromJson("{\"type\":\"item\",\"item\":\"minecraft:not_a_real_item\"}", MenuIconData.class);
    MenuIconData badCase = BukkitJson.GSON.fromJson("{\"type\":\"item\",\"item\":\"DIAMOND_SWORD\"}", MenuIconData.class);

    assertTrue(unknown instanceof ItemIconData);
    assertTrue(badCase instanceof ItemIconData);
    assertNull(((ItemIconData) unknown).materialType());
    assertNull(((ItemIconData) badCase).materialType());
    assertThrows(MenuIconException.class, ((ItemIconData) unknown)::requireMaterial);
  }

  @Test
  public void missingIconKeepsItsEightRowCheckerboard() throws MenuIconException {
    assertEquals(8, TextImageMenuIcon.MISSING.size());
    TextImageMenuIcon.MISSING.forEach(row -> assertEquals(8, TextUtils.content(row).length()));
    TextImageMenuIcon icon = new TextImageMenuIcon(session(), anchor());
    CollisionPlane plane = icon.createBoundingBox(anchor());
    assertEquals(8F * MenuIcon.NAMETAG_SIZE, plane.getHeight(), 0F);
  }

  private static MenuSession session() {
    MenuDefinitionData data = new MenuDefinitionData(
        new Vector(),
        false,
        false,
        8D,
        false,
        false,
        List.<MenuComponentData>of()
    );
    data.setId("icon-test");
    Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getLocation" -> anchor();
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Player[icon-test]";
          default -> throw new UnsupportedOperationException(method.getName());
        });
    return new MenuSession(data, player, MenuSessionOptions.personal(data, player, null));
  }
}
