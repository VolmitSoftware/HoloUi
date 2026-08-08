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
package art.arcane.holoui.enums;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MenuIconTypeTest {

  @Test
  public void theUnimplementedFontImageTypeIsGone() {
    assertThrows(IllegalArgumentException.class, () -> MenuIconType.valueOf("FONT_IMAGE"));
    assertTrue(Arrays.stream(MenuIconType.values()).noneMatch(t -> t.getSerializedName().equals("fontImage")));
  }

  @Test
  public void theApiOnlyItemStackTypeStays() {
    assertEquals("itemStack", MenuIconType.ITEM_STACK.getSerializedName());
    assertNull(MenuIconType.ITEM_STACK.getType());
  }

  @Test
  public void everyOtherTypeDeclaresItsPayloadRecord() {
    for (MenuIconType type : MenuIconType.values()) {
      if (type == MenuIconType.ITEM_STACK)
        continue;
      assertNotNull(type.name(), type.getType());
    }
  }
}
