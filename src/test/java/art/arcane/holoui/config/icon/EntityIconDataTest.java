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

import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.google.gson.JsonObject;
import org.bukkit.entity.EntityType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class EntityIconDataTest {

  @Test
  public void namespacedLivingEntityRoundTrips() throws MenuIconException {
    EntityIconData data = entity(
        "{\"type\":\"entity\",\"entity\":\"minecraft:parrot\",\"width\":0.5,\"height\":0.9}");

    assertEquals(EntityType.PARROT, data.requireEntityType());
    assertEquals(0.5F, data.resolvedWidth(), 0F);
    assertEquals(0.9F, data.resolvedHeight(), 0F);

    JsonObject encoded = BukkitJson.GSON.toJsonTree(data, MenuIconData.class).getAsJsonObject();
    assertEquals("entity", encoded.get("type").getAsString());
    assertEquals("minecraft:parrot", encoded.get("entity").getAsString());
  }

  @Test
  public void missingDimensionsUseOneBlockDefaults() {
    EntityIconData data = entity("{\"type\":\"entity\",\"entity\":\"minecraft:cow\"}");

    assertNull(data.width());
    assertNull(data.height());
    assertEquals(1F, data.resolvedWidth(), 0F);
    assertEquals(1F, data.resolvedHeight(), 0F);
  }

  @Test
  public void omittedNamespaceResolvesAgainstMinecraft() throws MenuIconException {
    EntityIconData data = entity("{\"type\":\"entity\",\"entity\":\"parrot\"}");

    assertEquals(EntityType.PARROT, data.requireEntityType());
  }

  @Test
  public void unknownAndUnsafeTypesFailAtIconResolution() {
    EntityIconData unknown = entity("{\"type\":\"entity\",\"entity\":\"minecraft:not_real\"}");
    EntityIconData player = entity("{\"type\":\"entity\",\"entity\":\"minecraft:player\"}");
    EntityIconData item = entity("{\"type\":\"entity\",\"entity\":\"minecraft:item\"}");

    assertThrows(MenuIconException.class, unknown::requireEntityType);
    assertThrows(MenuIconException.class, player::requireEntityType);
    assertThrows(MenuIconException.class, item::requireEntityType);
  }

  @Test
  public void invalidDimensionsRejectTheDocument() {
    assertThrows(RuntimeException.class, () -> entity(
        "{\"type\":\"entity\",\"entity\":\"minecraft:parrot\",\"width\":0,\"height\":1}"));
    assertThrows(RuntimeException.class, () -> entity(
        "{\"type\":\"entity\",\"entity\":\"minecraft:parrot\",\"width\":1,\"height\":65}"));
  }

  private static EntityIconData entity(String json) {
    return (EntityIconData) BukkitJson.GSON.fromJson(json, MenuIconData.class);
  }
}
