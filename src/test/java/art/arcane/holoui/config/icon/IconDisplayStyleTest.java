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
package art.arcane.holoui.config.icon;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuSessionOptions;
import art.arcane.holoui.menu.icon.MenuIcon;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IconDisplayStyleTest {

  @Test
  public void omittedStyleAndEmptyStyleUseRuntimeDefaults() {
    TextIconData omitted = (TextIconData) icon("{\"type\":\"text\",\"text\":\"A\"}");
    TextIconData empty = (TextIconData) icon("{\"type\":\"text\",\"text\":\"A\",\"style\":{}}");

    assertNull(omitted.style());
    assertEquals(IconDisplayStyle.defaults(), empty.style());
    assertEquals(-1, empty.style().packedBrightness());
    assertEquals(0, empty.style().textFlags());
  }

  @Test
  public void completeStyleDecodesAllMetadataAndColorsRoundTrip() {
    TextIconData data = (TextIconData) icon("{\"type\":\"text\",\"text\":\"A\",\"style\":{"
        + "\"billboard\":\"center\",\"shadow\":true,\"seeThrough\":true,\"textAlignment\":\"right\","
        + "\"backgroundArgb\":\"#80445566\",\"textOpacity\":128,\"lineWidth\":120,"
        + "\"blockLight\":7,\"skyLight\":12,\"viewRange\":2.5,\"shadowRadius\":0.4,"
        + "\"shadowStrength\":0.7,\"cullingWidth\":4,\"cullingHeight\":3,"
        + "\"glowColor\":\"#FFFF00FF\",\"scaleX\":2,\"scaleY\":0.5,\"scaleZ\":1.25}}");
    IconDisplayStyle style = data.style();

    assertEquals(IconBillboard.CENTER, style.billboard());
    assertEquals((byte) 0x13, style.textFlags());
    assertEquals((7 << 4) | (12 << 20), style.packedBrightness());
    assertEquals(0x80445566, style.backgroundArgb().argb());
    assertEquals("#80445566", style.backgroundArgb().hex());
    assertEquals(0xFFFF00FF, style.glowColorOverride());
    assertEquals((byte) 0x40, style.entityFlags());
    assertEquals(2F, style.scaleX(), 0F);
    assertEquals(0.5F, style.scaleY(), 0F);

    JsonObject encoded = BukkitJson.GSON.toJsonTree(data, MenuIconData.class).getAsJsonObject();
    JsonObject encodedStyle = encoded.getAsJsonObject("style");
    assertEquals("center", encodedStyle.get("billboard").getAsString());
    assertEquals("right", encodedStyle.get("textAlignment").getAsString());
    assertEquals("#80445566", encodedStyle.get("backgroundArgb").getAsString());
    assertEquals("#FFFF00FF", encodedStyle.get("glowColor").getAsString());
  }

  @Test
  public void invalidStyleValuesRejectTheMenuDocument() {
    assertThrows(RuntimeException.class, () -> icon(
        "{\"type\":\"text\",\"text\":\"A\",\"style\":{\"scaleX\":0}}"));
    assertThrows(RuntimeException.class, () -> icon(
        "{\"type\":\"text\",\"text\":\"A\",\"style\":{\"blockLight\":15}}"));
    assertThrows(RuntimeException.class, () -> icon(
        "{\"type\":\"text\",\"text\":\"A\",\"style\":{\"backgroundArgb\":\"#123456\"}}"));
    assertThrows(RuntimeException.class, () -> icon(
        "{\"type\":\"text\",\"text\":\"A\",\"style\":{\"billboard\":\"camera\"}}"));
    assertThrows(RuntimeException.class, () -> icon(
        "{\"type\":\"text\",\"text\":\"A\",\"style\":{\"textAlignment\":\"justify\"}}"));
  }

  @Test
  public void styleIsAppliedToTextDisplayMetadataAndScale() throws MenuIconException {
    IconDisplayStyle style = new IconDisplayStyle(
        IconBillboard.VERTICAL, true, true, IconTextAlignment.LEFT,
        IconArgbColor.parse("#40112233"), 90, 80, 6, 13, 3F,
        0.5F, 0.75F, 4F, 5F, IconArgbColor.parse("#FFAA00CC"),
        2F, 0.5F, 1.5F
    );
    StyledProbe icon = new StyledProbe(session(), new TextIconData("A", style, null));
    DisplayEntity entity = icon.render();

    assertEquals((byte) 1, entity.billboard());
    assertEquals((byte) 0x0B, entity.textFlags());
    assertEquals((byte) 90, entity.textOpacity());
    assertEquals(80, entity.lineWidth());
    assertEquals((6 << 4) | (13 << 20), entity.brightness());
    assertEquals(3F, entity.viewRange(), 0F);
    assertEquals(0.5F, entity.shadowRadius(), 0F);
    assertEquals(0.75F, entity.shadowStrength(), 0F);
    assertEquals(4F, entity.width(), 0F);
    assertEquals(5F, entity.height(), 0F);
    assertEquals(0xFFAA00CC, entity.glowColorOverride());
    assertTrue((entity.entityFlags() & 0x40) != 0);
    assertEquals(2F, entity.scale().getX(), 0F);
    assertEquals(0.5F, entity.scale().getY(), 0F);
    assertEquals(1.5F, entity.scale().getZ(), 0F);
  }

  private static MenuIconData icon(String json) {
    return BukkitJson.GSON.fromJson(json, MenuIconData.class);
  }

  private static MenuSession session() {
    MenuDefinitionData data = new MenuDefinitionData(
        new Vector(), false, false, 8D, false, false, List.<MenuComponentData>of()
    );
    data.setId("style-test");
    Player player = (Player) Proxy.newProxyInstance(
        Player.class.getClassLoader(),
        new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getLocation", "getEyeLocation" -> new Location(null, 0D, 0D, 0D);
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Player[style-test]";
          case "isOnline" -> true;
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
    return new MenuSession(data, player, MenuSessionOptions.personal(data, player, null));
  }

  private static final class StyledProbe extends MenuIcon<TextIconData> {
    private StyledProbe(MenuSession session, TextIconData data) throws MenuIconException {
      super(session, new Location(null, 0D, 0D, 0D), data);
    }

    private DisplayEntity render() {
      EntityType entityType = (EntityType) Proxy.newProxyInstance(
          EntityType.class.getClassLoader(),
          new Class<?>[]{EntityType.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "EntityType[style-test]";
            default -> throw new UnsupportedOperationException(method.getName());
          }
      );
      return applyTextStyle(new DisplayEntity(1, UUID.randomUUID(), entityType));
    }

    @Override
    protected List<UUID> createDisplayEntities(Location location) {
      return List.of();
    }

    @Override
    public CollisionPlane createBoundingBox(Location anchor) {
      return new CollisionPlane(anchor.toVector(), 1F, 1F);
    }
  }
}
