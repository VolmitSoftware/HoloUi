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
import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class BlockIconDataTest {

  @Test
  public void namespacedBlockMaterialRoundTrips() {
    BlockIconData data = block("{\"type\":\"block\",\"block\":\"minecraft:stone\"}");

    assertEquals(Material.STONE, data.blockType());
    JsonObject encoded = BukkitJson.GSON.toJsonTree(data, MenuIconData.class).getAsJsonObject();
    assertEquals("block", encoded.get("type").getAsString());
    assertEquals("minecraft:stone", encoded.get("block").getAsString());
  }

  @Test
  public void optionalStyleRoundTrips() {
    BlockIconData data = block("""
        {"type":"block","block":"minecraft:stone","style":{"scaleX":1.5,"billboard":"vertical"}}
        """);

    assertEquals(1.5F, data.style().scaleX(), 0F);
    assertEquals(IconBillboard.VERTICAL, data.style().billboard());
  }

  @Test
  public void unknownAndNonBlockMaterialsRemainDistinguishableForRuntimeValidation() {
    BlockIconData unknown = block("{\"type\":\"block\",\"block\":\"minecraft:not_real\"}");
    BlockIconData sword = block("{\"type\":\"block\",\"block\":\"minecraft:diamond_sword\"}");

    assertNull(unknown.blockType());
    assertThrows(MenuIconException.class, unknown::requireBlock);
    assertEquals(Material.DIAMOND_SWORD, sword.blockType());
  }

  private static BlockIconData block(String json) {
    return (BlockIconData) BukkitJson.GSON.fromJson(json, MenuIconData.class);
  }
}
