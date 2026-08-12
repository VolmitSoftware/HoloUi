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
package art.arcane.holoui.api;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HoloBlockIconTest {

  @Test
  public void acceptsBlockMaterials() {
    HoloIcon.Block icon = (HoloIcon.Block) HoloIcon.block(Material.STONE);

    assertEquals(Material.STONE, icon.material());
  }

  @Test
  public void rejectsNullWithoutRequiringAStandalonePaperRegistry() {
    assertThrows(NullPointerException.class, () -> HoloIcon.block(null));
  }
}
