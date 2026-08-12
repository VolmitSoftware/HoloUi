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

import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class TextIconDataTest {

  @Test
  public void omittedRefreshUsesTheLiveDefault() {
    TextIconData data = text("{\"type\":\"text\",\"text\":\"%player_name%\"}");

    assertNull(data.refreshTicks());
    assertEquals(10, data.resolvedRefreshTicks());
  }

  @Test
  public void zeroDisablesAndMaximumIsAccepted() {
    assertEquals(0, text("{\"type\":\"text\",\"text\":\"A\",\"refreshTicks\":0}").resolvedRefreshTicks());
    assertEquals(1200, text("{\"type\":\"text\",\"text\":\"A\",\"refreshTicks\":1200}").resolvedRefreshTicks());
  }

  @Test
  public void valuesOutsideTheContractAreRejected() {
    assertThrows(RuntimeException.class,
        () -> text("{\"type\":\"text\",\"text\":\"A\",\"refreshTicks\":-1}"));
    assertThrows(RuntimeException.class,
        () -> text("{\"type\":\"text\",\"text\":\"A\",\"refreshTicks\":1201}"));
  }

  private static TextIconData text(String json) {
    return (TextIconData) BukkitJson.GSON.fromJson(json, MenuIconData.class);
  }
}
