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
import org.bukkit.util.Vector;
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
            + "\"hitbox\":{\"width\":1.25,\"height\":0.35,\"offset\":[0.5,-0.1,0.25],\"anchor\":\"menu\"}}",
        ComponentData.class);
    ButtonComponentData button = (ButtonComponentData) decoded;

    assertEquals(1.25F, button.hitbox().width(), 0F);
    assertEquals(0.35F, button.hitbox().height(), 0F);
    assertEquals(3.125F, button.hitbox().scaledWidth(2.5F), 0F);
    assertEquals(0.875F, button.hitbox().scaledHeight(2.5F), 0F);
    assertEquals(new Vector(1.25, -0.25, 0.625), button.hitbox().scaledOffset(2.5F));
    assertEquals(HitboxAnchor.MENU, button.hitbox().anchorOrDefault());

    JsonObject encoded = BukkitJson.GSON.toJsonTree(button, ComponentData.class).getAsJsonObject();
    assertEquals(1.25F, encoded.getAsJsonObject("hitbox").get("width").getAsFloat(), 0F);
    assertEquals(0.35F, encoded.getAsJsonObject("hitbox").get("height").getAsFloat(), 0F);
    assertEquals(0.5F, encoded.getAsJsonObject("hitbox").getAsJsonArray("offset").get(0).getAsFloat(), 0F);
    assertEquals("menu", encoded.getAsJsonObject("hitbox").get("anchor").getAsString());
  }

  @Test
  public void invalidExplicitDimensionsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new HitboxData(0F, 0.5F, null, null));
    assertThrows(IllegalArgumentException.class, () -> new HitboxData(0.5F, Float.NaN, null, null));
    assertThrows(IllegalArgumentException.class, () -> new HitboxData(0.5F, null, null, null));
    assertThrows(IllegalArgumentException.class, () -> new HitboxData(null, null, new Vector(Double.NaN, 0, 0), null));
  }

  @Test
  public void offsetOnlyKeepsAutomaticSizing() {
    ComponentData decoded = BukkitJson.GSON.fromJson(
        "{\"type\":\"button\",\"actions\":[],\"icon\":{\"type\":\"text\",\"text\":\"Play\"},"
            + "\"hitbox\":{\"offset\":[0.5,0,0]}}",
        ComponentData.class);
    HitboxData hitbox = ((ButtonComponentData) decoded).hitbox();

    assertTrue(!hitbox.hasCustomSize());
    assertEquals(new Vector(0.5, 0, 0), hitbox.offset());
    assertEquals(HitboxAnchor.BUTTON, hitbox.anchorOrDefault());
  }
}
