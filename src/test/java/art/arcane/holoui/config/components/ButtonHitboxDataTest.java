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
package art.arcane.holoui.config.components;

import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ButtonHitboxDataTest {

  @Test
  public void omittedHitboxKeepsAutomaticIconSizing() {
    ComponentData decoded = BukkitJson.GSON.fromJson(
        "{\"type\":\"button\",\"actions\":[],\"icon\":{\"type\":\"text\",\"text\":\"Play\"}}",
        ComponentData.class);

    assertTrue(decoded instanceof ButtonComponentData);
    assertNull(((ButtonComponentData) decoded).hitbox());
  }

  @Test
  public void explicitHitboxRoundTripsAndScales() {
    ComponentData decoded = BukkitJson.GSON.fromJson(
        "{\"type\":\"button\",\"actions\":[],\"icon\":{\"type\":\"text\",\"text\":\"Play\"},"
            + "\"hitbox\":{\"width\":1.25,\"height\":0.35}}",
        ComponentData.class);
    ButtonComponentData button = (ButtonComponentData) decoded;

    assertEquals(1.25F, button.hitbox().width(), 0F);
    assertEquals(0.35F, button.hitbox().height(), 0F);
    assertEquals(3.125F, button.hitbox().scaledWidth(2.5F), 0F);
    assertEquals(0.875F, button.hitbox().scaledHeight(2.5F), 0F);

    JsonObject encoded = BukkitJson.GSON.toJsonTree(button, ComponentData.class).getAsJsonObject();
    assertEquals(1.25F, encoded.getAsJsonObject("hitbox").get("width").getAsFloat(), 0F);
    assertEquals(0.35F, encoded.getAsJsonObject("hitbox").get("height").getAsFloat(), 0F);
  }

  @Test
  public void invalidExplicitDimensionsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new HitboxData(0F, 0.5F));
    assertThrows(IllegalArgumentException.class, () -> new HitboxData(0.5F, Float.NaN));
  }
}
